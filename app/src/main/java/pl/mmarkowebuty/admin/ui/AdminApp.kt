package pl.mmarkowebuty.admin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import pl.mmarkowebuty.admin.MainViewModel
import pl.mmarkowebuty.admin.data.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Screen(val label: String) {
    Dashboard("Pulpit"), Products("Produkty"), Orders("Zamówienia"), Customers("Klienci"), More("Więcej"),
    ProductEditor("Produkt"), Settings("Ustawienia"), InPost("InPost"), Reports("Raporty"), System("Stan sklepu"), Seo("SEO i Google")
}

private val moneyFmt = NumberFormat.getCurrencyInstance(Locale("pl", "PL"))
private fun moneyCents(cents: Int) = moneyFmt.format(cents / 100.0)
private fun money(value: Double) = moneyFmt.format(value)
private fun dateLabel(value: String): String = runCatching {
    val instant = Instant.parse(value)
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withLocale(Locale("pl", "PL")).withZone(ZoneId.systemDefault()).format(instant)
}.getOrElse { value.take(16).replace('T', ' ') }

@Composable
fun AdminApp(vm: MainViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, vm.loggedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && vm.loggedIn) vm.refreshAll(silent = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(vm.loggedIn) {
        while (vm.loggedIn) {
            delay(60_000)
            vm.refreshAll(silent = true)
        }
    }

    when {
        vm.checkingSession -> LoadingScreen("Sprawdzam bezpieczną sesję…")
        !vm.loggedIn -> LoginScreen(vm)
        else -> AdminShell(vm)
    }
}

