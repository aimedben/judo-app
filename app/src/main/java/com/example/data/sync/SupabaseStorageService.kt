package com.example.data.sync

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Supabase **Storage** REST API used to keep profile photos out of
 * the database rows.
 *
 * Why Storage instead of the old base64-in-a-text-column trick:
 *  - a 512 px lossy WebP avatar is ~20-40 KB, so the whole club stays far below the 1 GB
 *    free tier and the JSON payloads pushing rows stay small (low egress, low DB size);
 *  - photos can be fetched lazily, one at a time, only when a device actually needs them.
 *
 * All calls are synchronous network calls and must run off the main thread (the sync engine
 * already does that).
 */
class SupabaseStorageService {

    companion object {
        const val BUCKET_MEMBER_PHOTOS = "member-photos"
        const val BUCKET_COACH_PHOTOS = "coach-photos"
        const val BUCKET_CLUB_ASSETS = "club-assets"

        const val MIME_WEBP = "image/webp"

        /** Deterministic object key for a member avatar. */
        fun memberPhotoKey(memberId: Long): String = "members/$memberId.webp"

        /** Deterministic object key for a coach avatar. */
        fun coachPhotoKey(coachId: String): String = "coaches/$coachId.webp"

        /** Deterministic object key for the club administrator avatar. */
        const val ADMIN_PHOTO_KEY = "admin/admin.webp"

        /** Public URL of an object (the three buckets are public-read). */
        fun publicUrl(baseUrl: String, bucket: String, objectPath: String): String =
            "${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/$objectPath"
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads (or replaces) an object. Uses `x-upsert: true` so re-uploading the same
     * avatar after an edit simply overwrites it instead of failing.
     */
    fun upload(
        cfg: SupabaseConfig,
        bucket: String,
        objectPath: String,
        bytes: ByteArray,
        contentType: String = MIME_WEBP
    ): Boolean {
        if (!cfg.isConfigured || bytes.isEmpty()) return false
        return try {
            val request = Request.Builder()
                .url("${cfg.url}/storage/v1/object/$bucket/$objectPath")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .addHeader("x-upsert", "true")
                .addHeader("Cache-Control", "3600")
                .put(bytes.toRequestBody(contentType.toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val ok = response.isSuccessful || response.code in 200..299
                if (!ok) Log.w("SupabaseStorage", "Upload $bucket/$objectPath failed: HTTP ${response.code}")
                ok
            }
        } catch (e: Exception) {
            Log.w("SupabaseStorage", "Upload $bucket/$objectPath error: ${e.message}")
            false
        }
    }

    /** Downloads an object's bytes, or null when it is missing / the network fails. */
    fun download(cfg: SupabaseConfig, bucket: String, objectPath: String): ByteArray? {
        if (!cfg.isConfigured) return null
        return try {
            val request = Request.Builder()
                .url("${cfg.url}/storage/v1/object/$bucket/$objectPath")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            Log.w("SupabaseStorage", "Download $bucket/$objectPath error: ${e.message}")
            null
        }
    }

    /** Best-effort object deletion (used when a member / coach is removed). */
    fun delete(cfg: SupabaseConfig, bucket: String, objectPath: String): Boolean {
        if (!cfg.isConfigured) return false
        return try {
            val request = Request.Builder()
                .url("${cfg.url}/storage/v1/object/$bucket/$objectPath")
                .addHeader("apikey", cfg.anonKey)
                .addHeader("Authorization", "Bearer ${cfg.anonKey}")
                .delete()
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..299 || response.code == 404
            }
        } catch (e: Exception) {
            Log.w("SupabaseStorage", "Delete $bucket/$objectPath error: ${e.message}")
            false
        }
    }
}
