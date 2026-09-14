package pl.mmarkowebuty.admin.batch

import android.net.Uri

data class FolderSpec(
    val size: String,
    val insoleCm: String,
    val price: String,
)

enum class BatchStage {
    READY,
    INVALID,
    ANALYZING,
    PROCESSING,
    UPLOADING,
    PUBLISHING,
    LEGAL_REVIEW,
    DONE,
    ERROR,
}

data class BatchItem(
    val folderName: String,
    val folderUri: Uri,
    val spec: FolderSpec?,
    val imageUris: List<Uri>,
    val stage: BatchStage,
    val message: String = "",
    val processed: Int = 0,
    val total: Int = imageUris.size,
    val detectedCategory: String = "",
    val detectedColor: String = "",
    val productId: Long? = null,
)

data class BatchLegalProfile(
    val brand: String = "Bez marki",
    val manufacturerName: String = "",
    val manufacturerAddress: String = "",
    val manufacturerEmail: String = "",
    val responsiblePersonName: String = "",
    val responsiblePersonAddress: String = "",
    val responsiblePersonEmail: String = "",
    val safetyInfo: String = "",
) {
    fun canProcess(): Boolean = manufacturerName.isNotBlank() && manufacturerAddress.isNotBlank()
    fun isReady(): Boolean = canProcess() && manufacturerEmail.isNotBlank()
}

data class BatchUiState(
    val rootUri: Uri? = null,
    val rootName: String = "",
    val items: List<BatchItem> = emptyList(),
    val running: Boolean = false,
    val overallMessage: String = "",
    val legalProfile: BatchLegalProfile = BatchLegalProfile(),
)

object FolderSpecParser {
    private val sizeRx = Regex("(?i)(?:^|[_\\s-])r(?:ozm(?:iar)?)?\\.?\\s*([0-9]{2}(?:[.,]5)?)")
    private val insoleRx = Regex("(?i)(?:dl\\.?\\s*wkl\\.?|dł\\.?\\s*wkł\\.?|wkładka|wkladka|długość\\s*wkładki|dlugosc\\s*wkladki)\\s*[:.-]?\\s*([0-9]{2}(?:[.,][0-9])?)\\s*cm")
    private val priceRx = Regex("(?i)([0-9]+(?:[.,][0-9]{1,2})?)\\s*z(?:ł|l)")

    fun parse(name: String): FolderSpec? {
        val size = sizeRx.find(name)?.groupValues?.getOrNull(1)?.replace(',', '.') ?: return null
        val insole = insoleRx.find(name)?.groupValues?.getOrNull(1)?.replace(',', '.') ?: return null
        val price = priceRx.find(name)?.groupValues?.getOrNull(1)?.replace(',', '.') ?: return null
        if (price.toDoubleOrNull() == null || insole.toDoubleOrNull() == null) return null
        return FolderSpec(size = size, insoleCm = insole, price = price)
    }
}
