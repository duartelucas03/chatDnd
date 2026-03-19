package com.example.easychat.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/** Claude AI - início
 * Prompt: Crie um utilitário de criptografia pra criptografar e descriptografar as mensagens do chat usando AES. Se der erro na descriptografia, retorna o texto original sem quebrar o app.
 */
object CryptoManager {

    private const val SECRET_KEY = "EasyChat2026SecretKey!@#$%^&*()"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private fun getKey(): SecretKeySpec {
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8).copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    private val fixedIv = ByteArray(16) { 0 }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), IvParameterSpec(fixedIv))
            Base64.encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (e: Exception) { plainText }
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return cipherText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), IvParameterSpec(fixedIv))
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) { cipherText }
    }
}


/** Claude AI - final */