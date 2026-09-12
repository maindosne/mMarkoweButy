package pl.mmarkowebuty.admin

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pl.mmarkowebuty.admin.data.*
import pl.mmarkowebuty.admin.security.SecureTokenStore
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ApiClient()
    private val tokens = SecureTokenStore(application)

    var checkingSession by mutableStateOf(true)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var infoMessage by mutableStateOf<String?>(null)
        private set
    var products by mutableStateOf<List<Product>>(emptyList())
        private set
    var orders by mutableStateOf<List<Order>>(emptyList())
        private set
    var settings by mutableStateOf(StoreSettings())
        private set
    var system by mutableStateOf(BackendSystemStatus())
        private set
    var inPostSettings by mutableStateOf(InPostSettings())
        private set
    var gatewayHealthy by mutableStateOf(false)
        private set
    var lastSync by mutableStateOf<Instant?>(null)
        private set

    init {
        validateStoredSession()
    }

    private fun tokenOrThrow(): String = tokens.read() ?: throw ApiException("Sesja administratora wygasła. Zaloguj się ponownie.", 401)

    private fun validateStoredSession() {
        viewModelScope.launch {
            val token = tokens.read()
            if (token.isNullOrBlank()) {
                checkingSession = false
                return@launch
            }
            runCatching { api.session(token) }
                .onSuccess {
                    loggedIn = it
                    if (it) refreshAll(silent = true) else tokens.clear()
                }
                .onFailure { tokens.clear() }
            checkingSession = false
        }
    }

    fun login(password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val (token, expiresAt) = api.login(password)
                tokens.save(token, expiresAt)
                loggedIn = true
                refreshAll(silent = true)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
                checkingSession = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = tokens.read()
            if (!token.isNullOrBlank()) api.logout(token)
            tokens.clear()
            loggedIn = false
            products = emptyList()
            orders = emptyList()
            infoMessage = null
            errorMessage = null
        }
    }

    fun clearMessage() {
        infoMessage = null
        errorMessage = null
    }

    fun refreshAll(silent: Boolean = false) {
        if (!loggedIn || busy && !silent) return
        viewModelScope.launch {
            if (!silent) busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                coroutineScope {
                    val p = async { api.products(token) }
                    val o = async { api.orders(token) }
                    val s = async { api.settings(token) }
                    val ip = async { api.inPostSettings(token) }
                    val h = async { runCatching { api.health() }.getOrNull() }
                    products = p.await()
                    orders = o.await()
                    val (store, sys) = s.await()
                    settings = store
                    system = sys
                    inPostSettings = ip.await()
                    gatewayHealthy = h.await()?.optBoolean("ok", false) == true
                }
                lastSync = Instant.now()
                if (!silent) infoMessage = "Dane sklepu odświeżone."
            } catch (e: Exception) {
                handleError(e)
            } finally {
                if (!silent) busy = false
            }
        }
    }

    fun saveProduct(id: Long?, draft: ProductDraft, onDone: () -> Unit) {
        if (draft.brand.isBlank() || draft.name.isBlank() || draft.size.isBlank() || draft.price.replace(',', '.').toDoubleOrNull() == null) {
            errorMessage = "Uzupełnij markę, nazwę, jeden rozmiar i prawidłową cenę."
            return
        }
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                if (id == null) api.createProduct(token, draft) else api.updateProduct(token, id, draft)
                products = api.products(token)
                lastSync = Instant.now()
                infoMessage = if (id == null) "Produkt został dodany." else "Produkt został zapisany."
                onDone()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun deleteProduct(product: Product, confirmation: String, onDone: () -> Unit) {
        if (confirmation.trim().uppercase() !in setOf("USUŃ", "USUN")) {
            errorMessage = "Wpisz USUŃ, aby potwierdzić."
            return
        }
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                api.deleteProduct(tokenOrThrow(), product.id)
                products = api.products(tokenOrThrow())
                lastSync = Instant.now()
                infoMessage = "Oferta została usunięta."
                onDone()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun uploadImages(uris: List<Uri>, onEach: (String) -> Unit, onDone: () -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                for (uri in uris.take(10)) {
                    val url = api.uploadImage(getApplication(), uri, token)
                    onEach(url)
                }
                infoMessage = "Zdjęcia zostały wysłane."
                onDone()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun updateFulfillment(order: Order, status: String, carrier: String, tracking: String) {
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                api.updateFulfillment(token, order.id, status, carrier, tracking)
                orders = api.orders(token)
                lastSync = Instant.now()
                infoMessage = "Status zamówienia został zapisany."
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun saveSettings(updated: StoreSettings) {
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                api.saveSettings(token, updated)
                val (store, sys) = api.settings(token)
                settings = store
                system = sys
                lastSync = Instant.now()
                infoMessage = "Ustawienia sklepu zostały zapisane."
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun saveInPost(updated: InPostSettings) {
        viewModelScope.launch {
            busy = true
            errorMessage = null
            try {
                val token = tokenOrThrow()
                api.saveInPostSettings(token, updated)
                inPostSettings = api.inPostSettings(token)
                val (_, sys) = api.settings(token)
                system = sys
                lastSync = Instant.now()
                infoMessage = "Ustawienia InPost zostały zapisane."
            } catch (e: Exception) {
                handleError(e)
            } finally {
                busy = false
            }
        }
    }

    fun customers(): List<CustomerSummary> {
        val map = linkedMapOf<String, CustomerSummary>()
        orders.filter { it.status == "paid" }.forEach { order ->
            val key = (order.customerEmail ?: order.customerPhone ?: order.customerName ?: order.id).lowercase()
            val old = map[key]
            map[key] = CustomerSummary(
                key = key,
                name = order.customerName ?: old?.name ?: "—",
                email = order.customerEmail ?: old?.email ?: "",
                phone = order.customerPhone ?: old?.phone ?: "",
                orders = (old?.orders ?: 0) + 1,
                totalCents = (old?.totalCents ?: 0) + order.amountCents,
                lastOrder = if ((old?.lastOrder ?: "") >= order.createdAt) (old?.lastOrder ?: "") else order.createdAt,
            )
        }
        return map.values.sortedByDescending { it.totalCents }
    }

    private fun handleError(e: Exception) {
        if (e is ApiException && e.statusCode == 401) {
            tokens.clear()
            loggedIn = false
            errorMessage = "Sesja administratora wygasła. Zaloguj się ponownie."
        } else {
            errorMessage = e.message ?: "Wystąpił nieoczekiwany błąd."
        }
    }
}
