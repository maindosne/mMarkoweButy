package pl.mmarkowebuty.admin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max

class ApiException(message: String, val statusCode: Int = 0) : Exception(message)

class ApiClient {
    companion object {
        const val BASE_URL = "https://eufwkwksjcdzfmlaugbj.supabase.co/functions/v1/mmarkowebuty-mobile-api"
    }

    suspend fun health(): JSONObject = withContext(Dispatchers.IO) {
        request("/health")
    }

    suspend fun login(password: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("password", password)
        val result = request("/api/admin/login", "POST", body)
        val token = result.optString("token")
        val expiresAt = result.optString("expiresAt")
        if (token.isBlank()) throw ApiException("Backend nie zwrócił sesji administratora.")
        token to expiresAt
    }

    suspend fun session(token: String): Boolean = withContext(Dispatchers.IO) {
        request("/api/admin/session", token = token).optBoolean("authenticated", false)
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        runCatching { request("/api/admin/logout", "POST", JSONObject(), token) }
        Unit
    }

    suspend fun products(token: String): List<Product> = withContext(Dispatchers.IO) {
        parseProducts(request("/api/admin/products", token = token))
    }

    suspend fun orders(token: String): List<Order> = withContext(Dispatchers.IO) {
        parseOrders(request("/api/admin/orders", token = token))
    }

    suspend fun settings(token: String): Pair<StoreSettings, BackendSystemStatus> = withContext(Dispatchers.IO) {
        parseSettings(request("/api/admin/store-settings", token = token))
    }

    suspend fun inPostSettings(token: String): InPostSettings = withContext(Dispatchers.IO) {
        parseInPostSettings(request("/api/inpost/admin-settings", token = token))
    }

    suspend fun saveInPostSettings(token: String, settings: InPostSettings) = withContext(Dispatchers.IO) {
        request(
            "/api/inpost/admin-settings",
            "PUT",
            JSONObject().put("geowidgetToken", settings.token).put("enabled", settings.enabled),
            token
        )
        Unit
    }

    suspend fun saveSettings(token: String, settings: StoreSettings) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("customerEmail", settings.customerEmail)
            .put("customerPhone", settings.customerPhone)
            .put("returnsAddress", settings.returnsAddress)
            .put("shippingMethod", settings.shippingMethod)
            .put("shippingPrice", settings.shippingPrice)
            .put("freeShippingFrom", settings.freeShippingFrom)
        request("/api/admin/store-settings", "PUT", body, token)
        Unit
    }

    suspend fun createProduct(token: String, draft: ProductDraft): Long = withContext(Dispatchers.IO) {
        request("/api/admin/products", "POST", draft.toJson(), token).optLong("id")
    }

    suspend fun updateProduct(token: String, id: Long, draft: ProductDraft) = withContext(Dispatchers.IO) {
        request("/api/admin/products/$id", "PUT", draft.toJson(), token)
        Unit
    }

    suspend fun deleteProduct(token: String, id: Long) = withContext(Dispatchers.IO) {
        request("/api/admin/products/$id", "DELETE", null, token)
        Unit
    }

    suspend fun updateFulfillment(token: String, orderId: String, status: String, carrier: String, trackingNumber: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("status", status)
            .put("carrier", carrier)
            .put("trackingNumber", trackingNumber)
        request("/api/admin/orders/$orderId/fulfillment", "PUT", body, token)
        Unit
    }

    suspend fun uploadImage(context: Context, uri: Uri, token: String): String = withContext(Dispatchers.IO) {
        val bytes = prepareImage(context, uri)
        val fileName = queryDisplayName(context, uri).substringBeforeLast('.', "produkt") + ".jpg"
        multipartUpload(fileName, bytes, token)
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        token: String? = null,
    ): JSONObject {
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val source = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = source?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "{}"
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                val msg = json.optString("error").ifBlank { "Błąd połączenia ze sklepem ($status)." }
                throw ApiException(msg, status)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun multipartUpload(fileName: String, bytes: ByteArray, token: String): String {
        val boundary = "----mmarkowebuty-${UUID.randomUUID()}"
        val conn = (URL("$BASE_URL/api/admin/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            useCaches = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            BufferedOutputStream(conn.outputStream).use { out ->
                fun write(s: String) = out.write(s.toByteArray(StandardCharsets.UTF_8))
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\n")
                write("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
                write("\r\n--$boundary--\r\n")
            }
            val status = conn.responseCode
            val source = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = source?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "{}"
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw ApiException(json.optString("error").ifBlank { "Nie udało się wysłać zdjęcia." }, status)
            return json.optString("url").takeIf { it.isNotBlank() } ?: throw ApiException("Backend nie zwrócił adresu zdjęcia.")
        } finally {
            conn.disconnect()
        }
    }

    private fun prepareImage(context: Context, uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val largest = max(w, h)
            if (largest > 1800) {
                val scale = 1800.0 / largest
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        val result = out.toByteArray()
        if (result.size > 5 * 1024 * 1024) {
            out.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, out)
        }
        val finalBytes = out.toByteArray()
        if (finalBytes.size > 5 * 1024 * 1024) throw ApiException("Zdjęcie po kompresji nadal przekracza 5 MB.")
        return finalBytes
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx) ?: "produkt.jpg"
            }
        }
        return "produkt.jpg"
    }
}
