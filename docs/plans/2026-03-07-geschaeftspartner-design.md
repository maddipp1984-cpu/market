# Design: Geschaeftspartner (erster Detail-UseCase)

## Uebersicht
Geschaeftspartner als erste "echte" Detail-Entitaet mit JPA. Dient als Vorlage fuer alle weiteren Detail-UseCases.
Ansprechpartner sind eine eigene Entitaet mit 1:n-Beziehung, werden aber ueber den GP gespeichert (Cascade).

## Architektur-Entscheidung: JPA vs Raw JDBC
- **JPA** fuer alle Detail-Entitaeten (CRUD, Beziehungen, Geschaeftslogik)
- **Raw JDBC** nur fuer Uebersichtsseiten (leichtgewichtige Abfragen) und performance-kritische Zugriffe (Zeitreihen)
- Beide koexistieren im selben Spring Boot Projekt

## Datenmodell

### business_partner
| Spalte | Typ | Constraint |
|--------|-----|-----------|
| id | BIGSERIAL | PK |
| short_name | VARCHAR(50) | NOT NULL, UNIQUE |
| name | VARCHAR(255) | NOT NULL |
| notes | TEXT | nullable |

### contact_person
| Spalte | Typ | Constraint |
|--------|-----|-----------|
| id | BIGSERIAL | PK |
| partner_id | BIGINT | FK -> business_partner, NOT NULL |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | nullable |
| phone | VARCHAR(50) | nullable |
| street | VARCHAR(255) | nullable |
| zip_code | VARCHAR(10) | nullable |
| city | VARCHAR(100) | nullable |

### contact_person_function (Join-Tabelle fuer Mehrfachauswahl)
| Spalte | Typ | Constraint |
|--------|-----|-----------|
| contact_person_id | BIGINT | FK -> contact_person |
| function_type | VARCHAR(30) | NOT NULL |
| | | PK = (contact_person_id, function_type) |

### Enum: ContactFunction
- `ABRECHNUNG` — Abrechnung
- `BK_VERANTWORTLICHER` — BK-Verantwortlicher

Erweiterbar durch Hinzufuegen neuer Enum-Werte.

## Backend

### Package-Struktur
```
de.projekt.businesspartner/
    model/
        BusinessPartner.java          -- @Entity
        ContactPerson.java            -- @Entity
        ContactFunction.java          -- Enum
    repository/
        BusinessPartnerRepository.java -- JpaRepository<BusinessPartner, Long>
    service/
        BusinessPartnerService.java   -- @Service, @Transactional, Validierung, Mapping
    rest/
        BusinessPartnerController.java -- @RestController (duenn, delegiert an Service)
        dto/
            BusinessPartnerDto.java
            ContactPersonDto.java
```

### JPA-Beziehungen
- `BusinessPartner` -> `@OneToMany(cascade = ALL, orphanRemoval = true)` -> `List<ContactPerson>`
- `ContactPerson` -> `@ElementCollection` -> `Set<ContactFunction>`
- Ein einziges Repository: `BusinessPartnerRepository`
- Service uebernimmt: Validierung (unique shortName), DTO<->Entity Mapping, Transaktionen

### REST-Endpoints
| Methode | Pfad | Beschreibung |
|---------|------|-------------|
| GET | `/api/business-partners` | Uebersicht (id, shortName, name) |
| GET | `/api/business-partners/{id}` | Detail inkl. Ansprechpartner |
| POST | `/api/business-partners` | Neu anlegen (GP + Ansprechpartner) |
| PUT | `/api/business-partners/{id}` | Aktualisieren (GP + Ansprechpartner) |
| DELETE | `/api/business-partners/{id}` | Loeschen (kaskadiert) |

### Service-Layer
- Validierung: Kurzbezeichnung eindeutig, Pflichtfelder
- DTO <-> Entity Mapping
- `@Transactional` am Service
- Controller ist duenn: Request rein -> Service -> Response raus

## Frontend

### Uebersichtsseite (BusinessPartnerPage)
- `<OverviewPage>` + `<VirtualTable>`
- Spalten: Kurzbezeichnung, Name
- Doppelklick -> Detail-Tab oeffnen (mode: edit, entityId)
- Neu-Button -> Detail-Tab oeffnen (mode: new)

### Detailseite (BusinessPartnerDetailPage)
- `<DetailPage>` Template (Toolbar, Modi, Validierung, Dirty-Guard)
- **Oberer Bereich**: Card mit Feldern
  - Name (Input, Pflicht)
  - Kurzbezeichnung (Input, Pflicht)
  - Notizen (Textarea, optional)
- **Unterer Bereich**: Ansprechpartner als aufklappbare Cards
  - Card-Header: "Vorname Nachname" + Funktionen als Chips
  - Card-Body (aufgeklappt): Vorname, Nachname, E-Mail, Telefon, Strasse, PLZ, Ort, Funktionen (Checkboxen)
  - Button "Ansprechpartner hinzufuegen" -> neue leere Card
  - Jede Card hat Entfernen-Button
- Ein Speichern sichert GP + alle Ansprechpartner zusammen
- Dirty-Flag umfasst GP-Felder UND Ansprechpartner-Aenderungen

### Sidebar
- Neuer Eintrag "Geschaeftspartner" unter Ordner "Stammdaten"

## Abgrenzung (kommt spaeter)
- GP-Typen/Rollen (ueber Rollensystem)
- Steuernummer (ueber Abrechnungskonfiguration)
- Verknuepfung GP <-> Zeitreihen/Objekte
