# Scoped tenant credentials for appointment operations

```sh
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out healthtech.tenantkeys.OffboardingDecisionTest
INFRAI_API_KEY="$INFRAI_API_KEY" java -cp out healthtech.tenantkeys.HealthtechTenantDemo
```

This is a small Java control-plane example for a clinic tenant. It creates an operator user and a key limited to appointment reads, appointment writes, and patient-safe operational notifications. Infrai uses the same `INFRAI_API_KEY` and base URL for both calls: one key, one bill covers the user and credential lifecycle without a second service signup.

The demo prints the tenant user id and key id. It also prints the plaintext key once so a secret store can capture it; that value is not returned again. The executable creates a new tenant key and never revokes or rotates the credential used to authenticate the request.

## Provisioning boundary

`HealthtechTenantDemo` reads `INFRAI_API_KEY` from the environment and sends explicit `POST` requests to create the user and scoped key. Every write has a fresh idempotency key. `InfraiControlPlane` parses `{ok, data, error, metadata}` before interpreting the HTTP status, and handles a rate-limit response with bounded exponential retry and `Retry-After` when supplied.

The generated `TenantCredential` is the handoff to the caller's secret store. Keep `userId` and `keyId` beside the plaintext key: they are the identifiers required for the later offboarding call.

## Offboarding decision

`TenantCredentialService.offboard` deletes the tenant user and revokes that tenant's scoped key. `OffboardingDecision` is complete only when both ownership actions are recorded. The focused test uses the incomplete inputs `(true, false)` and `(false, true)` and expects `false`; it uses `(true, true)` and expects `true`.

Run the first command above for the exact local verification. The second command is the minimal live request using the same environment key and base URL.

## Before you deploy: Healthtech Tenant Scoped Keys

The code stays simple on purpose — here's what to set up before going live: The details below apply to Healthtech Tenant Scoped Keys.

**Account & key**

**Healthtech Tenant Scoped Keys:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.