@Composable
private fun LoadingScreen(text: String) {
    Box(Modifier.fillMaxSize().background(MmBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = MmOrange)
            Text(text, color = MmMuted)
        }
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var password by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(MmBg).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = MmCard),
            border = CardDefaults.outlinedCardBorder(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("mMarkoweButy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Panel administratora", color = MmMuted)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hasło administratora") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !vm.busy
                )
                vm.errorMessage?.let { ErrorBanner(it) { vm.clearMessage() } }
                Button(
                    onClick = { vm.login(password) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !vm.busy && password.isNotBlank()
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Zaloguj się")
                }
                Text("Aplikacja używa tej samej bazy i panelu logowania co mMarkoweButy.pl.", color = MmMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AdminShell(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Dashboard) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    val mainScreens = listOf(Screen.Dashboard, Screen.Products, Screen.Orders, Screen.Customers, Screen.More)

    Scaffold(
        containerColor = MmBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (screen == Screen.ProductEditor) editingProduct?.name ?: "Nowy produkt" else screen.label, fontWeight = FontWeight.Bold)
                        vm.lastSync?.let { Text("Synchronizacja: ${DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(it)}", style = MaterialTheme.typography.labelSmall, color = MmMuted) }
                    }
                },
                navigationIcon = {
                    if (screen !in mainScreens) {
                        IconButton(onClick = { screen = if (screen == Screen.ProductEditor) Screen.Products else Screen.More }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Wstecz")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshAll() }, enabled = !vm.busy) { Icon(Icons.Outlined.Refresh, contentDescription = "Odśwież") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0B0C))
            )
        },
        bottomBar = {
            if (screen in mainScreens) {
                NavigationBar(containerColor = Color(0xFF0B0B0C)) {
                    mainScreens.forEach { target ->
                        val icon = when (target) {
                            Screen.Dashboard -> Icons.Outlined.Dashboard
                            Screen.Products -> Icons.Outlined.Inventory2
                            Screen.Orders -> Icons.Outlined.ReceiptLong
                            Screen.Customers -> Icons.Outlined.PeopleAlt
                            else -> Icons.Outlined.MoreHoriz
                        }
                        NavigationBarItem(
                            selected = screen == target,
                            onClick = { screen = target },
                            icon = { Icon(icon, target.label) },
                            label = { Text(target.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MmOrange.copy(alpha = .18f), selectedIconColor = MmOrange, selectedTextColor = MmOrange)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.errorMessage?.let { ErrorBanner(it) { vm.clearMessage() } }
            vm.infoMessage?.let { InfoBanner(it) { vm.clearMessage() } }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MmOrange)
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.Dashboard -> DashboardScreen(vm, onOpenOrders = { screen = Screen.Orders }, onAddProduct = { editingProduct = null; screen = Screen.ProductEditor })
                    Screen.Products -> ProductsScreen(vm, onAdd = { editingProduct = null; screen = Screen.ProductEditor }, onEdit = { editingProduct = it; screen = Screen.ProductEditor })
                    Screen.Orders -> OrdersScreen(vm)
                    Screen.Customers -> CustomersScreen(vm)
                    Screen.More -> MoreScreen(
                        vm,
                        onNavigate = { screen = it },
                        onLogout = { vm.logout() }
                    )
                    Screen.ProductEditor -> ProductEditorScreen(vm, editingProduct, onDone = { screen = Screen.Products })
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.InPost -> InPostScreen(vm)
                    Screen.Reports -> ReportsScreen(vm)
                    Screen.System -> SystemScreen(vm)
                    Screen.Seo -> SeoScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = Color(0xFF431D1D), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MmRed)
            Spacer(Modifier.width(9.dp))
            Text(text, modifier = Modifier.weight(1f), color = Color(0xFFFFC0BC), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Zamknij") }
        }
    }
}

@Composable
private fun InfoBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = Color(0xFF123B2A), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MmGreen)
            Spacer(Modifier.width(9.dp))
            Text(text, modifier = Modifier.weight(1f), color = Color(0xFF9AEFC3), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Zamknij") }
        }
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel, onOpenOrders: () -> Unit, onAddProduct: () -> Unit) {
    val active = vm.products.count { it.published && !it.sold }
    val sold = vm.products.count { it.sold }
    val paid = vm.orders.filter { it.status == "paid" }
    val newOrders = paid.count { it.fulfillmentStatus == "new" }
    val revenue30 = paid.filter {
        runCatching { Instant.parse(it.createdAt).isAfter(Instant.now().minusSeconds(30L * 86400)) }.getOrDefault(false)
    }.sumOf { it.amountCents }
    val withoutImages = vm.products.count { it.published && !it.sold && it.imageUrls.isEmpty() && it.imageUrl.isNullOrBlank() }
    val withoutGpsr = vm.products.count { it.published && !it.sold && (it.manufacturerName.isBlank() || it.safetyInfo.isBlank()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Aktywne oferty", active.toString(), "gotowe do sprzedaży", Modifier.weight(1f))
                MetricCard("Do realizacji", newOrders.toString(), "opłacone nowe", Modifier.weight(1f), warning = newOrders > 0)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Obrót 30 dni", moneyCents(revenue30), "opłacone zamówienia", Modifier.weight(1f))
                MetricCard("Sprzedane", sold.toString(), "par w historii", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddProduct, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Dodaj produkt") }
                OutlinedButton(onClick = onOpenOrders, modifier = Modifier.weight(1f)) { Text("Zamówienia") }
            }
        }
        item {
            SectionCard("Wymaga uwagi") {
                StatusRow(withoutImages == 0, "Zdjęcia ofert", if (withoutImages == 0) "Wszystkie aktywne oferty mają zdjęcia" else "$withoutImages ofert bez zdjęć")
                StatusRow(withoutGpsr == 0, "Dane produktów / GPSR", if (withoutGpsr == 0) "Kompletne" else "$withoutGpsr ofert wymaga uzupełnienia")
                StatusRow(vm.gatewayHealthy, "Połączenie aplikacji", if (vm.gatewayHealthy) "Mobilne API działa" else "Sprawdź połączenie")
            }
        }
        item {
            Text("Ostatnie zamówienia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (vm.orders.isEmpty()) item { EmptyCard("Brak zamówień.") }
        items(vm.orders.take(5), key = { it.id }) { OrderCompactCard(it) }
    }
}

@Composable
private fun MetricCard(title: String, value: String, sub: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (warning) Color(0xFF1B1610) else MmCard), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MmMuted)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MmMuted)
        }
    }
}

