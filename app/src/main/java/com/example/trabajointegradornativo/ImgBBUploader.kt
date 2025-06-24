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
        private const val API_KEY = "3fc24a14a9488f101644740df249948a"
        private const val JPEG_QUALITY = 80

        // Constantes para mensajes (NO localizados - para logs y errores técnicos)
        private const val ERROR_CONVERTING_IMAGE = "Error al convertir imagen a base64"
        private const val STARTING_UPLOAD = "Iniciando subida a ImgBB..."
        private const val HTTP_REQUEST_ERROR = "Error en la petición HTTP"
        private const val CONNECTION_ERROR_PREFIX = "Error de conexión: "
        private const val EMPTY_SERVER_RESPONSE = "Respuesta vacía del servidor"
        private const val IMGBB_RESPONSE_PREFIX = "Respuesta de ImgBB: "
        private const val IMAGE_UPLOADED_SUCCESS = "Imagen subida exitosamente: "
        private const val UNKNOWN_ERROR = "Error desconocido"
        private const val IMGBB_ERROR_PREFIX = "Error de ImgBB: "
        private const val JSON_PARSING_ERROR = "Error al parsear respuesta JSON"
        private const val RESPONSE_PROCESSING_ERROR = "Error al procesar respuesta: "
        private const val BITMAP_TO_BASE64_ERROR = "Error al convertir bitmap a base64"
        private const val PROFILE_IMAGE_PREFIX = "profile_"
    }

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
        val base64Image = bitmapToBase64(bitmap)

        if (base64Image == null) {
            callback.onError(ERROR_CONVERTING_IMAGE)
            return
        }

        callback.onProgress(10)

        val requestBody = FormBody.Builder()
            .add("key", API_KEY)
            .add("image", base64Image)
            .add("name", PROFILE_IMAGE_PREFIX + System.currentTimeMillis())
            .build()

        val request = Request.Builder()
            .url(IMGBB_API_URL)
            .post(requestBody)
            .build()

        callback.onProgress(30)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, HTTP_REQUEST_ERROR, e)
                callback.onError(CONNECTION_ERROR_PREFIX + e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                callback.onProgress(80)

                response.use { resp ->
                    val responseBody = resp.body?.string()

                    if (responseBody == null) {
                        callback.onError(EMPTY_SERVER_RESPONSE)
                        return
                    }

                    try {
                        val imgbbResponse = gson.fromJson(responseBody, ImgBBResponse::class.java)

                        if (imgbbResponse.success && imgbbResponse.data != null) {
                            callback.onProgress(100)
                            callback.onSuccess(imgbbResponse.data.url, imgbbResponse.data.deleteUrl)
                        } else {
                            val errorMessage = imgbbResponse.error?.message ?: UNKNOWN_ERROR
                            callback.onError(IMGBB_ERROR_PREFIX + errorMessage)
                        }
                    } catch (e: Exception) {
                        callback.onError(RESPONSE_PROCESSING_ERROR + e.message)
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
            Log.e(TAG, BITMAP_TO_BASE64_ERROR, e)
            null
        }
    }
}