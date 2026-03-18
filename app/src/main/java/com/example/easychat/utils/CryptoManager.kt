package com.example.easychat.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    // Chave fixa de 32 bytes (AES-256) — mesma em todos os dispositivos
    private const val SECRET_KEY = "EasyChat2026SecretKey!@#\$%^&*()"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private fun getKey(): SecretKeySpec {
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(16) { 0 } // IV fixo para simplicidade
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.DEFAULT).trim()
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(16) { 0 }
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(iv))
            val decoded = Base64.decode(cipherText, Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }
}