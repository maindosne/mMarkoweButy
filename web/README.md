# mMarkoweButy — źródła WWW

Ten katalog jest kopią/rekonstrukcją warstwy WWW działającej na produkcji.

## Produkcja
- Vercel project: `markowe-buty`
- Domeny: `mmarkowebuty.pl`, `www.mmarkowebuty.pl`
- Backend/API: Supabase Edge Function `markowe-buty`
- Frontend JS: Supabase Edge Function bundle `mmarkowebuty-shop-bundle`
- Auth klientów: `mmarkowebuty-customer-auth`
- Panel admin: `mmarkowebuty-admin-bundle`

## Funkcje wdrożone 2026-09-18
- opcjonalna stara cena (`old_price_cents`) i profesjonalne przekreślenie + rabat na stronie,
- BLIK przez Stripe Checkout Dynamic Payment Methods (PLN; BLIK aktywny na koncie LIVE),
- rejestracja/logowanie klienta przez Supabase Auth,
- profil klienta w `customer_profiles` i powiązanie zamówienia przez `orders.customer_user_id`,
- brak pola kraju produkcji w publicznym API produktu i interfejsie produktu.

## Naprawa kont klientów — 2026-09-18

- Żądania klienta trafiają wyłącznie do `/api/customer-auth` na domenie sklepu. Istniejące przekierowanie Vercel `/api/*` kieruje je do funkcji `markowe-buty`.
- Obsługa Supabase Auth znajduje się w `supabase/functions/markowe-buty/customer-auth.ts`.
- Sesja: ciasteczko `__Host-mm_customer`, HttpOnly, Secure, SameSite=Lax, bez tokenów w localStorage i odpowiedziach JSON.
- Interfejs źródłowy: `customer-auth.js`; funkcja `mmarkowebuty-customer-auth` dostarcza jego wersję JavaScript do istniejącego bundla sklepu.
- Stary endpoint `mmarkowebuty-auth-api` jest wycofany (HTTP 410).
- Migracja `20260918211046` naprawia brakujące prawa odczytu `customer_profiles`, zachowując RLS.
- Rejestracja zachowuje wymagane przez Supabase potwierdzenie adresu e-mail. Nie wyłączono tej ochrony.

Kod w tym katalogu jest kopią wdrożonych funkcji. Commit GitHub nie uruchamia wdrożenia Vercel automatycznie — projekt nie ma skonfigurowanej Git Integration. Bieżąca naprawa korzysta z istniejących tras Vercel i wdrożonych funkcji Supabase.
