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

Uwaga: Vercel nadal nie ma Git Integration (`link: null`). Ten katalog zabezpiecza wejściową warstwę WWW w GitHub; kod wykonywalny backendu pozostaje obecnie w Supabase Edge Functions.
