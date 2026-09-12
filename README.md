# mMarkoweButy Admin — Android

Natywna aplikacja administratora sklepu mMarkoweButy na Androida.

## Założenia

- Kotlin + Jetpack Compose
- ten sam backend i ta sama baza danych co mMarkoweButy.pl
- brak klucza `service_role`, haseł Stripe i innych sekretów w APK
- logowanie tym samym hasłem administratora co panel WWW
- token sesji szyfrowany przez Android Keystore
- produkty, zdjęcia, zamówienia, klienci, InPost, ustawienia, raporty i stan sklepu
- brak funkcji usuwania prawdziwych zamówień
- sprzedane produkty są chronione przed edycją/usunięciem w aplikacji

## Synchronizacja

Aplikacja komunikuje się z produkcyjnym mobilnym gatewayem mMarkoweButy i nie ma osobnej bazy danych. Zapis w aplikacji trafia do tego samego backendu, z którego korzysta sklep internetowy.

## APK

GitHub Actions buduje wariant debug APK bez płatnego buildera. Artefakt po udanym buildzie nazywa się `mMarkoweButy-Admin-debug`.
