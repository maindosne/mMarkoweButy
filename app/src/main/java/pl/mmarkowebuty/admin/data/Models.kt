package pl.mmarkowebuty.admin.data

import org.json.JSONArray
import org.json.JSONObject

data class Product(
    val id: Long,
    val brand: String,
    val name: String,
    val description: String,
    val sizes: List<String>,
    val price: Double,
    val imageUrl: String?,
    val imageUrls: List<String>,
    val published: Boolean,
    val sold: Boolean,
    val productIdentifier: String,
    val manufacturerName: String,
    val manufacturerAddress: String,
    val manufacturerEmail: String,
    val responsiblePersonName: String,
    val responsiblePersonAddress: String,
    val responsiblePersonEmail: String,
    val safetyInfo: String,
    val materialUpper: String,
    val materialLining: String,
    val materialSole: String,
    val oldPrice: Double?,
)

data class ProductDraft(
    val brand: String = "",
    val name: String = "",
    val description: String = "",
    val size: String = "",
    val price: String = "",
    val imageUrls: List<String> = emptyList(),
    val published: Boolean = true,
    val productIdentifier: String = "",
    val manufacturerName: String = "",
    val manufacturerAddress: String = "",
    val manufacturerEmail: String = "",
    val responsiblePersonName: String = "",
    val responsiblePersonAddress: String = "",
    val responsiblePersonEmail: String = "",
    val safetyInfo: String = "",
    val materialUpper: String = "",
    val materialLining: String = "",
    val materialSole: String = "",
    val oldPrice: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("brand", brand.trim())
        put("name", name.trim())
        put("description", description.trim())
        put("sizes", JSONArray().put(size.trim()))
        put("price", price.replace(',', '.').toDoubleOrNull() ?: 0.0)
        put("oldPrice", oldPrice.replace(',', '.').toDoubleOrNull())
        put("imageUrls", JSONArray(imageUrls))
        put("published", published)
        put("sold", false)
        put("productIdentifier", productIdentifier.trim())
        put("manufacturerName", manufacturerName.trim())
        put("manufacturerAddress", manufacturerAddress.trim())
        put("manufacturerEmail", manufacturerEmail.trim())
        put("responsiblePersonName", responsiblePersonName.trim())
        put("responsiblePersonAddress", responsiblePersonAddress.trim())
        put("responsiblePersonEmail", responsiblePersonEmail.trim())
        put("safetyInfo", safetyInfo.trim())
        put("materialUpper", materialUpper.trim())
        put("materialLining", materialLining.trim())
        put("materialSole", materialSole.trim())
    }
}

data class OrderItem(
    val productName: String,
    val sizeLabel: String?,
    val unitPriceCents: Int,
    val quantity: Int,
)

data class Order(
    val id: String,
    val amountCents: Int,
    val status: String,
    val fulfillmentStatus: String,
    val customerEmail: String?,
    val customerPhone: String?,
    val customerName: String?,
    val createdAt: String,
    val shippingMethod: String?,
    val shippingAmountCents: Int,
    val inpostPointId: String?,
    val inpostPointName: String?,
    val inpostPointAddress: String?,
    val inpostPointPostalCode: String?,
    val inpostPointCity: String?,
    val carrier: String?,
    val trackingNumber: String?,
    val items: List<OrderItem>,
)

data class StoreSettings(
    val storeName: String = "mMarkoweButy",
    val legalName: String = "",
    val nip: String = "",
    val regon: String = "",
    val addressLine: String = "",
    val postalCode: String = "",
    val city: String = "",
    val countryCode: String = "PL",
    val customerEmail: String = "",
    val customerPhone: String = "",
    val websiteUrl: String = "",
    val returnsAddress: String = "",
    val shippingMethod: String = "InPost Paczkomat",
    val shippingPrice: Double = 0.0,
    val freeShippingFrom: Double = 0.0,
)

data class BackendSystemStatus(
    val stripePresent: Boolean = false,
    val stripeMode: String = "unknown",
    val inpostEnabled: Boolean = false,
    val inpostTokenPresent: Boolean = false,
)

data class InPostSettings(
    val token: String = "",
    val enabled: Boolean = false,
    val tokenValid: Boolean = false,
    val tokenReason: String? = null,
)

data class CustomerSummary(
    val key: String,
    val name: String,
    val email: String,
    val phone: String,
    val orders: Int,
    val totalCents: Int,
    val lastOrder: String,
)

internal fun JSONObject.stringOrEmpty(name: String): String = optString(name, "").takeUnless { it == "null" } ?: ""
internal fun JSONObject.nullableString(name: String): String? = optString(name, "").takeUnless { it.isBlank() || it == "null" }

