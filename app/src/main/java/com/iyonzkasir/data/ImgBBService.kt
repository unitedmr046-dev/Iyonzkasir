package com.iyonzkasir.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service untuk upload foto ke ImgBB (gratis unlimited).
 * Hasil: URL foto yang bisa dipakai di Firestore.
 */
object ImgBBService {

    private const val BASE_URL = "https://api.imgbb.com/1/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Upload file foto dari local path → return URL.
     * @param localPath path file foto (dari internal storage)
     * @param apiKey ImgBB API key
     * @return URL foto atau null kalau gagal
     */
    suspend fun upload(
        localPath: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(localPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File tidak ditemukan"))
            }
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API key kosong"))
            }

            // Baca file → encode base64
            val bytes = file.readBytes()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            // Build request
            val body = FormBody.Builder()
                .add("key", apiKey)
                .add("image", base64)
                .build()

            val request = Request.Builder()
                .url(BASE_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}: ${bodyStr.take(200)}")
                    )
                }

                val json = JSONObject(bodyStr)
                val success = json.optBoolean("success", false)
                if (!success) {
                    val err = json.optJSONObject("error")?.optString("message")
                        ?: "Unknown error"
                    return@withContext Result.failure(Exception(err))
                }

                val data = json.getJSONObject("data")
                val url = data.getString("url")
                Result.success(url)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload dari URI (dari galeri) — caller harus convert ke file dulu
     * atau kirim bytes. Helper untuk case yang butuh.
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API key kosong"))
            }
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val body = FormBody.Builder()
                .add("key", apiKey)
                .add("image", base64)
                .build()

            val request = Request.Builder()
                .url(BASE_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("HTTP ${response.code}")
                    )
                }
                val json = JSONObject(bodyStr)
                val data = json.getJSONObject("data")
                Result.success(data.getString("url"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
