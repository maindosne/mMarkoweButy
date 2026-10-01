# AGENTS.md — mMarkoweButy

## Project

This repository contains the mMarkoweButy project. It currently contains both:
- `web/` — WWW storefront source/copy and Supabase functions/migrations used by the store.
- `app/` — Android administrator application (`pl.mmarkowebuty.admin`).

The repository default branch is `main`.

## Important production facts

- Production domains documented in `web/README.md`: `mmarkowebuty.pl` and `www.mmarkowebuty.pl`.
- Vercel project documented there: `markowe-buty`.
- The web layer uses Supabase Edge Functions for backend/API and customer authentication.
- `vercel.json` contains rewrites for the storefront, `/api/*`, `sitemap.xml`, `robots.txt`, and `merchant-feed.xml`.
- The repository documentation explicitly says the Vercel project does not currently have Git Integration configured; do NOT assume that a GitHub push deploys the web production automatically.

## Web structure

- `web/public/index.html` — storefront HTML.
- `web/public/styles.css` — main storefront styling.
- `web/public/swipe.css` / `web/public/swipe.js` — swipe-first storefront.
- `web/public/pro-shop.css` / `web/public/pro-shop.js` — professional storefront layer.
- `web/customer-auth.js` — customer authentication client.
- `web/supabase/functions/` — Supabase Edge Functions.
- `web/supabase/migrations/` — database migrations.
- `web/README.md` — current web deployment/auth notes.
- `vercel.json` — Vercel routing and headers.

## Android structure

- Root project name: `mMarkoweButyAdmin`.
- Module: `app`.
- Namespace/application id: `pl.mmarkowebuty.admin`.
- Kotlin + Jetpack Compose.
- Current app version in `app/build.gradle.kts`: `1.5.1` / versionCode `9`.
- Java compatibility: 17.
- Compile/target SDK: 36.
- Android build workflow: `.github/workflows/build-apk.yml`.
- CI builds `:app:assembleDebug` and publishes artifact `mMarkoweButy-Admin-debug`.

## Agent rules

1. Inspect before editing.
2. Keep changes narrowly scoped to the requested task.
3. Never invent files, APIs, environment variables, production configuration, or deployment behavior.
4. Before using a command, verify it is compatible with the actual repository.
5. Never expose or commit secrets. Do not print secret values from environment files.
6. Never put Supabase `service_role`, Stripe secret keys, webhook secrets, passwords, or similar credentials into source control or APK code.
7. Treat production database, payments, orders, authentication, and deployments as sensitive.
8. Do not run destructive production database commands or delete real orders/products without explicit confirmation.
9. Do not assume Vercel deploys from GitHub automatically; check `web/README.md` and current Vercel configuration first.
10. After code changes, run the smallest relevant validation available and report the exact command/result.
11. For web changes, check the affected HTML/JS/CSS and relevant Supabase function/migration before editing.
12. For Android changes, use the existing Gradle/Android setup and preserve the existing security model.
13. Review the Git diff after edits and keep unrelated files untouched.

## Git

- Work on `main` only when explicitly requested.
- Prefer a separate branch for substantial changes.
- Never force-push unless explicitly requested.
- Do not rewrite existing commits.
- Before claiming a task is complete, check `git status` and the final diff.

## Validation

Android CI currently uses Java 17, Android platform 36/build-tools 36.0.0 and Gradle 9.6.0. The known CI build command is:

`gradle :app:assembleDebug --stacktrace --no-daemon`

For web changes, first determine the actual available local scripts/tooling from the repository. Do not invent `npm`, `pnpm`, or framework commands when the corresponding files are absent.

## Unknowns

If a required fact is not present in the repository, mark it as `TODO` and ask for the missing value instead of guessing.
