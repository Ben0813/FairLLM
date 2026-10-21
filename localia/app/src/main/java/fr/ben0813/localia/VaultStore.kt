package fr.ben0813.localia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object VaultCipher {
    private val header = byteArrayOf(0x4c, 0x43, 0x56, 1)
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(header)
        require(cipher.iv.size == 12)
        return header + cipher.iv + cipher.doFinal(plaintext)
    }
    fun decrypt(key: SecretKey, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 32 && ciphertext.take(4).toByteArray().contentEquals(header)) { "Coffre invalide" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext.copyOfRange(4, 16)))
        cipher.updateAAD(header)
        return cipher.doFinal(ciphertext.copyOfRange(16, ciphertext.size))
    }
}

class VaultStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "localia.vault"))
    private val mutex = Mutex()
    private var lastRevision = -1L
    private val alias = "localia-vault-v1"
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "La clé de ce coffre n’est plus disponible sur cet appareil." }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    suspend fun read(): Vault = withContext(Dispatchers.IO) { mutex.withLock {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withLock Vault()
        val bytes = file.openRead().use { stream ->
            require(stream.channel.size() <= 32 * 1024 * 1024) { "Coffre trop volumineux" }
            stream.readBytes()
        }
        Vault.from(VaultCipher.decrypt(key(false), bytes).toString(Charsets.UTF_8)).also { lastRevision = it.revision }
    } }
    suspend fun save(vault: Vault) = withContext(Dispatchers.IO) { mutex.withLock {
        if (vault.revision <= lastRevision) return@withLock
        val data = vault.json().toString().toByteArray(Charsets.UTF_8)
        require(data.size < 31 * 1024 * 1024) { "Historique trop volumineux : exporte puis supprime des conversations." }
        val encrypted = VaultCipher.encrypt(key(true), data)
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream); lastRevision = vault.revision }
        catch (e: Exception) { file.failWrite(stream); throw e }
    } }
    suspend fun erase() = withContext(Dispatchers.IO) { mutex.withLock {
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        lastRevision = -1
    } }
}
