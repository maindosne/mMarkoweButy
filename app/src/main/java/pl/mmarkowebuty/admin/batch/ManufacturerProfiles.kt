package pl.mmarkowebuty.admin.batch

data class ManufacturerProfile(
    val brand: String,
    val manufacturerName: String,
    val manufacturerAddress: String,
    val manufacturerEmail: String,
)

object ManufacturerProfiles {
    val all: List<ManufacturerProfile> = listOf(
        ManufacturerProfile(
            brand = "Cabin",
            manufacturerName = "CA BIN CZ s.r.o.",
            manufacturerAddress = "Andersenova 427/2, 102 00 Praha 10 – Štěrboholy, Czechy",
            manufacturerEmail = "",
        ),
        ManufacturerProfile(
            brand = "ChunSen",
            manufacturerName = "HANG YU s.r.o.",
            manufacturerAddress = "Mattioliho 3272/9, 106 00 Praha 10 – Záběhlice, Czechy",
            manufacturerEmail = "hangyuatcz@gmail.com",
        ),
        ManufacturerProfile(
            brand = "Koka",
            manufacturerName = "KOKA Shoes fashion",
            manufacturerAddress = "",
            manufacturerEmail = "",
        ),
    )

    fun find(brand: String): ManufacturerProfile? =
        all.firstOrNull { it.brand.equals(brand.trim(), ignoreCase = true) }
}
