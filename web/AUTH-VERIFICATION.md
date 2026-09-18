# Weryfikacja kont klientów — 2026-09-18

## Przyczyny problemu

1. Frontend wysyłał żądania bezpośrednio do Supabase, a produkcyjny nagłówek CSP zezwalał wyłącznie na `connect-src 'self'`.
2. `customer_profiles` miała RLS i polityki, lecz brakowało uprawnień SELECT, również dla service_role.
3. Stare wylogowanie używało klienta Auth bez ustawionej sesji; odświeżanie sesji było ręczne i wyłącznie przy starcie strony.

## Testy wykonane

- Produkcyjne `/api/customer-auth`: HTTP 200, prywatne odpowiedzi no-store, niezmieniona restrykcyjna CSP.
- Rejestracja przez publiczny endpoint utworzyła użytkownika Auth i powiązany profil.
- Do testu API potwierdzono administracyjnie wyłącznie jednorazowe konto testowe. Dostarczenia wiadomości i docelowego przekierowania e-mail jeszcze nie sprawdzono.
- Logowanie prawidłowym hasłem: użytkownik, profil i ciasteczko sesji.
- Ciasteczko przekazywane przez Vercel: HttpOnly i Secure; tokeny nie są zwracane w JSON.
- Ponowne żądanie z ciasteczkiem przywraca sesję.
- Wymuszenie ścieżki odnowienia sesji odświeża token i ciasteczko.
- Wylogowanie usuwa ciasteczko; następne żądanie nie jest zalogowane.
- Ponowne logowanie działa; nieprawidłowe hasło daje polski komunikat.
- Konto testowe, jego profil i sesje usunięto. Tymczasowy chroniony endpoint weryfikacji wyłączono (HTTP 410 i wymóg JWT).
- W przeglądarce sprawdzono otwieranie nowego dialogu, przełączanie zakładek, pusty formularz, polską walidację i wygląd desktopowy.
- Sprawdzono składnię JavaScript i TypeScript.

## Pozostałe testy

- Pełna rejestracja i cykl logowania przez formularz w przeglądarce wymagają bezpiecznego wprowadzenia danych testowych przez browserAuth.
- Weryfikacja dostarczenia e-mail i URL po kliknięciu linku.
- Wizualne sprawdzenie na rzeczywistym wąskim ekranie; CSS ma osobny układ dla szerokości do 660 px.

Nie należy uznawać powyższych testów API za pełny test formularza w przeglądarce.
