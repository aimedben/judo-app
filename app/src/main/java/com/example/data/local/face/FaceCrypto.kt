package com.example.data.local.face

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffrement des embeddings avec une cle AES-256 non exportable du Keystore Android
 * (StrongBox si l'appareil en dispose, sinon TEE).
 */
object FaceCrypto {

    private const val ALIAS = "jcs_face_embedding_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        fun generator(strongBox: Boolean): SecretKey {
            val builder = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(builder.build())
            return generator.generateKey()
        }

        return try {
            generator(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generator(strongBox = false)
        } catch (_: Exception) {
            generator(strongBox = false)
        }
    }

    /** Retourne (octets chiffres, IV) ou null en cas d'echec. */
    fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray>? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.doFinal(plain) to cipher.iv
    } catch (_: Exception) {
        null
    }

    fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(cipherText)
    } catch (_: Exception) {
        null
    }
}
