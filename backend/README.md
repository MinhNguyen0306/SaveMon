# SaveMon Backend

The backend is a Java 21 / Spring Boot modular monolith. Business modules follow the
`identity`, `personalfinance`, `sharedfinance`, `ai`, and `shared` boundaries in
`../docs/ARCHITECTURE.md`. Identity, Personal Finance, and Shared Finance have
`domain`, `application`, `infrastructure`, and `interfaces` layers. AI has
`application`, `infrastructure`, and `interfaces` layers. The shared module is for
cross-cutting technical concerns only; do not place business logic there.

## Build and run

```powershell
mvn test
mvn spring-boot:run
```

The `dev` profile is the default. Set `SPRING_PROFILES_ACTIVE` to `test` or `prod`
to activate the corresponding profile. PostgreSQL connection settings can be
provided with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; the default URL points
to a local `savemon` database. The health endpoint is `/actuator/health`.

## Security configuration

JWT validation requires `SAVEMON_JWT_PUBLIC_KEY` containing the active RSA
X.509 public key in PEM format and `SAVEMON_JWT_KEY_ID` containing its `kid`.
The backend accepts RS256 access tokens with a UUID `sub`, a required expiration
no more than 900 seconds in the future, and the configured `kid`. Missing or
invalid key configuration prevents the application from starting. Provide key
material through the deployment's secret/configuration system; do not commit it.

This service only validates access tokens. The corresponding private signing
key belongs to a separate token issuer and must be provided to that issuer
through its environment/secret configuration; it is not needed or loaded by
this resource server. Token issuance is not implemented here.

Flyway is configured to load migrations from
`src/main/resources/db/migration`. No application schema or migration is created
as part of this foundation.
