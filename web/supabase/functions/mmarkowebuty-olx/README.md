# mMarkoweButy — OLX integration

Production-oriented backend module for OLX Partner API 2.0.

## Safety model
- Server-side only. Never expose OLX client secret, access token or refresh token to browser/app code.
- Default mode before OLX approval: `dry-run`.
- AI may prepare listing data, but publishing is a separate explicit operation.
- Validate category, required attributes, price, images, product safety data and delivery settings against current OLX API metadata before publish.
- Use a stable `external_id` mapped to the store product/variant to reduce duplicate listings.
- Log request outcome and OLX request/error identifiers, but never log secrets/tokens.
- Do not automatically purchase packets or paid features.
- Do not automatically enable auto-extension.
- Publishing/deactivation/deletion must be idempotent where possible and guarded against retries.

## Planned flow
1. OAuth authorization-code connection to the seller OLX account.
2. Store/rotate access + refresh tokens server-side.
3. Synchronize OLX categories/attributes/delivery metadata.
4. Build draft from store product.
5. Validate draft.
6. Human approval.
7. Publish once.
8. Save OLX advert id, URL, status and timestamps.
9. Reconcile status with OLX.
10. Handle edit/deactivate/delete explicitly.

## Required secrets (NOT committed)
- OLX_CLIENT_ID
- OLX_CLIENT_SECRET
- OLX_REDIRECT_URI

Tokens obtained from OAuth must also remain server-side.

## Current state
Scaffold only. No production OLX request is enabled until credentials are approved, OAuth is connected, database migration is reviewed/applied and end-to-end checks pass.
