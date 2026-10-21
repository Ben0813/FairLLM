package fr.ben0813.localia

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.net.ssl.SSLException

class SecurityTest {
    private fun url(server: MockWebServer) = server.url("/").newBuilder().host("localhost").build().toString()
    private fun event(type: String, fields: String = "") = "event: $type\ndata: {\"type\":\"$type\"$fields}\n\n"
    private fun end(id: String = "resp_first", text: String = "Voici la réponse.") = event("chat.end",
        ",\"result\":{\"response_id\":\"$id\",\"output\":[{\"type\":\"message\",\"content\":\"$text\"}],\"stats\":{\"tokens_per_second\":22.5}}")

    private fun tlsServer(block: (MockWebServer, OkHttpClient) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCerts = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverCerts.sslSocketFactory(), false)
            server.start()
            block(server, OkHttpClient.Builder().sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager).build())
        }
    }

    @Test fun httpAndUrlCredentialLeaksAreRejected() {
        listOf("http://pc:1234", "https://user:password@pc", "https://pc?token=secret", "https://pc/#secret", "https://pc/v1", "file:///data/private").forEach {
            assertThrows(IllegalArgumentException::class.java) { SecureEndpoint.parse(it) }
        }
        assertEquals("https://pc.tail123.ts.net/", SecureEndpoint.parse(" https://pc.tail123.ts.net ").toString())
    }

    @Test fun modelsUseAuthenticatedTlsAndExcludeEmbeddingModels() = tlsServer { server, client -> runBlocking {
        server.enqueue(MockResponse().setBody("""{"models":[{"type":"embedding","key":"embed"},{"type":"llm","key":"qwen","display_name":"Qwen","loaded_instances":[{"id":"qwen"}],"capabilities":{"vision":true,"trained_for_tool_use":true}}]}"""))
        val models = SecureApi(client).models(Connection(url(server), "private-token"))
        assertEquals(listOf(ModelInfo("qwen", "Qwen", true, true, true)), models)
        val request = server.takeRequest()
        assertEquals("/api/v1/models", request.path)
        assertEquals("Bearer private-token", request.getHeader("Authorization"))
    } }

    @Test fun untrustedCertificateIsRejected() = tlsServer { server, _ -> runBlocking {
        assertThrows(SSLException::class.java) {
            runBlocking { SecureApi().models(Connection(url(server), "token")) }
        }
        assertEquals(0, server.requestCount)
    } }

    @Test fun wrongCertificateHostnameIsRejected() = tlsServer { server, client -> runBlocking {
        val wrongHost = server.url("/").newBuilder().host("127.0.0.1").build().toString()
        assertThrows(SSLException::class.java) { runBlocking { SecureApi(client).models(Connection(wrongHost, "token")) } }
        assertEquals(0, server.requestCount)
    } }

    @Test fun redirectNeverForwardsBearerToken() = tlsServer { server, client -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://unrelated.example/collect"))
        val e = assertThrows(IOException::class.java) { runBlocking { SecureApi(client).models(Connection(url(server), "top-secret")) } }
        assertTrue(e.message!!.contains("redirige"))
        assertEquals(1, server.requestCount)
        assertFalse(e.message!!.contains("top-secret"))
    } }

    @Test fun invalidTokenHasActionableErrorWithoutSecret() = tlsServer { server, client -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("private-token"))
        val e = assertThrows(IOException::class.java) { runBlocking { SecureApi(client).models(Connection(url(server), "private-token")) } }
        assertTrue(e.message!!.contains("Jeton refusé"))
        assertFalse(e.message!!.contains("private-token"))
    } }

    @Test fun missingTokenDoesNotSendARequest() = tlsServer { server, client -> runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { SecureApi(client).models(Connection(url(server))) } }
        assertEquals(0, server.requestCount)
    } }

    @Test fun nativeToolsStreamAndFollowupKeepServerContext() = tlsServer { server, client -> runBlocking {
        val stream = ": keepalive\n\n" + event("reasoning.delta", ",\"content\":\"hidden\"") +
            event("tool_call.start", ",\"tool\":\"search\"") + event("tool_call.success", ",\"tool\":\"search\",\"output\":\"external data\"") +
            event("message.delta", ",\"content\":\"Voici la réponse.\"") + end()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(end("resp_second")))
        val api = SecureApi(client)
        val connection = Connection(url(server), "token", "qwen", true, "duckduckgo")
        val text = mutableListOf<String>()
        val activity = mutableListOf<String>()
        val first = api.chat(connection, "Cherche", null, null, { text.add(it) }, { activity.add(it) })
        assertEquals("resp_first", first.responseId)
        assertEquals("Voici la réponse.", text.last())
        assertFalse(text.any { it.contains("hidden") || it.contains("external data") })
        assertTrue(activity.contains("Outil utilisé · search"))
        assertEquals(22.5, first.tokensPerSecond!!, 0.0)
        val request = server.takeRequest()
        assertEquals("/api/v1/chat", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("mcp/duckduckgo", body.getJSONArray("integrations").getString(0))
        assertFalse(body.has("previous_response_id"))
        api.chat(connection, "Et ensuite ?", null, first.responseId, {}, {})
        assertEquals("resp_first", JSONObject(server.takeRequest().body.readUtf8()).getString("previous_response_id"))
    } }

    @Test fun cancelClosesAnActiveRequest() = tlsServer { server, client -> runBlocking { supervisorScope {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val api = SecureApi(client)
        val request = async(Dispatchers.IO) { api.chat(Connection(url(server), "token", "qwen"), "Salut", null, null, {}, {}) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        api.cancel()
        val failure = runCatching { withTimeout(3000) { request.await() } }.exceptionOrNull()
        assertTrue(failure is IOException)
    } } }

    @Test fun disabledToolsAreAbsentFromRequest() {
        val body = NativeProtocol.request(Connection(model = "qwen", extensionsEnabled = false, extensionNames = "duckduckgo"), "Salut", null, null)
        assertFalse(body.has("integrations"))
        assertTrue(body.getBoolean("store"))
    }

    @Test fun imageAndTextDocumentsUseNativeInputItems() {
        val c = Connection(model = "qwen")
        val image = NativeProtocol.request(c, "Décris", Attachment("photo.png", "data:image/png;base64,test", true), null).getJSONArray("input")
        assertEquals("image", image.getJSONObject(1).getString("type"))
        val doc = NativeProtocol.request(c, "Résume", Attachment("notes.txt", "document text", false), null).getJSONArray("input")
        assertTrue(doc.getJSONObject(0).getString("content").contains("document text"))
    }

    @Test fun multilineSseAndFinalOnlyResponsesAreSupported() {
        val stream = "event: message.delta\ndata: {\ndata: \"content\":\"Bonjour\"}\n\n" + end(text = "Bonjour")
        val text = mutableListOf<String>()
        assertEquals("Bonjour", NativeProtocol.consume(Buffer().writeUtf8(stream), { text.add(it) }, {}).text)
        assertEquals("Bonjour", text.last())
        assertEquals("Voici la réponse.", NativeProtocol.consume(Buffer().writeUtf8(end()), {}, {}).text)
    }

    @Test fun errorsAndTruncatedStreamsCannotCommitContext() {
        listOf(event("error", ",\"error\":{\"message\":\"MCP denied\"}"),
            event("tool_call.failure"), event("message.delta", ",\"content\":\"Partial\""),
            event("chat.end", ",\"result\":{\"output\":[]}")).forEach {
            assertThrows(IOException::class.java) { NativeProtocol.consume(Buffer().writeUtf8(it), {}, {}) }
        }
    }

    @Test fun vaultEncryptionIsRandomizedAndAuthenticated() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = "secret token et conversation française".toByteArray()
        val encrypted = VaultCipher.encrypt(key, plaintext)
        assertFalse(encrypted.contentEquals(VaultCipher.encrypt(key, plaintext)))
        assertArrayEquals(plaintext, VaultCipher.decrypt(key, encrypted))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("secret token"))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { VaultCipher.decrypt(key, encrypted) }
    }

    @Test fun wrongKeyCannotDecryptVault() {
        val generator = KeyGenerator.getInstance("AES").apply { init(256) }
        val ciphertext = VaultCipher.encrypt(generator.generateKey(), "private".toByteArray())
        assertThrows(Exception::class.java) { VaultCipher.decrypt(generator.generateKey(), ciphertext) }
    }

    @Test fun vaultRoundTripPreservesHistoryAndExportsOmitCredentials() {
        val c = Connection("https://pc.example", "private-bearer", "qwen", true, "duckduckgo")
        val chat = Conversation(title = "Échange", model = "qwen", binding = c.binding(), responseId = "resp_a", messages = listOf(Message("user", "Salut"), Message("assistant", "Bonjour")))
        val vault = Vault(c, listOf(chat), true, 42)
        assertEquals(vault, Vault.from(vault.json().toString()))
        assertFalse(chat.exportText().contains("private-bearer"))
        assertFalse(c.toString().contains("private-bearer"))
        assertNotEquals(c.binding(), c.copy(token = "rotated").binding())
        assertNotEquals(c.binding(), c.copy(model = "other").binding())
        assertNotEquals(c.binding(), c.copy(extensionNames = "other").binding())
    }
}
