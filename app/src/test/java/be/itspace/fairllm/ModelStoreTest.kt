package be.itspace.fairllm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val bytes = "GGUF-test-model".toByteArray()
    private val model = ModelPreset("test", "test/test:Q4_K_M", 0, 2048, "test", "test.gguf", "test",
        bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })

    private fun store(server: MockWebServer, space: Long = Long.MAX_VALUE) = ModelStore(folder.root,
        availableBytes = { space }, downloadUrl = { server.url("/model").toString() })

    @Test fun verifiedModelIsReusedWithoutNetwork() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(String(bytes)))
            val store = store(server)
            val file = store.prepare(model) {}
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(store.ready(model))
            store.prepare(model) {}
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun partialDownloadIsResumedAtCorrectOffset() {
        MockWebServer().use { server ->
            File(folder.root, "test.gguf.part").writeBytes(bytes.copyOfRange(0, 4))
            server.enqueue(MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 4-${bytes.lastIndex}/${bytes.size}")
                .setBody(String(bytes.copyOfRange(4, bytes.size))))
            val file = store(server).prepare(model) {}
            assertArrayEquals(bytes, file.readBytes())
            assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun serverIgnoringRangeRestartsDownload() {
        MockWebServer().use { server ->
            File(folder.root, "test.gguf.part").writeBytes(bytes.copyOfRange(0, 4))
            server.enqueue(MockResponse().setBody(String(bytes)))
            assertArrayEquals(bytes, store(server).prepare(model) {}.readBytes())
        }
    }

    @Test fun corruptDownloadIsRejectedAndDeleted() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("X".repeat(bytes.size)))
            val store = store(server)
            assertThrows(IOException::class.java) { store.prepare(model) {} }
            assertFalse(store.ready(model))
            assertFalse(File(folder.root, "test.gguf.part").exists())
        }
    }

    @Test fun interruptedDownloadKeepsPartialForRetry() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("GGUF"))
            val store = store(server)
            assertThrows(IOException::class.java) { store.prepare(model) {} }
            assertEquals(4L, File(folder.root, "test.gguf.part").length())
            assertFalse(store.ready(model))
        }
    }

    @Test fun insufficientSpaceDoesNotStartDownload() {
        MockWebServer().use { server ->
            assertThrows(IOException::class.java) { store(server, space = 0).prepare(model) {} }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun cancellationDoesNotStartDownload() {
        MockWebServer().use { server ->
            val store = store(server)
            store.cancel()
            assertThrows(IOException::class.java) { store.prepare(model) {} }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun importedModelsWithSameRemoteFilenameDoNotOverwriteEachOther() {
        MockWebServer().use { server ->
            val imported = model.copy(storageName = "imported.gguf")
            server.enqueue(MockResponse().setBody(String(bytes)))
            server.enqueue(MockResponse().setBody(String(bytes)))
            val store = store(server)
            val builtinFile = store.prepare(model) {}
            val importedFile = store.prepare(imported) {}
            assertNotEquals(builtinFile, importedFile)
            store.delete(imported)
            assertFalse(store.ready(imported))
            assertTrue(store.ready(model))
        }
    }
}