fun parseProducts(root: JSONObject): List<Product> {
    val array = root.optJSONArray("products") ?: JSONArray()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val sizesJson = o.optJSONArray("sizes") ?: JSONArray()
            val imagesJson = o.optJSONArray("imageUrls") ?: JSONArray()
            val sizes = buildList { for (x in 0 until sizesJson.length()) add(sizesJson.optString(x)) }
            val images = buildList { for (x in 0 until imagesJson.length()) imagesJson.optString(x).takeIf { it.isNotBlank() }?.let(::add) }
            add(
                Product(
                    id = o.optLong("id"),
                    brand = o.stringOrEmpty("brand"),
                    name = o.stringOrEmpty("name"),
                    description = o.stringOrEmpty("description"),
                    sizes = sizes,
                    price = o.optDouble("price", 0.0),
                    imageUrl = o.nullableString("imageUrl"),
                    imageUrls = images,
                    published = o.optBoolean("published", true),
                    sold = o.optBoolean("sold", false),
                    productIdentifier = o.stringOrEmpty("productIdentifier"),
                    manufacturerName = o.stringOrEmpty("manufacturerName"),
                    manufacturerAddress = o.stringOrEmpty("manufacturerAddress"),
                    manufacturerEmail = o.stringOrEmpty("manufacturerEmail"),
                    responsiblePersonName = o.stringOrEmpty("responsiblePersonName"),
                    responsiblePersonAddress = o.stringOrEmpty("responsiblePersonAddress"),
                    responsiblePersonEmail = o.stringOrEmpty("responsiblePersonEmail"),
                    safetyInfo = o.stringOrEmpty("safetyInfo"),
                    materialUpper = o.stringOrEmpty("materialUpper"),
                    materialLining = o.stringOrEmpty("materialLining"),
                    materialSole = o.stringOrEmpty("materialSole"),
                    oldPrice = o.optDouble("oldPrice").takeIf { o.has("oldPrice") && !o.isNull("oldPrice") },
                )
            )
        }
    }
}

fun parseOrders(root: JSONObject): List<Order> {
    val array = root.optJSONArray("orders") ?: JSONArray()
    return buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val itemsJson = o.optJSONArray("order_items") ?: JSONArray()
            val items = buildList {
                for (x in 0 until itemsJson.length()) {
                    val it = itemsJson.optJSONObject(x) ?: continue
                    add(OrderItem(
                        productName = it.stringOrEmpty("product_name"),
                        sizeLabel = it.nullableString("size_label"),
                        unitPriceCents = it.optInt("unit_price_cents", 0),
                        quantity = it.optInt("quantity", 1),
                    ))
                }
            }
            add(
                Order(
                    id = o.stringOrEmpty("id"),
                    amountCents = o.optInt("amount_cents", 0),
                    status = o.stringOrEmpty("status"),
                    fulfillmentStatus = o.stringOrEmpty("fulfillment_status").ifBlank { "new" },
                    customerEmail = o.nullableString("customer_email"),
                    customerPhone = o.nullableString("customer_phone"),
                    customerName = o.nullableString("customer_name"),
                    createdAt = o.stringOrEmpty("created_at"),
                    shippingMethod = o.nullableString("shipping_method"),
                    shippingAmountCents = o.optInt("shipping_amount_cents", 0),
                    inpostPointId = o.nullableString("inpost_point_id"),
                    inpostPointName = o.nullableString("inpost_point_name"),
                    inpostPointAddress = o.nullableString("inpost_point_address"),
                    inpostPointPostalCode = o.nullableString("inpost_point_postal_code"),
                    inpostPointCity = o.nullableString("inpost_point_city"),
                    carrier = o.nullableString("carrier"),
                    trackingNumber = o.nullableString("tracking_number"),
                    items = items,
                )
            )
        }
    }
}

fun parseSettings(root: JSONObject): Pair<StoreSettings, BackendSystemStatus> {
    val s = root.optJSONObject("settings") ?: JSONObject()
    val sys = root.optJSONObject("system") ?: JSONObject()
    return StoreSettings(
        storeName = s.stringOrEmpty("storeName").ifBlank { "mMarkoweButy" },
        legalName = s.stringOrEmpty("legalName"),
        nip = s.stringOrEmpty("nip"),
        regon = s.stringOrEmpty("regon"),
        addressLine = s.stringOrEmpty("addressLine"),
        postalCode = s.stringOrEmpty("postalCode"),
        city = s.stringOrEmpty("city"),
        countryCode = s.stringOrEmpty("countryCode").ifBlank { "PL" },
        customerEmail = s.stringOrEmpty("customerEmail"),
        customerPhone = s.stringOrEmpty("customerPhone"),
        websiteUrl = s.stringOrEmpty("websiteUrl"),
        returnsAddress = s.stringOrEmpty("returnsAddress"),
        shippingMethod = s.stringOrEmpty("shippingMethod").ifBlank { "InPost Paczkomat" },
        shippingPrice = s.optDouble("shippingPrice", 0.0),
        freeShippingFrom = s.optDouble("freeShippingFrom", 0.0),
    ) to BackendSystemStatus(
        stripePresent = sys.optBoolean("stripePresent", false),
        stripeMode = sys.stringOrEmpty("stripeMode").ifBlank { "unknown" },
        inpostEnabled = sys.optBoolean("inpostEnabled", false),
        inpostTokenPresent = sys.optBoolean("inpostTokenPresent", false),
    )
}

fun parseInPostSettings(root: JSONObject): InPostSettings = InPostSettings(
    token = root.stringOrEmpty("geowidgetToken"),
    enabled = root.optBoolean("enabled", false),
    tokenValid = root.optBoolean("tokenValid", false),
    tokenReason = root.nullableString("tokenReason"),
)
