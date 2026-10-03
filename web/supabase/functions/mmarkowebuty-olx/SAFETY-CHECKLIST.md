# OLX production safety checklist

No real advert may be published until every applicable item is confirmed.

## Access
- [ ] OLX application accepted
- [ ] Client ID configured as backend secret
- [ ] Client Secret configured as backend secret
- [ ] Redirect URI exactly matches approved OLX configuration
- [ ] Authorization-code OAuth completed for the correct seller account
- [ ] Refresh-token rotation tested and persisted safely

## API contract
- [ ] Current OLX Partner API 2.0 specification rechecked
- [ ] Version: 2.0 header sent
- [ ] Category fetched from API
- [ ] Required category attributes fetched and validated
- [ ] City/location identifiers validated
- [ ] Delivery eligibility/methods fetched where applicable
- [ ] Product Safety Regulation fields reviewed for the actual product

## Listing
- [ ] Correct store product and variant
- [ ] Stable unique external_id
- [ ] Title reviewed
- [ ] Description reviewed
- [ ] Price and PLN currency reviewed
- [ ] Condition/size/brand/category attributes reviewed
- [ ] Image URLs reachable by OLX and correspond to this product
- [ ] Stock > 0 immediately before publish
- [ ] No existing OLX advert mapped to this external_id

## Money / package
- [ ] No automatic packet purchase during tests
- [ ] No automatic paid-feature purchase
- [ ] No automatic extension unless explicitly chosen
- [ ] Response status checked before any activation/payment step

## Production rollout
- [ ] Dry-run output reviewed
- [ ] First listing published manually through guarded API action
- [ ] Result fetched back from OLX and compared with intended listing
- [ ] OLX advert id/url/status saved
- [ ] Deactivation path tested safely
- [ ] Duplicate/retry protection tested
- [ ] Errors do not expose tokens/secrets
- [ ] Only after first listing is verified: controlled batch publishing
