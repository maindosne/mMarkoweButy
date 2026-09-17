package pl.mmarkowebuty.admin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.mlkit.barcode.Barcode
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.data.ProductDraft

@Composable
fun BarcodeWarehouseScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var barcode by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    Scaffold(
        containerColor = MmBg,
        topBar = {
            TopAppBar(
                title = { Text("Skaner magazynu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Zeskanuj kod kreskowy z pudełka, uzupełnij podstawowe dane i dodaj parę do magazynu. Produkt zostanie zapisany jako ukryty, więc nie pojawi się automatycznie w sklepie.", color = MmMuted)

            Button(
                onClick = {
                    scanError = null
                    scanner.startScan()
                        .addOnSuccessListener { result -> barcode = result.rawValue.orEmpty() }
                        .addOnFailureListener { scanError = "Nie udało się zeskanować kodu. Spróbuj ponownie." }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !vm.busy
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (barcode.isBlank()) "Skanuj kod kreskowy" else "Skanuj ponownie")
            }

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("EAN / kod kreskowy") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val existing = vm.products.firstOrNull { it.productIdentifier.trim() == barcode.trim() && barcode.isNotBlank() }
            if (existing != null) {
                Text("Ten kod jest już zapisany: ${existing.brand} ${existing.name}.", color = MmOrange, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("Marka") }, singleLine = true)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nazwa produktu") }, singleLine = true)
            OutlinedTextField(size, { size = it }, Modifier.fillMaxWidth(), label = { Text("Rozmiar") }, singleLine = true)
            OutlinedTextField(
                price,
                { price = it },
                Modifier.fillMaxWidth(),
                label = { Text("Cena (zł)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            Button(
                onClick = {
                    val draft = ProductDraft(
                        brand = brand,
                        name = name,
                        size = size,
                        price = price,
                        published = false,
                        productIdentifier = barcode
                    )
                    vm.saveProduct(null, draft) {
                        barcode = ""
                        brand = ""
                        name = ""
                        size = ""
                        price = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !vm.busy && existing == null && barcode.isNotBlank() && brand.isNotBlank() && name.isNotBlank() && size.isNotBlank() && price.replace(',', '.').toDoubleOrNull() != null
            ) {
                Text("DODAJ DO MAGAZYNU")
            }
        }
    }
}