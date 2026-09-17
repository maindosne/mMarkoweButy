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
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.data.Product
import pl.mmarkowebuty.admin.data.ProductDraft
import java.util.Locale

private fun Product.warehouseDraft(code: String) = ProductDraft(
    brand = brand,
    name = name,
    description = description,
    size = sizes.firstOrNull().orEmpty(),
    price = String.format(Locale.US, "%.2f", price),
    imageUrls = imageUrls,
    published = false,
    productIdentifier = code,
    manufacturerName = manufacturerName,
    manufacturerAddress = manufacturerAddress,
    manufacturerEmail = manufacturerEmail,
    responsiblePersonName = responsiblePersonName,
    responsiblePersonAddress = responsiblePersonAddress,
    responsiblePersonEmail = responsiblePersonEmail,
    safetyInfo = safetyInfo,
    materialUpper = materialUpper,
    materialLining = materialLining,
    materialSole = materialSole,
)

@Composable
fun BarcodeWarehouseScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var barcode by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var lastAdded by remember { mutableStateOf<String?>(null) }
    var matchedProduct by remember { mutableStateOf<Product?>(null) }

    fun fillFromProduct(product: Product) {
        matchedProduct = product
        brand = product.brand
        name = product.name
        size = product.sizes.firstOrNull().orEmpty()
        price = String.format(Locale.US, "%.2f", product.price)
    }

    fun addMatchedProduct(code: String, product: Product) {
        fillFromProduct(product)
        scanError = null
        lastAdded = null
        vm.saveProduct(null, product.warehouseDraft(code)) {
            val stock = vm.products.count { it.productIdentifier.trim() == code && !it.sold }
            lastAdded = "Dodano do magazynu: ${product.brand} ${product.name}, rozmiar ${product.sizes.firstOrNull().orEmpty()}. Stan dla tego kodu: $stock szt."
        }
    }

    fun handleCode(raw: String) {
        val code = raw.trim()
        barcode = code
        scanError = null
        lastAdded = null
        matchedProduct = null
        if (code.isBlank()) {
            scanError = "Skaner nie odczytał kodu."
            return
        }
        val product = vm.products.firstOrNull { it.productIdentifier.trim() == code }
        if (product != null) {
            addMatchedProduct(code, product)
        } else {
            brand = ""
            name = ""
            size = ""
            price = ""
            scanError = "Nie znaleziono tego kodu w produktach. Uzupełnij dane tylko za pierwszym razem."
        }
    }

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
            Text("Zeskanuj kod raz. Jeżeli EAN jest już przypisany do produktu, aplikacja rozpozna parę, pokaże jej dane i automatycznie doda kolejną fizyczną parę do magazynu.", color = MmMuted)

            Button(
                onClick = {
                    scanError = null
                    scanner.startScan()
                        .addOnSuccessListener { result -> handleCode(result.rawValue.orEmpty()) }
                        .addOnCanceledListener { scanError = null }
                        .addOnFailureListener { scanError = "Nie udało się zeskanować kodu. Spróbuj ponownie." }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !vm.busy
            ) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (vm.busy) "Dodawanie..." else "Skanuj i dodaj do magazynu")
            }

            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filter(Char::isDigit); matchedProduct = null; lastAdded = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("EAN / kod kreskowy") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            lastAdded?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            scanError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            matchedProduct?.let { product ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ROZPOZNANO PRODUKT", fontWeight = FontWeight.Bold, color = MmOrange)
                        Text("But: ${product.brand} ${product.name}", fontWeight = FontWeight.Bold)
                        Text("Rozmiar: ${product.sizes.firstOrNull().orEmpty()}")
                        Text("Kolor / opis: ${product.description.ifBlank { "brak zapisanego koloru/opisu" }}")
                        Text("EAN: ${product.productIdentifier}")
                        Text("Cena: ${String.format(Locale.US, "%.2f", product.price)} zł")
                    }
                }
            }

            if (matchedProduct == null) {
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
                        val savedCode = barcode
                        vm.saveProduct(null, draft) {
                            val stock = vm.products.count { it.productIdentifier.trim() == savedCode && !it.sold }
                            lastAdded = "Nowy kod zapisany w magazynie. Stan: $stock szt."
                            vm.products.firstOrNull { it.productIdentifier.trim() == savedCode }?.let(::fillFromProduct)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !vm.busy && barcode.isNotBlank() && brand.isNotBlank() && name.isNotBlank() && size.isNotBlank() && price.replace(',', '.').toDoubleOrNull() != null
                ) {
                    Text("ZAPISZ NOWY KOD W MAGAZYNIE")
                }
            }
        }
    }
}