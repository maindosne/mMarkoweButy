package pl.mmarkowebuty.admin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.data.Product
import pl.mmarkowebuty.admin.data.ProductDraft
import java.util.Locale

@Composable
fun BarcodeWarehouseScreen(vm: MainViewModel, onBack: () -> Unit) {
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var addMode by remember { mutableStateOf(false) }
    var brand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var ean by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MmBg,
        topBar = {
            TopAppBar(
                title = { Text(if (addMode) "Dodaj produkt" else "Magazyn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            addMode -> addMode = false
                            selectedProduct != null -> selectedProduct = null
                            else -> onBack()
                        }
                    }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Wstecz") }
                }
            )
        }
    ) { padding ->
        when {
            addMode -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Dodaj nowy produkt do magazynu. Kod EAN możesz wpisać teraz albo przypisać później.", color = MmMuted)
                OutlinedTextField(brand, { brand = it }, Modifier.fillMaxWidth(), label = { Text("Marka") }, singleLine = true)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nazwa produktu") }, singleLine = true)
                OutlinedTextField(size, { size = it }, Modifier.fillMaxWidth(), label = { Text("Rozmiar") }, singleLine = true)
                OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Cena (zł)") }, singleLine = true)
                OutlinedTextField(ean, { ean = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("EAN / kod kreskowy (opcjonalnie)") }, singleLine = true)
                Button(
                    onClick = {
                        vm.saveProduct(null, ProductDraft(brand = brand, name = name, size = size, price = price, published = false, productIdentifier = ean)) {
                            brand = ""; name = ""; size = ""; price = ""; ean = ""; addMode = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !vm.busy && brand.isNotBlank() && name.isNotBlank() && size.isNotBlank() && price.replace(',', '.').toDoubleOrNull() != null
                ) { Text("DODAJ PRODUKT") }
            }

            selectedProduct != null -> {
                val p = selectedProduct!!
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${p.brand} ${p.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Rozmiar: ${p.sizes.joinToString().ifBlank { "—" }}")
                    Text("EAN: ${p.productIdentifier.ifBlank { "nie przypisano" }}")
                    Text("Cena: ${String.format(Locale.US, "%.2f", p.price)} zł")
                    Text(if (p.published) "Status: aktywny w sklepie" else "Status: w magazynie / ukryty", color = MmMuted)
                }
            }

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Button(
                    onClick = { addMode = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("DODAJ PRODUKT")
                }

                Text("Produkty", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                if (vm.products.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nie ma jeszcze produktów.", color = MmMuted)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(vm.products, key = { it.id }) { product ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedProduct = product },
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = MmOrange)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${product.brand} ${product.name}", fontWeight = FontWeight.Bold)
                                        Text("Rozmiar: ${product.sizes.joinToString().ifBlank { "—" }}", color = MmMuted)
                                        if (product.productIdentifier.isNotBlank()) Text("EAN: ${product.productIdentifier}", color = MmMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
