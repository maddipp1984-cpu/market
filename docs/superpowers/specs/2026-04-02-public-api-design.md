# Design: Public API fuer Drittsysteme

## Zusammenfassung

Separate REST-API unter `/public-api/` fuer externe Systeme. Basic Auth ueber HTTPS, eigene DTOs (stabil, entkoppelt von interner API), eigene Controller die intern bestehende Services nutzen. Erster Endpoint: `POST /public-api/counterparts` (Geschaeftspartner anlegen).

## Security-Architektur

Zwei getrennte Security-Filter-Chains in Spring Security:

- **Chain 1** (`/public-api/**`): Basic Auth, eigener `UserDetailsService` mit Credentials aus `application.properties`
- **Chain 2** (`/api/**`): Keycloak OAuth2 Resource Server (wie bisher)

Bestehende `SecurityConfig` muss einen `securityMatcher("/api/**")` bekommen, damit sie nicht mehr global greift.

### Credentials

In `application.properties`:

```properties
market.public-api.username=api-user
market.public-api.password=changeme
```

Gelesen ueber `@ConfigurationProperties(prefix = "market.public-api")`.

## Paket-Struktur

```
de.market.publicapi/
    PublicApiSecurityConfig.java        -- @Configuration, SecurityFilterChain fuer /public-api/**
    PublicApiUserDetailsService.java    -- Liest Credentials aus Properties
    PublicApiProperties.java            -- @ConfigurationProperties
    PublicApiExceptionHandler.java      -- @RestControllerAdvice fuer konsistentes Error-Format
    counterpart/
        CounterpartController.java     -- @RestController /public-api/counterparts
        dto/
            CreateCounterpartRequest.java  -- Stabile Request-DTOs
            CounterpartResponse.java       -- Stabile Response-DTOs
```

## Erster Endpoint

```
POST /public-api/counterparts
Content-Type: application/json
Authorization: Basic base64(user:pass)

Request:  { "shortName": "ENE", "name": "Energie AG" }
Response: { "id": 42, "shortName": "ENE", "name": "Energie AG" }
HTTP 201 Created
```

Der Controller mapped `CreateCounterpartRequest` auf `BusinessPartnerDto`, ruft `BusinessPartnerService.create()` auf, und mapped das Ergebnis auf `CounterpartResponse`.

## Fehlerformat

Eigener `@RestControllerAdvice(basePackages = "de.market.publicapi")`:

```json
{ "error": "Bad Request", "message": "Kurzbezeichnung ist ein Pflichtfeld", "status": 400 }
```

Faengt `IllegalArgumentException` (400), `IllegalStateException` (409) und generische Exceptions (500).

## Aenderungen an bestehender SecurityConfig

Die bestehende `SecurityConfig` bekommt `securityMatcher("/api/**")` auf ihrer `SecurityFilterChain`, damit sie nur fuer interne Endpoints gilt und die Public-API-Chain daneben existieren kann.

## Nicht im Scope

- Rate Limiting
- API-Versionierung
- Weitere Endpoints (nur createCounterpart)
- Mehrere API-User
- Swagger/OpenAPI-Dokumentation
