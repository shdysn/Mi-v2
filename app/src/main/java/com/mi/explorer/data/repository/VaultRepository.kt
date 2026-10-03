package com.mi.explorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mi.explorer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class VaultRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mi_vault_prefs", Context.MODE_PRIVATE)

    private val vaultDir: File = File(context.filesDir, "MiVault_Secure").apply {
        if (!exists()) mkdirs()
    }

    private val filesDir: File = File(vaultDir, "vault_files").apply {
        if (!exists()) mkdirs()
    }

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin) == storedHash
    }

    fun setPin(pin: String, securityAnswer: String) {
        prefs.edit()
            .putString(KEY_PIN_HASH, hash(pin))
            .putString(KEY_SECURITY_ANSWER, hash(securityAnswer.trim().lowercase()))
            .apply()
    }

    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = prefs.getString(KEY_SECURITY_ANSWER, null) ?: return false
        return hash(answer.trim().lowercase()) == storedHash
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    suspend fun getVaultFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = filesDir.listFiles() ?: return@withContext emptyList()
        files.map { FileItem(it) }.sortedByDescending { it.lastModified }
    }

    suspend fun addToVault(source: File): Boolean = withContext(Dispatchers.IO) {
        if (!source.exists() || source.isDirectory) return@withContext false
        try {
            val dest = File(filesDir, source.name)
            val finalDest = if (dest.exists()) {
                File(filesDir, "${System.currentTimeMillis()}_${source.name}")
            } else dest

            source.copyTo(finalDest, overwrite = true)
            source.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreFromVault(vaultFile: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext false
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val dest = File(targetDir, vaultFile.name)
            vaultFile.copyTo(dest, overwrite = true)
            vaultFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFromVault(vaultFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            vaultFile.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_PIN_HASH = "vault_pin_hash"
        private const val KEY_SECURITY_ANSWER = "vault_security_answer"
        private const val KEY_BIOMETRIC_ENABLED = "vault_biometric_enabled"
    }
}
