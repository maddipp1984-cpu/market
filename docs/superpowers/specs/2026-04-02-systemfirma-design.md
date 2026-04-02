# Design: Systemfirma-Konzept (System Company)

## Zusammenfassung

In der Energiewirtschaft muss eine fuehrende Systemfirma definiert werden, die das System betreibt. Diese wird spaeter als Referenz fuer die Richtung von Energiefluessen und Entgelten (Kauf/Verkauf) genutzt. Zusaetzlich koennen Tochterfirmen als weitere Systemfirmen mit Ranking hinterlegt werden.

Das Feature ist rein backend-seitig (Cache + DB-Spalte) mit visueller Markierung in der GP-Uebersicht. Keine GUI-Pflege — Konfiguration erfolgt per SQL/Migration.

## Datenmodell

Neue nullable Spalte `system_rank` in `business_partner`:

```sql
ALTER TABLE business_partner ADD COLUMN system_rank SMALLINT UNIQUE;
```

- `NULL` — normaler Handelspartner (Grossteil der Eintraege)
- `1` — fuehrende Systemfirma (immer genau eine)
- `2`, `3`, ... — Tochterfirmen (aufsteigend, lueckenlos, eindeutig)

UNIQUE-Constraint auf DB-Ebene sichert Eindeutigkeit. Lueckenlosigkeit und Existenz von Rang 1 werden nicht per DB-Constraint erzwungen (nur per Konvention bei der manuellen Pflege).

Pflege ausschliesslich per SQL/Migration, nicht ueber die API.

## Backend

### SystemCompanyEntry (Record)

```java
public record SystemCompanyEntry(Long partnerId, String shortName, String name, int rank) {}
```

Leichtgewichtiges Value-Object fuer den Cache. Kein JPA-Entity, kein DTO.

### SystemCompanyService

```java
@Service
public class SystemCompanyService {
    private volatile List<SystemCompanyEntry> cache = List.of();
    
    @PostConstruct
    void loadOnStartup() { reload(); }
    
    public Optional<SystemCompanyEntry> getPrimary()  // rank == 1
    public List<SystemCompanyEntry> getAll()           // sortiert nach rank
    public boolean isSystemCompany(Long partnerId)     // Lookup
}
```

- Laedt beim Anwendungsstart per `@PostConstruct` aus der DB
- Query: `SELECT id, short_name, name, system_rank FROM business_partner WHERE system_rank IS NOT NULL ORDER BY system_rank`
- Ausgefuehrt via jOOQ (`DSLContext`), passend zum bestehenden Pattern
- Cache ist `volatile List` — wird einmal beim Start gesetzt, danach nur gelesen
- Kein Reload zur Laufzeit (Konfiguration aendert sich nicht im Betrieb)
- Wird von anderen Services per DI genutzt (spaeter fuer Kauf/Verkauf-Richtung)

### Keine REST-API-Aenderungen

Keine neuen Endpoints. Kein Schreib-Zugriff auf `system_rank` ueber die API.

## Frontend

### GP-Uebersicht: Visuelle Markierung

`BusinessPartnerOverviewRepository` liefert `system_rank` als zusaetzliche Spalte. Im Frontend:

- `system_rank IS NOT NULL` → Chip/Badge in der Uebersichtstabelle
- Rang 1: "Systemfirma" (oder "Fuehrend")
- Rang 2+: "Tochter (2)", "Tochter (3)" etc.
- `system_rank IS NULL` → keine Anzeige (leere Zelle)

Die Spalte wird als read-only ColumnOverride konfiguriert und kann nicht gefiltert werden (rein visuell).

### Keine Aenderung an der Detailmaske

Die GP-Detailmaske bleibt unveraendert. Der Rang ist nicht editierbar.

## Nicht im Scope

- GUI zum Pflegen des Systemfirma-Rangs
- REST-Endpoints fuer system_rank
- Kauf/Verkauf-Logik basierend auf Systemfirma (kommt spaeter, nutzt dann `SystemCompanyService`)
- Validierung der Lueckenlosigkeit (Konvention bei manueller DB-Pflege)
