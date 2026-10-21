package fr.ben0813.localia

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaultDeviceTest {
    @Test fun keystorePersistsEncryptedDataAndDetectsTamperingWithoutOverwriting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VaultStore(context)
        store.erase()
        try {
            val vault = Vault(connection = Connection("https://pc.example", "device-secret", "qwen"), revision = 5)
            store.save(vault)
            val file = File(context.noBackupFilesDir, "localia.vault")
            val ciphertext = file.readBytes()
            assertFalse(ciphertext.toString(Charsets.ISO_8859_1).contains("device-secret"))
            assertEquals(vault, VaultStore(context).read())
            store.save(vault.copy(revision = 4, connection = Connection()))
            assertEquals(vault, store.read())
            val corrupt = ciphertext.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
            file.writeBytes(corrupt)
            val failure = runCatching { store.read() }.exceptionOrNull()
            assertNotNull(failure)
            assertArrayEquals(corrupt, file.readBytes())
        } finally { store.erase() }
        assertEquals(Vault(), store.read())
    }
}