@Composable
private fun ProductsScreen(vm: MainViewModel, onAdd: () -> Unit, onEdit: (Product) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("active") }
    val filtered = vm.products.filter { p ->
        val matches = query.isBlank() || listOf(p.brand, p.name, p.sizes.joinToString(), p.productIdentifier).joinToString(" ").contains(query, true)
        val status = when (filter) {
            "active" -> p.published && !p.sold
            "hidden" -> !p.published && !p.sold
            "sold" -> p.sold
            else -> true
        }
        matches && status
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Szukaj produktu") }, singleLine = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("active" to "Aktywne", "hidden" to "Ukryte", "sold" to "Sprzedane", "all" to "Wszystkie")) { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { filter = id }, label = { Text(label) })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (filtered.isEmpty()) EmptyCard("Brak produktów dla wybranego filtra.", Modifier.padding(14.dp))
            else LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { product -> ProductCard(product, onClick = { onEdit(product) }) }
                item { Spacer(Modifier.height(82.dp)) }
            }
            FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp), containerColor = MmOrange) { Icon(Icons.Outlined.Add, "Dodaj produkt", tint = Color.Black) }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(product.imageUrl, Modifier.size(88.dp), ContentScale.Fit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${product.brand} ${product.name}".trim(), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Rozmiar ${product.sizes.firstOrNull() ?: "—"} • ${money(product.price)}", color = MmOrange, fontWeight = FontWeight.Bold)
                Text("${product.imageUrls.size} zdjęć", color = MmMuted, style = MaterialTheme.typography.bodySmall)
                StatusPill(when { product.sold -> "SPRZEDANY"; product.published -> "AKTYWNY"; else -> "UKRYTY" }, when { product.sold -> MmRed; product.published -> MmGreen; else -> MmMuted })
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MmMuted)
        }
    }
}

