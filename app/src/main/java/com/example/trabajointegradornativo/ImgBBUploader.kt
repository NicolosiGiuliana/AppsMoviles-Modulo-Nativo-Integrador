package com.example.trabajointegradornativo

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.io.IOException

class ImgBBUploader {

    companion object {
        private const val TAG = "ImgBBUploader"
        private const val IMGBB_API_URL = "https://api.imgbb.com/1/upload"
        // Tu API key está aquí - ¡perfecto!
        private const val API_KEY = "3fc24a14a9488f101644740df249948a"
        private const val JPEG_QUALITY = 80
    }

    // Clases para manejar la respuesta de ImgBB
    data class ImgBBResponse(
        @SerializedName("data") val data: ImgBBData?,
        @SerializedName("success") val success: Boolean,
        @SerializedName("status") val status: Int,
        @SerializedName("error") val error: ImgBBError?
    )

    data class ImgBBData(
        @SerializedName("id") val id: String,
        @SerializedName("title") val title: String,
        @SerializedName("url_viewer") val urlViewer: String,
        @SerializedName("url") val url: String,
        @SerializedName("display_url") val displayUrl: String,
        @SerializedName("width") val width: Int,
        @SerializedName("height") val height: Int,
        @SerializedName("size") val size: Int,
        @SerializedName("time") val time: String,
        @SerializedName("expiration") val expiration: String,
        @SerializedName("image") val image: ImgBBImage,
        @SerializedName("thumb") val thumb: ImgBBThumb,
        @SerializedName("medium") val medium: ImgBBMedium,
        @SerializedName("delete_url") val deleteUrl: String
    )

    data class ImgBBImage(
        @SerializedName("filename") val filename: String,
        @SerializedName("name") val name: String,
        @SerializedName("mime") val mime: String,
        @SerializedName("extension") val extension: String,
        @SerializedName("url") val url: String
    )

    data class ImgBBThumb(
        @SerializedName("filename") val filename: String,
        @SerializedName("name") val name: String,
        @SerializedName("mime") val mime: String,
        @SerializedName("extension") val extension: String,
        @SerializedName("url") val url: String
    )

    data class ImgBBMedium(
        @SerializedName("filename") val filename: String,
        @SerializedName("name") val name: String,
        @SerializedName("mime") val mime: String,
        @SerializedName("extension") val extension: String,
        @SerializedName("url") val url: String
    )

    data class ImgBBError(
        @SerializedName("message") val message: String,
        @SerializedName("code") val code: Int
    )

    interface UploadCallback {
        fun onSuccess(imageUrl: String, deleteUrl: String)
        fun onError(error: String)
        fun onProgress(progress: Int)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val gson = Gson()

    fun uploadImage(bitmap: Bitmap, callback: UploadCallback) {
        // Convertir bitmap a base64
        val base64Image = bitmapToBase64(bitmap)

        if (base64Image == null) {
            callback.onError("Error al convertir imagen a base64")
            return
        }

        Log.d(TAG, "Iniciando subida a ImgBB...")
        callback.onProgress(10)

        // Crear el cuerpo de la petición
        val requestBody = FormBody.Builder()
            .add("key", API_KEY)
            .add("image", base64Image)
            .add("name", "profile_${System.currentTimeMillis()}")
            .build()

        // Crear la petición
        val request = Request.Builder()
            .url(IMGBB_API_URL)
            .post(requestBody)
            .build()

        callback.onProgress(30)

        // Ejecutar la petición de forma asíncrona
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error en la petición HTTP", e)
                callback.onError("Error de conexión: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                callback.onProgress(80)

                response.use { resp ->
                    val responseBody = resp.body?.string()

                    if (responseBody == null) {
                        callback.onError("Respuesta vacía del servidor")
                        return
                    }

                    Log.d(TAG, "Respuesta de ImgBB: $responseBody")

                    try {
                        val imgbbResponse = gson.fromJson(responseBody, ImgBBResponse::class.java)

                        if (imgbbResponse.success && imgbbResponse.data != null) {
                            Log.d(TAG, "Imagen subida exitosamente: ${imgbbResponse.data.url}")
                            callback.onProgress(100)
                            callback.onSuccess(imgbbResponse.data.url, imgbbResponse.data.deleteUrl)
                        } else {
                            val errorMessage = imgbbResponse.error?.message ?: "Error desconocido"
                            Log.e(TAG, "Error de ImgBB: $errorMessage")
                            callback.onError("Error de ImgBB: $errorMessage")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al parsear respuesta JSON", e)
                        callback.onError("Error al procesar respuesta: ${e.message}")
                    }
                }
            }
        })
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error al convertir bitmap a base64", e)
            null
        }
    }
}