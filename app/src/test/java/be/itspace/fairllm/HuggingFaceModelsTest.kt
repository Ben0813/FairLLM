package be.itspace.fairllm

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse

class HuggingFaceModelsTest {
    @Test fun searchCallsHuggingFaceWithEncodedQueryAndGgufFilter() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[{"id":"owner/model","downloads":42,"gated":false,"pipeline_tag":"text-generation"}]"""))
            val models = HuggingFaceModels(apiBase = server.url("/api/")).search("Qwen & petit")
            val request = server.takeRequest().requestUrl!!
            assertEquals("/api/models", request.encodedPath)
            assertEquals("gguf", request.queryParameter("filter"))
            assertEquals("Qwen & petit", request.queryParameter("search"))
            assertEquals("owner/model", models.single().id)
        }
    }
    @Test fun searchExcludesPrivateGatedAndAudioModels() {
        val json = """[{"id":"owner/chat","pipeline_tag":"text-generation"},
            {"id":"owner/private","private":true},{"id":"owner/gated","gated":"auto"},
            {"id":"owner/audio","pipeline_tag":"text-to-speech"},{"id":"owner/Qwen-TTS-gguf"},
            {"id":"invalid/../id"}]"""
        assertEquals(listOf("owner/chat"), HuggingFaceModels.parseSearch(json).map { it.id })
    }
    @Test fun emptyQueryRetrievesPopularModels() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            HuggingFaceModels(apiBase = server.url("/api/")).search("")
            val request = server.takeRequest().requestUrl!!
            assertNull(request.queryParameter("search"))
            assertEquals("downloads", request.queryParameter("sort"))
        }
    }
    @Test fun searchRateLimitHasAnActionableError() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            val error = assertThrows(IOException::class.java) { HuggingFaceModels(apiBase = server.url("/api/")).search("Qwen") }
            assertTrue(error.message!!.contains("Réessaie"))
        }
    }
    private val hash = "a".repeat(64)
    private val revision = "b".repeat(40)
    private fun metadata(files: String, gated: String = "false") =
        """{"sha":"$revision","private":false,"gated":$gated,"siblings":[$files]}"""
    private fun file(name: String, size: Long = 100) =
        """{"rfilename":"$name","lfs":{"size":$size,"sha256":"$hash"}}"""

    @Test fun acceptsRepositoryAndFileLinks() {
        assertEquals("owner/model", HuggingFaceModels.repository("owner/model"))
        assertEquals("owner/model", HuggingFaceModels.repository("https://huggingface.co/owner/model"))
        assertEquals("owner/model", HuggingFaceModels.repository("https://huggingface.co/owner/model/blob/main/file.gguf?download=true"))
    }

    @Test fun rejectsForeignSitesAndInvalidIdentifiers() {
        listOf("https://evil.example/owner/model", "https://huggingface.co.evil.example/owner/model",
            "https://huggingface.co:444/owner/model", "../model", "owner/model/extra", "owner", "http://huggingface.co/owner/model")
            .forEach { assertThrows(IOException::class.java) { HuggingFaceModels.repository(it) } }
    }

    @Test fun pinsDownloadAndIsolatesNestedFilenames() {
        val model = HuggingFaceModels.parse("owner/model", metadata(file("subdir/my file-Q4_K_M.gguf"))).single()
        assertEquals(revision, model.revision)
        assertEquals(hash, model.sha256)
        assertEquals("$hash.gguf", model.storageName)
        assertEquals("https://huggingface.co/owner/model/resolve/$revision/subdir/my%20file-Q4_K_M.gguf", model.downloadUrl)
    }

    @Test fun excludesSplitModelsProjectorsAndOtherFormats() {
        val files = listOf("model-00001-of-00002.gguf", "mmproj-model-f16.gguf", "model.safetensors", "../model.gguf", "model-Q4_K_M.gguf")
        val models = HuggingFaceModels.parse("owner/model", metadata(files.joinToString(",") { file(it) }))
        assertEquals(listOf("model-Q4_K_M.gguf"), models.map { it.fileName })
    }

    @Test fun requiresVerifiableSizeAndHash() {
        assertThrows(IOException::class.java) { HuggingFaceModels.parse("owner/model", metadata(file("model.gguf", 0))) }
        assertThrows(IOException::class.java) { HuggingFaceModels.parse("owner/model", metadata("""{"rfilename":"model.gguf"}""")) }
        assertThrows(IOException::class.java) { HuggingFaceModels.parse("owner/model", metadata(file("model.gguf")).replace(hash, "invalid")) }
    }

    @Test fun givesExplicitMessageForGatedRepositories() {
        val error = assertThrows(IOException::class.java) {
            HuggingFaceModels.parse("owner/model", metadata(file("model.gguf"), "\"auto\""))
        }
        assertTrue(error.message!!.contains("autorisation"))
    }

    @Test fun prefersQ4KMBeforeLargerVariants() {
        val models = HuggingFaceModels.parse("owner/model", metadata(file("model-Q8_0.gguf", 200) + "," + file("model-Q4_K_M.gguf", 100)))
        assertEquals("model-Q4_K_M.gguf", models.first().fileName)
    }

    @Test fun readsRealHuggingFaceMetadataSnapshot() {
        val json = javaClass.getResourceAsStream("/hf-qwen.json")!!.bufferedReader().use { it.readText() }
        val models = HuggingFaceModels.parse("ggml-org/Qwen3-1.7B-GGUF", json)
        val expected = MODEL_PRESETS[1]
        val parsed = models.single { it.fileName == expected.fileName }
        assertEquals(expected.sha256, parsed.sha256)
        assertEquals(expected.byteSize, parsed.byteSize)
        assertEquals(expected.downloadUrl, parsed.downloadUrl)
    }
}
