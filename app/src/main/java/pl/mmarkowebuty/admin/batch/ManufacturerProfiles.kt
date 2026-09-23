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
        ManufacturerProfile(
            brand = "Camo",
            manufacturerName = "LECAMO s.r.o.",
            manufacturerAddress = "Hrušovská 323/19, 102 00 Praha 10 – Štěrboholy, Czechy",
            manufacturerEmail = "",
        ),
        ManufacturerProfile(
            brand = "SuperIn",
            manufacturerName = "Lam Ng Chung",
            manufacturerAddress = "Symfonická 1426/5, 150 00 Praha 5 – Stodůlky, Czechy",
            manufacturerEmail = "",
        ),
        ManufacturerProfile(
            brand = "Bellamila",
            manufacturerName = "Roman Sucholas – Firma Handlowa",
            manufacturerAddress = "ul. Obywatelska 11/2, 65-736 Zielona Góra, Polska",
            manufacturerEmail = "",
        ),
        ManufacturerProfile(brand = "Minke", manufacturerName = "ZION s.r.o.", manufacturerAddress = "V olšinách 3383/126a, 100 00 Praha 10 – Strašnice, Czechy", manufacturerEmail = ""),
        ManufacturerProfile(brand = "Sports", manufacturerName = "", manufacturerAddress = "", manufacturerEmail = ""),
    )

    fun find(brand: String): ManufacturerProfile? =
        all.firstOrNull { it.brand.equals(brand.trim(), ignoreCase = true) }

    fun findInFolderName(folderName: String): ManufacturerProfile? {
        return all.firstOrNull { profile ->
            Regex(
                pattern = "(^|[^\\p{L}\\p{N}])${Regex.escape(profile.brand)}([^\\p{L}\\p{N}]|$)",
                option = RegexOption.IGNORE_CASE,
            ).containsMatchIn(folderName)
        }
    }
}
