package pl.mmarkowebuty.admin.batch

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.data.ApiClient
import pl.mmarkowebuty.admin.data.ApiException
import pl.mmarkowebuty.admin.data.ProductDraft
import pl.mmarkowebuty.admin.security.SecureTokenStore
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

class BatchController(
    private val context: Context,
    private val vm: MainViewModel,
) {
    private val api = ApiClient()
    private val tokenStore = SecureTokenStore(context)
    private val _state = MutableStateFlow(BatchUiState())
    val state: StateFlow<BatchUiState> = _state.asStateFlow()

    fun updateLegalProfile(profile: BatchLegalProfile) {
        _state.value = _state.value.copy(legalProfile = profile)
    }

    suspend fun scan(rootUri: Uri) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: throw IllegalArgumentException("Nie udało się otworzyć wybranego folderu.")
        val folders = root.listFiles()
            .filter { it.isDirectory }
            .sortedBy { it.name?.lowercase(Locale.ROOT).orEmpty() }

        val items = folders.map { folder ->
            val name = folder.name ?: "Bez nazwy"
            val spec = FolderSpecParser.parse(name)
            val images = folder.listFiles()
                .filter { it.isFile && isImage(it) }
                .sortedBy { it.name?.lowercase(Locale.ROOT).orEmpty() }
                .take(10)
                .map { it.uri }

            val (stage, message) = when {
                spec == null -> BatchStage.INVALID to "Nazwa folderu nie zawiera poprawnie rozmiaru, długości wkładki i ceny."
                images.isEmpty() -> BatchStage.INVALID to "Folder nie zawiera zdjęć."
                else -> BatchStage.READY to "Gotowe do przetworzenia • ${images.size} zdjęć"
            }
            BatchItem(
                folderName = name,
                folderUri = folder.uri,
                spec = spec,
                imageUris = images,
                stage = stage,
                message = message,
            )
        }

        _state.value = _state.value.copy(
            rootUri = rootUri,
            rootName = root.name ?: "Wybrany folder",
            items = items,
            overallMessage = when {
                folders.isEmpty() -> "W wybranym folderze nie ma podfolderów z modelami."
                items.none { it.stage == BatchStage.READY } -> "Nie znaleziono folderu gotowego do przetworzenia."
                else -> "Znaleziono ${items.size} folderów, gotowych: ${items.count { it.stage == BatchStage.READY }}."
            }
        )
    }

    suspend fun processAll() {
        val start = _state.value
        if (start.running) return
        if (!start.legalProfile.canProcess()) {
            _state.value = start.copy(overallMessage = "Brakuje potwierdzonej nazwy lub adresu producenta. Uzupełnij dane producenta przed przetwarzaniem.")
            return
        }
        val token = tokenStore.read() ?: run {
            _state.value = start.copy(overallMessage = "Sesja administratora wygasła. Zaloguj się ponownie.")
            return
        }

        _state.value = start.copy(running = true, overallMessage = "Rozpoczynam seryjne przetwarzanie…")
        val processor = BatchPhotoProcessor(context)
        try {
            val indexes = _state.value.items.indices.toList()
            for (index in indexes) {
                val item = _state.value.items.getOrNull(index) ?: continue
                if (item.stage !in setOf(BatchStage.READY, BatchStage.ERROR)) continue
                processOne(index, item, token, processor)
            }
            val final = _state.value.items
            _state.value = _state.value.copy(
                running = false,
                overallMessage = "Zakończono. Wystawione: ${final.count { it.stage == BatchStage.DONE }}, do uzupełnienia: ${final.count { it.stage == BatchStage.LEGAL_REVIEW }}, błędy: ${final.count { it.stage == BatchStage.ERROR }}, pominięte: ${final.count { it.stage == BatchStage.INVALID }}."
            )
            vm.refreshAll(silent = true)
        } finally {
            processor.close()
            _state.value = _state.value.copy(running = false)
        }
    }

    private suspend fun processOne(index: Int, source: BatchItem, token: String, processor: BatchPhotoProcessor) {
        val spec = source.spec ?: return
        try {
            replace(index, source.copy(stage = BatchStage.ANALYZING, message = "Analizuję pierwsze zdjęcie…", processed = 0))
            val labels = mutableListOf<String>()
            val colors = mutableListOf<String>()
            val urls = mutableListOf<String>()

            source.imageUris.forEachIndexed { imageIndex, uri ->
                replace(
                    index,
                    current(index).copy(
                        stage = BatchStage.PROCESSING,
                        message = "Przetwarzam zdjęcie ${imageIndex + 1}/${source.imageUris.size}",
                        processed = imageIndex,
                    )
                )
                val processed = processor.process(uri)
                labels += processed.labels
                if (processed.colorName.isNotBlank()) colors += processed.colorName

                replace(
                    index,
                    current(index).copy(
                        stage = BatchStage.UPLOADING,
                        message = "Wysyłam zdjęcie ${imageIndex + 1}/${source.imageUris.size}",
                    )
                )
                val fileName = "batch-${System.currentTimeMillis()}-${index + 1}-${imageIndex + 1}.jpg"
                urls += uploadJpeg(processed.jpeg, fileName, token)
                replace(index, current(index).copy(processed = imageIndex + 1))
            }

            val category = BatchPhotoProcessor.categoryFromLabels(labels)
            val color = colors.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            val productName = listOf(color, category).filter { it.isNotBlank() }.joinToString(" ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pl", "PL")) else it.toString() }
                .ifBlank { "Buty" }
            val insolePl = spec.insoleCm.replace('.', ',')
            val description = "$productName. Rozmiar ${spec.size}, długość wkładki $insolePl cm. Oferta dotyczy jednej konkretnej pary widocznej na zdjęciach."
            val profile = _state.value.legalProfile
            val publishNow = profile.isReady()

            replace(
                index,
                current(index).copy(
                    stage = BatchStage.PUBLISHING,
                    message = if (publishNow) "Publikuję ofertę w mMarkoweButy…" else "Zapisuję ofertę jako ukrytą do uzupełnienia danych GPSR…",
                    detectedCategory = category,
                    detectedColor = color,
                )
            )

            val draft = ProductDraft(
                brand = profile.brand.ifBlank { "Bez marki" },
                name = productName,
                description = description,
                size = spec.size,
                price = spec.price,
                imageUrls = urls,
                published = publishNow,
                manufacturerName = profile.manufacturerName,
                manufacturerAddress = profile.manufacturerAddress,
                manufacturerEmail = profile.manufacturerEmail,
                responsiblePersonName = profile.responsiblePersonName,
                responsiblePersonAddress = profile.responsiblePersonAddress,
                responsiblePersonEmail = profile.responsiblePersonEmail,
                safetyInfo = profile.safetyInfo,
            )
            val productId = api.createProduct(token, draft)
            val verified = api.products(token).any {
                it.id == productId && it.published == publishNow && !it.sold && it.imageUrls.isNotEmpty() && it.sizes.contains(spec.size)
            }
            if (!verified) throw ApiException("Oferta została zapisana, ale nie przeszła końcowej weryfikacji.")

            if (publishNow) {
                replace(
                    index,
                    current(index).copy(
                        stage = BatchStage.DONE,
                        message = "Wystawiono: $productName • ${spec.size} • ${spec.price.replace('.', ',')} zł",
                        productId = productId,
                        processed = source.imageUris.size,
                    )
                )
            } else {
                replace(
                    index,
                    current(index).copy(
                        stage = BatchStage.LEGAL_REVIEW,
                        message = "Zdjęcia przetworzone i produkt zapisany jako UKRYTY. Uzupełnij e-mail producenta, aby opublikować ofertę.",
                        productId = productId,
                        processed = source.imageUris.size,
                    )
                )
            }
        } catch (e: Throwable) {
            replace(index, current(index).copy(stage = BatchStage.ERROR, message = e.message ?: "Nieznany błąd podczas przetwarzania."))
        }
    }

    private fun current(index: Int): BatchItem = _state.value.items[index]

    private fun replace(index: Int, item: BatchItem) {
        val list = _state.value.items.toMutableList()
        if (index in list.indices) list[index] = item
        _state.value = _state.value.copy(items = list)
    }

    private fun isImage(file: DocumentFile): Boolean {
        if (file.type?.startsWith("image/") == true) return true
        val name = file.name?.lowercase(Locale.ROOT).orEmpty()
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif").any(name::endsWith)
    }

    private suspend fun uploadJpeg(bytes: ByteArray, fileName: String, token: String): String = withContext(Dispatchers.IO) {
        val boundary = "----mm-batch-${UUID.randomUUID()}"
        val conn = (URL("${ApiClient.BASE_URL}/api/admin/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 40_000
            useCaches = false
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            BufferedOutputStream(conn.outputStream).use { out ->
                fun write(text: String) = out.write(text.toByteArray(StandardCharsets.UTF_8))
                write("--$boundary\r\n")
                write("Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\n")
                write("Content-Type: image/jpeg\r\n\r\n")
                out.write(bytes)
                write("\r\n--$boundary--\r\n")
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: "{}"
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw ApiException(json.optString("error").ifBlank { "Nie udało się wysłać przetworzonego zdjęcia." }, status)
            json.optString("url").takeIf { it.isNotBlank() }
                ?: throw ApiException("Serwer nie zwrócił adresu przetworzonego zdjęcia.")
        } finally {
            conn.disconnect()
        }
    }
}
