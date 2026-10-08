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

Flyway is configured to load migrations from
`src/main/resources/db/migration`. No application schema or migration is created
as part of this foundation.