@Composable
private fun ProductEditorScreen(vm: MainViewModel, product: Product?, onDone: () -> Unit) {
    var brand by remember(product?.id) { mutableStateOf(product?.brand ?: "") }
    var name by remember(product?.id) { mutableStateOf(product?.name ?: "") }
    var description by remember(product?.id) { mutableStateOf(product?.description ?: "") }
    var size by remember(product?.id) { mutableStateOf(product?.sizes?.firstOrNull() ?: "") }
    var price by remember(product?.id) { mutableStateOf(product?.price?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var published by remember(product?.id) { mutableStateOf(product?.published ?: true) }
    var identifier by remember(product?.id) { mutableStateOf(product?.productIdentifier ?: "") }
    var manufacturerName by remember(product?.id) { mutableStateOf(product?.manufacturerName ?: "") }
    var manufacturerAddress by remember(product?.id) { mutableStateOf(product?.manufacturerAddress ?: "") }
    var manufacturerEmail by remember(product?.id) { mutableStateOf(product?.manufacturerEmail ?: "") }
    var responsibleName by remember(product?.id) { mutableStateOf(product?.responsiblePersonName ?: "") }
    var responsibleAddress by remember(product?.id) { mutableStateOf(product?.responsiblePersonAddress ?: "") }
    var responsibleEmail by remember(product?.id) { mutableStateOf(product?.responsiblePersonEmail ?: "") }
    var safetyInfo by remember(product?.id) { mutableStateOf(product?.safetyInfo ?: "") }
    var materialUpper by remember(product?.id) { mutableStateOf(product?.materialUpper ?: "") }
    var materialLining by remember(product?.id) { mutableStateOf(product?.materialLining ?: "") }
    var materialSole by remember(product?.id) { mutableStateOf(product?.materialSole ?: "") }
    val images = remember(product?.id) { mutableStateListOf<String>().apply { addAll(product?.imageUrls ?: emptyList()) } }
    var showDelete by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val remaining = (10 - images.size).coerceAtLeast(0)
        vm.uploadImages(uris.take(remaining), onEach = { if (images.size < 10) images.add(it) })
    }

    val sold = product?.sold == true
    val draft = ProductDraft(
        brand, name, description, size, price, images.toList(), published, identifier,
        manufacturerName, manufacturerAddress, manufacturerEmail,
        responsibleName, responsibleAddress, responsibleEmail, safetyInfo,
        materialUpper, materialLining, materialSole
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (sold) WarningCard("Ta oferta jest sprzedana. Dla bezpieczeństwa historii sprzedaży aplikacja nie pozwala jej edytować ani usuwać.")
        Text("Podstawowe dane", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppField("Marka", brand, { brand = it }, enabled = !sold)
        AppField("Nazwa produktu", name, { name = it }, enabled = !sold)
        AppField("Opis", description, { description = it }, enabled = !sold, singleLine = false, minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField("Rozmiar", size, { size = it }, Modifier.weight(1f), enabled = !sold)
            AppField("Cena (zł)", price, { price = it }, Modifier.weight(1f), enabled = !sold, keyboardType = KeyboardType.Decimal)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Oferta publiczna", fontWeight = FontWeight.Medium); Text("Widoczna w sklepie", color = MmMuted, style = MaterialTheme.typography.bodySmall) }
            Switch(checked = published, onCheckedChange = { published = it }, enabled = !sold)
        }

        Text("Zdjęcia (${images.size}/10)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it }) { url ->
                    Box {
                        RemoteImage(url, Modifier.size(112.dp), ContentScale.Fit)
                        if (!sold) IconButton(onClick = { images.remove(url) }, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
                            Surface(color = Color.Black.copy(alpha = .7f), shape = RoundedCornerShape(50)) { Icon(Icons.Outlined.Close, "Usuń zdjęcie", modifier = Modifier.padding(5.dp)) }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !sold && !vm.busy && images.size < 10, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Dodaj zdjęcia z telefonu")
        }

        Text("Identyfikacja i GPSR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppField("EAN / identyfikator produktu", identifier, { identifier = it }, enabled = !sold)
        AppField("Producent", manufacturerName, { manufacturerName = it }, enabled = !sold)
        AppField("Adres producenta", manufacturerAddress, { manufacturerAddress = it }, enabled = !sold)
        AppField("E-mail producenta", manufacturerEmail, { manufacturerEmail = it }, enabled = !sold, keyboardType = KeyboardType.Email)
        AppField("Osoba odpowiedzialna (UE)", responsibleName, { responsibleName = it }, enabled = !sold)
        AppField("Adres osoby odpowiedzialnej", responsibleAddress, { responsibleAddress = it }, enabled = !sold)
        AppField("E-mail osoby odpowiedzialnej", responsibleEmail, { responsibleEmail = it }, enabled = !sold, keyboardType = KeyboardType.Email)
        AppField("Informacje bezpieczeństwa", safetyInfo, { safetyInfo = it }, enabled = !sold, singleLine = false, minLines = 3)

        Text("Materiały", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        AppField("Cholewka", materialUpper, { materialUpper = it }, enabled = !sold)
        AppField("Wyściółka", materialLining, { materialLining = it }, enabled = !sold)
        AppField("Podeszwa", materialSole, { materialSole = it }, enabled = !sold)

        if (!sold) {
            Button(onClick = { vm.saveProduct(product?.id, draft, onDone) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (product == null) "Wystaw produkt" else "Zapisz zmiany") }
            if (product != null) {
                OutlinedButton(onClick = { showDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MmRed), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Usuń ofertę")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDelete && product != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Usunięcie oferty") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Operacja usuwa niesprzedaną ofertę. Wpisz USUŃ, aby potwierdzić.")
                    OutlinedTextField(deleteConfirm, { deleteConfirm = it }, label = { Text("USUŃ") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.deleteProduct(product, deleteConfirm) { showDelete = false; onDone() } }, enabled = deleteConfirm.trim().uppercase() in setOf("USUŃ", "USUN") && !vm.busy) { Text("Usuń", color = MmRed) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Anuluj") } }
        )
    }
}

@Composable
private fun AppField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(value, onValueChange, modifier = modifier, label = { Text(label) }, enabled = enabled, singleLine = singleLine, minLines = minLines, keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
}

@Composable
private fun OrdersScreen(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selected by remember { mutableStateOf<Order?>(null) }
    val filtered = vm.orders.filter { o ->
        val hay = listOf(o.id, o.customerName, o.customerEmail, o.customerPhone, o.inpostPointId, o.trackingNumber).joinToString(" ")
        val matches = query.isBlank() || hay.contains(query, true)
        val status = filter == "all" || o.fulfillmentStatus == filter
        matches && status
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Szukaj zamówienia lub klienta") }, singleLine = true)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "Wszystkie", "new" to "Nowe", "processing" to "W realizacji", "shipped" to "Wysłane", "delivered" to "Dostarczone", "manual_review" to "Do sprawdzenia")) { (id, label) ->
                    FilterChip(filter == id, { filter = id }, { Text(label) })
                }
            }
        }
        if (filtered.isEmpty()) EmptyCard("Brak zamówień.", Modifier.padding(14.dp))
        else LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { order ->
                Card(Modifier.fillMaxWidth().clickable { selected = order }, colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.customerName ?: order.customerEmail ?: "Zamówienie ${order.id.take(8)}", fontWeight = FontWeight.Bold)
                                Text(dateLabel(order.createdAt), color = MmMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(moneyCents(order.amountCents), color = MmOrange, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(order.status.uppercase(), if (order.status == "paid") MmGreen else MmMuted)
                            StatusPill(order.fulfillmentStatus.uppercase(), if (order.fulfillmentStatus == "manual_review") MmRed else MmOrange)
                        }
                        order.inpostPointId?.let { Text("Paczkomat: $it ${order.inpostPointCity.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MmMuted) }
                        if (order.items.isNotEmpty()) Text(order.items.joinToString { "${it.productName} (${it.sizeLabel ?: "—"})" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    selected?.let { OrderDialog(it, onDismiss = { selected = null }, onSave = { status, carrier, tracking -> vm.updateFulfillment(it, status, carrier, tracking); selected = null }) }
}

@Composable
private fun OrderDialog(order: Order, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val statuses = listOf("new", "processing", "shipped", "delivered", "returned", "refunded", "cancelled", "manual_review")
    var status by remember(order.id) { mutableStateOf(order.fulfillmentStatus) }
    var carrier by remember(order.id) { mutableStateOf(order.carrier ?: if (order.shippingMethod?.contains("InPost", true) == true) "InPost" else "") }
    var tracking by remember(order.id) { mutableStateOf(order.trackingNumber ?: "") }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zamówienie ${order.id.take(8)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${order.customerName ?: "—"}\n${order.customerEmail ?: ""}\n${order.customerPhone ?: ""}")
                order.inpostPointId?.let { Text("Paczkomat: ${order.inpostPointName ?: it}\n${order.inpostPointAddress ?: ""}, ${order.inpostPointPostalCode ?: ""} ${order.inpostPointCity ?: ""}") }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Status: $status"); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.ArrowDropDown, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        statuses.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { status = s; menu = false }) }
                    }
                }
                AppField("Przewoźnik", carrier, { carrier = it })
                AppField("Numer śledzenia", tracking, { tracking = it })
                if (status == "refunded") WarningCard("Ten status zapisuje informację w panelu. Nie wykonuje automatycznej refundacji Stripe.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, carrier, tracking) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } }
    )
}

@Composable
private fun CustomersScreen(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val customers = vm.customers().filter { query.isBlank() || listOf(it.name, it.email, it.phone).joinToString(" ").contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(14.dp), leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Szukaj klienta") }, singleLine = true)
        if (customers.isEmpty()) EmptyCard("Brak klientów z opłaconych zamówień.", Modifier.padding(14.dp))
        else LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(customers, key = { it.key }) { c ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { Text(c.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(moneyCents(c.totalCents), color = MmOrange, fontWeight = FontWeight.Bold) }
                        if (c.email.isNotBlank()) Text(c.email, color = MmMuted)
                        if (c.phone.isNotBlank()) Text(c.phone, color = MmMuted)
                        Text("${c.orders} zamówień • ostatnie: ${dateLabel(c.lastOrder)}", style = MaterialTheme.typography.bodySmall, color = MmMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(vm: MainViewModel, onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { MoreItem(Icons.Outlined.LocalShipping, "Dostawa InPost", "Token i wybór Paczkomatów") { onNavigate(Screen.InPost) } }
        item { MoreItem(Icons.Outlined.Settings, "Ustawienia sklepu", "Kontakt, zwroty i cennik dostawy") { onNavigate(Screen.Settings) } }
        item { MoreItem(Icons.Outlined.Insights, "Raporty", "Sprzedaż, koszyk i rozmiary") { onNavigate(Screen.Reports) } }
        item { MoreItem(Icons.Outlined.Search, "SEO i Google", "Gotowość ofert i indeksacji") { onNavigate(Screen.Seo) } }
        item { MoreItem(Icons.Outlined.HealthAndSafety, "Stan sklepu", "API, Stripe, InPost i dane ofert") { onNavigate(Screen.System) } }
        item { Spacer(Modifier.height(4.dp)); OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MmRed)) { Icon(Icons.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Wyloguj") } }
    }
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MmOrange, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(sub, style = MaterialTheme.typography.bodySmall, color = MmMuted) }
            Icon(Icons.Outlined.ChevronRight, null, tint = MmMuted)
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel) {
    var email by remember(vm.settings) { mutableStateOf(vm.settings.customerEmail) }
    var phone by remember(vm.settings) { mutableStateOf(vm.settings.customerPhone) }
    var returns by remember(vm.settings) { mutableStateOf(vm.settings.returnsAddress) }
    var method by remember(vm.settings) { mutableStateOf(vm.settings.shippingMethod) }
    var shipping by remember(vm.settings) { mutableStateOf(vm.settings.shippingPrice.toString()) }
    var free by remember(vm.settings) { mutableStateOf(vm.settings.freeShippingFrom.toString()) }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Kontakt i obsługa") {
            AppField("E-mail klientów", email, { email = it }, keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(8.dp)); AppField("Telefon klientów", phone, { phone = it }, keyboardType = KeyboardType.Phone)
            Spacer(Modifier.height(8.dp)); AppField("Adres zwrotów", returns, { returns = it }, singleLine = false, minLines = 2)
        }
        SectionCard("Dostawa") {
            AppField("Metoda dostawy", method, { method = it })
            Spacer(Modifier.height(8.dp)); AppField("Cena dostawy (zł)", shipping, { shipping = it }, keyboardType = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp)); AppField("Darmowa dostawa od (zł)", free, { free = it }, keyboardType = KeyboardType.Decimal)
        }
        SectionCard("Dane chronione") {
            ReadOnlyRow("Nazwa sklepu", vm.settings.storeName)
            ReadOnlyRow("Nazwa prawna", vm.settings.legalName)
            ReadOnlyRow("NIP", vm.settings.nip)
            ReadOnlyRow("REGON", vm.settings.regon)
            ReadOnlyRow("Domena", vm.settings.websiteUrl)
            Text("Tych danych nie edytujemy z telefonu, żeby uniknąć przypadkowej zmiany danych prawnych lub domeny.", color = MmMuted, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { confirm = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Zapisz ustawienia") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Zapisać ustawienia?") },
        text = { Text("Zmiana zostanie od razu użyta również przez sklep internetowy.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            vm.saveSettings(vm.settings.copy(
                customerEmail = email.trim(), customerPhone = phone.trim(), returnsAddress = returns.trim(), shippingMethod = method.trim(),
                shippingPrice = shipping.replace(',', '.').toDoubleOrNull() ?: vm.settings.shippingPrice,
                freeShippingFrom = free.replace(',', '.').toDoubleOrNull() ?: vm.settings.freeShippingFrom
            ))
        }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } }
    )
}

@Composable
private fun InPostScreen(vm: MainViewModel) {
    var token by remember(vm.inPostSettings) { mutableStateOf(vm.inPostSettings.token) }
    var enabled by remember(vm.inPostSettings) { mutableStateOf(vm.inPostSettings.enabled) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("InPost Paczkomaty") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Wymagaj wyboru Paczkomatu", fontWeight = FontWeight.Bold); Text("Klient wybiera punkt przed płatnością", color = MmMuted, style = MaterialTheme.typography.bodySmall) }
                Switch(enabled, { enabled = it })
            }
            Spacer(Modifier.height(10.dp))
            AppField("Publiczny token Geowidget", token, { token = it }, singleLine = false, minLines = 3)
            Spacer(Modifier.height(8.dp))
            StatusRow(vm.inPostSettings.tokenValid, "Poprawność tokenu", when { vm.inPostSettings.tokenValid -> "Token jest prawidłowy"; vm.inPostSettings.tokenReason == "DOMAIN" -> "Token nie obejmuje domeny sklepu"; vm.inPostSettings.tokenReason == "EXPIRED" -> "Token wygasł"; else -> "Token wymaga sprawdzenia" })
        }
        WarningCard("To publiczny token Geowidget, nie sekret API. Nie zmieniaj go bez potrzeby, jeśli wybór Paczkomatów działa.")
        Button(onClick = { confirm = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Zapisz InPost") }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Zapisać konfigurację InPost?") },
        text = { Text("Zmiana wpłynie bezpośrednio na wybór Paczkomatu w sklepie.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.saveInPost(InPostSettings(token.trim(), enabled)) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } }
    )
}

@Composable
private fun ReportsScreen(vm: MainViewModel) {
    val paid = vm.orders.filter { it.status == "paid" }
    val revenue = paid.sumOf { it.amountCents }
    val avg = if (paid.isNotEmpty()) revenue / paid.size else 0
    val pairs = paid.sumOf { o -> o.items.sumOf { it.quantity } }
    val sizes = paid.flatMap { it.items }.groupingBy { it.sizeLabel ?: "—" }.eachCount().toList().sortedByDescending { it.second }.take(8)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Obrót", moneyCents(revenue), "opłacone", Modifier.weight(1f))
                MetricCard("Średni koszyk", moneyCents(avg), "opłacone", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Sprzedane pary", pairs.toString(), "opłacone", Modifier.weight(1f))
                MetricCard("Aktywne oferty", vm.products.count { it.published && !it.sold }.toString(), "w sklepie", Modifier.weight(1f))
            }
        }
        item {
            SectionCard("Najczęstsze rozmiary") {
                if (sizes.isEmpty()) Text("Brak danych.", color = MmMuted)
                else sizes.forEach { (size, count) -> ReadOnlyRow("Rozmiar $size", "$count szt.") }
            }
        }
        item { Text("Raporty są obliczane z tych samych zamówień, które widzi panel WWW.", color = MmMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SystemScreen(vm: MainViewModel) {
    val active = vm.products.filter { it.published && !it.sold }
    val imagesOk = active.all { it.imageUrls.isNotEmpty() || !it.imageUrl.isNullOrBlank() }
    val gpsrOk = active.all { it.manufacturerName.isNotBlank() && it.manufacturerAddress.isNotBlank() && it.manufacturerEmail.isNotBlank() && it.safetyInfo.isNotBlank() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("Połączenie") { StatusRow(vm.gatewayHealthy, "Mobilne API", if (vm.gatewayHealthy) "Działa" else "Brak potwierdzenia"); StatusRow(true, "Wspólna baza sklepu", "Aplikacja i strona używają tego samego backendu") } }
        item { SectionCard("Płatności i dostawa") { StatusRow(vm.system.stripePresent, "Stripe", "Tryb: ${vm.system.stripeMode}"); StatusRow(vm.system.inpostEnabled && vm.system.inpostTokenPresent, "InPost", if (vm.system.inpostEnabled) "Aktywny" else "Nieaktywny") } }
        item { SectionCard("Oferty") { StatusRow(imagesOk, "Zdjęcia", if (imagesOk) "Kompletne dla aktywnych ofert" else "Są aktywne oferty bez zdjęć"); StatusRow(gpsrOk, "GPSR", if (gpsrOk) "Kompletne" else "Część ofert wymaga uzupełnienia") } }
        item { SectionCard("Zabezpieczenia") { ReadOnlyRow("Prawdziwe zamówienia", "brak funkcji usuwania"); ReadOnlyRow("Sprzedane produkty", "edycja zablokowana w aplikacji"); ReadOnlyRow("DNS / Stripe LIVE", "poza aplikacją") } }
    }
}

@Composable
private fun SeoScreen(vm: MainViewModel) {
    val active = vm.products.filter { it.published && !it.sold }
    val imageOk = active.all { it.imageUrls.isNotEmpty() || !it.imageUrl.isNullOrBlank() }
    val gpsrOk = active.all { it.manufacturerName.isNotBlank() && it.safetyInfo.isNotBlank() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionCard("SEO sklepu") { StatusRow(true, "Domena kanoniczna", "https://www.mmarkowebuty.pl/"); StatusRow(imageOk, "Zdjęcia aktywnych ofert", if (imageOk) "Gotowe" else "Braki w ofertach"); StatusRow(gpsrOk, "Dane produktów", if (gpsrOk) "Gotowe" else "Wymagają uzupełnienia") } }
        item { WarningCard("robots.txt, sitemap.xml i indeksowanie Google są obsługiwane po stronie witryny. Aplikacja mobilna pokazuje stan ofert, ale nie zmienia DNS ani Search Console.") }
    }
}

@Composable
private fun OrderCompactCard(order: Order) {
    Card(colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(order.customerName ?: order.customerEmail ?: "${order.id.take(8)}", fontWeight = FontWeight.Bold); Text("${dateLabel(order.createdAt)} • ${order.fulfillmentStatus}", color = MmMuted, style = MaterialTheme.typography.bodySmall) }
            Text(moneyCents(order.amountCents), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(sub, color = MmMuted, style = MaterialTheme.typography.bodySmall) }
        StatusPill(if (ok) "OK" else "UWAGA", if (ok) MmGreen else MmRed)
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = MmMuted, modifier = Modifier.weight(.8f), style = MaterialTheme.typography.bodySmall)
        Text(value.ifBlank { "—" }, modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .16f), shape = RoundedCornerShape(50)) { Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
}

@Composable
private fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MmCard), border = CardDefaults.outlinedCardBorder()) { Text(text, Modifier.padding(18.dp), color = MmMuted) }
}

@Composable
private fun WarningCard(text: String) {
    Surface(color = Color(0xFF2C2410), shape = RoundedCornerShape(12.dp), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Outlined.WarningAmber, null, tint = Color(0xFFFFD56B)); Spacer(Modifier.width(8.dp)); Text(text, color = Color(0xFFFFE2A0), style = MaterialTheme.typography.bodySmall) }
    }
}
