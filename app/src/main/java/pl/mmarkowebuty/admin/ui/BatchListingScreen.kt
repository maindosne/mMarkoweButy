package pl.mmarkowebuty.admin.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.batch.*

@Composable
fun BatchListingScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val controller = remember { BatchController(context.applicationContext, vm) }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()

    var brand by rememberSaveable { mutableStateOf("Bez marki") }
    var brandMenuExpanded by remember { mutableStateOf(false) }
    var manufacturerName by rememberSaveable { mutableStateOf("") }
    var manufacturerAddress by rememberSaveable { mutableStateOf("") }
    var manufacturerEmail by rememberSaveable { mutableStateOf("") }
    var responsibleName by rememberSaveable { mutableStateOf("") }
    var responsibleAddress by rememberSaveable { mutableStateOf("") }
    var responsibleEmail by rememberSaveable { mutableStateOf("") }
    var safetyInfo by rememberSaveable { mutableStateOf("") }

    fun syncLegal() {
        controller.updateLegalProfile(
            BatchLegalProfile(
                brand = brand.trim().ifBlank { "Bez marki" },
                manufacturerName = manufacturerName.trim(),
                manufacturerAddress = manufacturerAddress.trim(),
                manufacturerEmail = manufacturerEmail.trim(),
                responsiblePersonName = responsibleName.trim(),
                responsiblePersonAddress = responsibleAddress.trim(),
                responsiblePersonEmail = responsibleEmail.trim(),
                safetyInfo = safetyInfo.trim(),
            )
        )
    }

    fun applyManufacturerProfile(profile: ManufacturerProfile?) {
        if (profile == null) {
            brand = "Bez marki"
            manufacturerName = ""
            manufacturerAddress = ""
            manufacturerEmail = ""
        } else {
            brand = profile.brand
            manufacturerName = profile.manufacturerName
            manufacturerAddress = profile.manufacturerAddress
            manufacturerEmail = profile.manufacturerEmail
        }
        syncLegal()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch { runCatching { controller.scan(uri) } }
        }
    }

    Scaffold(
        containerColor = MmBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Automatyczne wystawianie", fontWeight = FontWeight.Bold)
                        Text("folder → obróbka → oferta", style = MaterialTheme.typography.labelSmall, color = MmMuted)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.running) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0B0C))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = MmOrange)
                            Spacer(Modifier.width(10.dp))
                            Text("Jak przygotować foldery", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "W folderze głównym umieść osobny podfolder dla każdej pary. Nazwa przykładowa: r.39_dl.wkl. 25,3 cm_65zl. W środku mogą być zdjęcia JPG, PNG, WEBP, HEIC lub HEIF.",
                            color = MmMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Aplikacja bierze maksymalnie 10 zdjęć z jednego folderu i przetwarza modele po kolei. Produkt nie jest generowany od nowa — zmieniane jest tło i prezentacja.",
                            color = MmMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { folderPicker.launch(null) },
                            enabled = !state.running,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.rootUri == null) "WYBIERZ FOLDER GŁÓWNY" else "ZMIEŃ FOLDER")
                        }
                        if (state.rootName.isNotBlank()) Text("Wybrano: ${state.rootName}", color = MmMuted)
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Profil prawny tej partii", fontWeight = FontWeight.Bold)
                        Text(
                            "Wybierz markę, a zapisane dane producenta zostaną uzupełnione automatycznie. Pola nadal można ręcznie poprawić przed wystawieniem.",
                            color = MmMuted,
                            style = MaterialTheme.typography.bodySmall
                        )

                        ExposedDropdownMenuBox(
                            expanded = brandMenuExpanded,
                            onExpandedChange = { if (!state.running) brandMenuExpanded = !brandMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = {},
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                readOnly = true,
                                enabled = !state.running,
                                label = { Text("Marka") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = brandMenuExpanded) }
                            )
                            ExposedDropdownMenu(
                                expanded = brandMenuExpanded,
                                onDismissRequest = { brandMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bez marki") },
                                    onClick = {
                                        brandMenuExpanded = false
                                        applyManufacturerProfile(null)
                                    }
                                )
                                ManufacturerProfiles.all.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.brand) },
                                        onClick = {
                                            brandMenuExpanded = false
                                            applyManufacturerProfile(profile)
                                        }
                                    )
                                }
                            }
                        }

                        BatchField("Producent – nazwa", manufacturerName) { manufacturerName = it; syncLegal() }
                        BatchField("Producent – adres", manufacturerAddress, singleLine = false) { manufacturerAddress = it; syncLegal() }
                        BatchField("Producent – e-mail", manufacturerEmail) { manufacturerEmail = it; syncLegal() }
                        BatchField("Osoba odpowiedzialna w UE – nazwa (jeśli dotyczy)", responsibleName) { responsibleName = it; syncLegal() }
                        BatchField("Osoba odpowiedzialna w UE – adres", responsibleAddress, singleLine = false) { responsibleAddress = it; syncLegal() }
                        BatchField("Osoba odpowiedzialna w UE – e-mail", responsibleEmail) { responsibleEmail = it; syncLegal() }
                        BatchField("Informacje / ostrzeżenia bezpieczeństwa (tylko jeśli znane)", safetyInfo, singleLine = false) { safetyInfo = it; syncLegal() }

                        val selectedProfile = ManufacturerProfiles.find(brand)
                        if (selectedProfile != null && (selectedProfile.manufacturerAddress.isBlank() || selectedProfile.manufacturerEmail.isBlank())) {
                            Text(
                                "Dla marki ${selectedProfile.brand} część danych kontaktowych producenta nie jest jeszcze potwierdzona. Nie uzupełniamy ich na podstawie domysłów.",
                                color = MmOrange,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        val legalReady = manufacturerName.isNotBlank() && manufacturerAddress.isNotBlank() && manufacturerEmail.isNotBlank()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (legalReady) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                                null,
                                tint = if (legalReady) MmGreen else MmOrange
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (legalReady) "Dane producenta gotowe." else "Do automatycznej publikacji potrzebne są prawdziwe dane producenta.",
                                color = if (legalReady) MmGreen else MmMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (state.overallMessage.isNotBlank()) {
                item {
                    Surface(color = Color(0xFF17191C), shape = RoundedCornerShape(12.dp)) {
                        Text(state.overallMessage, Modifier.fillMaxWidth().padding(12.dp), color = MmMuted)
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item {
                    val legalReady = manufacturerName.isNotBlank() && manufacturerAddress.isNotBlank() && manufacturerEmail.isNotBlank()
                    Button(
                        onClick = { syncLegal(); scope.launch { controller.processAll() } },
                        enabled = !state.running && legalReady && state.items.any { it.stage == BatchStage.READY || it.stage == BatchStage.ERROR },
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("PRZETWARZANIE…")
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text("PRZETWÓRZ WSZYSTKIE ZDJĘCIA")
                        }
                    }
                }

                itemsIndexed(state.items, key = { _, item -> item.folderUri.toString() }) { _, item ->
                    BatchItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun BatchField(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
    )
}

@Composable
private fun BatchItemCard(item: BatchItem) {
    val accent = when (item.stage) {
        BatchStage.DONE -> MmGreen
        BatchStage.ERROR, BatchStage.INVALID -> MmRed
        BatchStage.LEGAL_REVIEW -> MmOrange
        BatchStage.READY -> MmMuted
        else -> MmOrange
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MmCard),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.folderName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(stageLabel(item.stage), color = accent, style = MaterialTheme.typography.labelMedium)
            }
            item.spec?.let {
                Text(
                    "Rozmiar ${it.size} • wkładka ${it.insoleCm.replace('.', ',')} cm • ${it.price.replace('.', ',')} zł • ${item.imageUris.size} zdjęć",
                    color = MmMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (item.detectedCategory.isNotBlank() || item.detectedColor.isNotBlank()) {
                Text("Rozpoznano: ${listOf(item.detectedColor, item.detectedCategory).filter { it.isNotBlank() }.joinToString(" ")}", color = MmMuted)
            }
            if (item.stage in setOf(BatchStage.ANALYZING, BatchStage.PROCESSING, BatchStage.UPLOADING, BatchStage.PUBLISHING)) {
                val total = item.total.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (item.processed.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MmOrange
                )
            }
            Text(item.message, color = accent, style = MaterialTheme.typography.bodySmall)
            item.productId?.let { Text("ID produktu: $it", color = MmMuted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun stageLabel(stage: BatchStage): String = when (stage) {
    BatchStage.READY -> "GOTOWY"
    BatchStage.INVALID -> "POMINIĘTY"
    BatchStage.ANALYZING -> "ANALIZA"
    BatchStage.PROCESSING -> "OBRÓBKA"
    BatchStage.UPLOADING -> "WYSYŁANIE"
    BatchStage.PUBLISHING -> "WYSTAWIANIE"
    BatchStage.LEGAL_REVIEW -> "SPRAWDŹ"
    BatchStage.DONE -> "WYSTAWIONO"
    BatchStage.ERROR -> "BŁĄD"
}
