package com.example.domain

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordCryptoManager {
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val IV_SIZE_BYTES = 12
  private const val TAG_LENGTH_BITS = 128
  private const val APP_PEPPER = "NeoSerf_CyberGraphite_Vault_2026"

  private fun deriveKey(userUid: String): SecretKeySpec {
    val digest = MessageDigest.getInstance("SHA-256")
    val keyBytes = digest.digest("$userUid:$APP_PEPPER".toByteArray(Charsets.UTF_8))
    return SecretKeySpec(keyBytes, "AES")
  }

  fun encrypt(plainText: String, userUid: String): String {
    if (plainText.isEmpty()) return ""
    return try {
      val iv = ByteArray(IV_SIZE_BYTES)
      SecureRandom().nextBytes(iv)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
      cipher.init(Cipher.ENCRYPT_MODE, deriveKey(userUid), spec)
      val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
      val combined = ByteArray(iv.size + cipherBytes.size)
      System.arraycopy(iv, 0, combined, 0, iv.size)
      System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
      Base64.encodeToString(combined, Base64.NO_WRAP)
    } catch (e: Exception) {
      ""
    }
  }

  fun decrypt(encryptedBase64: String, userUid: String): String {
    if (encryptedBase64.isBlank()) return ""
    return try {
      val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
      if (combined.size <= IV_SIZE_BYTES) return "••••••••"
      val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
      val cipherBytes = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
      cipher.init(Cipher.DECRYPT_MODE, deriveKey(userUid), spec)
      String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    } catch (e: Exception) {
      "••••••••"
    }
  }

  fun generateStrongPassword(length: Int = 16, includeSymbols: Boolean = true): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#\$%^&*()-_=+?"
    val pool = if (includeSymbols) upper + lower + digits + symbols else upper + lower + digits
    val random = SecureRandom()
    val chars = CharArray(length.coerceIn(8, 32))
    chars[0] = upper[random.nextInt(upper.length)]
    chars[1] = lower[random.nextInt(lower.length)]
    chars[2] = digits[random.nextInt(digits.length)]
    if (includeSymbols) {
      chars[3] = symbols[random.nextInt(symbols.length)]
    }
    val startIdx = if (includeSymbols) 4 else 3
    for (i in startIdx until chars.size) {
      chars[i] = pool[random.nextInt(pool.length)]
    }
    for (i in chars.indices) {
      val swapIdx = random.nextInt(chars.size)
      val tmp = chars[i]
      chars[i] = chars[swapIdx]
      chars[swapIdx] = tmp
    }
    return String(chars)
  }
}
