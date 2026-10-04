package be.itspace.fairllm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class LlamaApiTest {
    @Test fun nullAndNonTextDeltasAreIgnoredButLiteralNullIsPreserved() = runBlocking {
        MockWebServer().use { server ->
            val deltas = listOf("{\"role\":\"assistant\",\"content\":null}", "{}",
                "{\"content\":123}", "{\"content\":\"Bonjour\"}", "{\"content\":null}",
                "{\"content\":\" null\"}", "{\"content\":\"\"}")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(deltas.joinToString("") { "data: {\"choices\":[{\"delta\":$it}]}\n\n" } + "data: [DONE]\n\n"))
            val tokens = mutableListOf<String>()
            LlamaApi().streamChat(server.url("/").toString(), listOf(ChatMessage("user", "Salut")),
                null, 0.7f, 0.8f, 128) { tokens += it }
            assertEquals(listOf("Bonjour", " null"), tokens)
        }
    }
    @Test fun streamingPreservesTextAndReadsMeasuredSpeed() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Bon\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"jour\"}}]}\n\n" +
                "data: {\"timings\":{\"predicted_per_second\":12.5},\"choices\":[]}\n\n" +
                "data: [DONE]\n\n"))
            var result = ""
            var speed = 0.0
            LlamaApi("test").streamChat(server.url("/").toString(), listOf(ChatMessage("user", "Salut")),
                "Réponds brièvement", 0.7f, 0.8f, 128, onSpeed = { speed = it }) { result += it }
            assertEquals("Bonjour", result)
            assertEquals(12.5, speed, 0.0001)
            assertEquals("Bearer test", server.takeRequest().getHeader("Authorization"))
        }
    }
}
