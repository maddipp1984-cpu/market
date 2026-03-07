# Geschaeftspartner Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Geschaeftspartner als erste JPA-basierte Detail-Entitaet mit Uebersichtsseite, Detailseite und Ansprechpartner-Verwaltung.

**Architecture:** JPA fuer Detail-Entitaeten (BusinessPartner + ContactPerson mit Cascade), Raw JDBC fuer Uebersicht (TableResponse). Service-Layer fuer Validierung/Mapping, duenner Controller. Frontend: OverviewPage + DetailPage mit aufklappbaren Ansprechpartner-Cards.

**Tech Stack:** Spring Boot 3.4.1, JPA/Hibernate, PostgreSQL, React 18, TypeScript, Vite

---

### Task 1: JPA-Dependency einrichten

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`

**Step 1: JPA-Dependency zu build.gradle hinzufuegen**

In `build.gradle` unter `dependencies` ergaenzen:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

**Step 2: JPA-Config in application.properties**

Ans Ende von `application.properties` anfuegen:

```properties
# JPA/Hibernate (neben Raw JDBC)
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_schema=public
```

`validate` stellt sicher, dass Hibernate die Tabellen nicht selbst anlegt (wir machen Migrationen manuell), aber prueft ob die Entities zu den Tabellen passen. `open-in-view=false` vermeidet Lazy-Loading-Probleme ausserhalb von Transaktionen.

**Step 3: Build pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (keine Tests brechen)

**Step 4: Commit**

```bash
git add build.gradle src/main/resources/application.properties
git commit -m "feat: JPA/Hibernate Dependency einrichten"
```

---

### Task 2: DB-Migration (Tabellen anlegen)

**Files:**
- Create: `sql/migrations/006_business_partner.sql`

**Step 1: Migration-SQL schreiben**

```sql
-- Geschaeftspartner
CREATE TABLE business_partner (
    id          BIGSERIAL PRIMARY KEY,
    short_name  VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    notes       TEXT
);

-- Ansprechpartner
CREATE TABLE contact_person (
    id          BIGSERIAL PRIMARY KEY,
    partner_id  BIGINT       NOT NULL REFERENCES business_partner(id) ON DELETE CASCADE,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(50),
    street      VARCHAR(255),
    zip_code    VARCHAR(10),
    city        VARCHAR(100)
);

CREATE INDEX idx_contact_person_partner ON contact_person(partner_id);

-- Funktionen (Mehrfachauswahl pro Ansprechpartner)
CREATE TABLE contact_person_function (
    contact_person_id  BIGINT      NOT NULL REFERENCES contact_person(id) ON DELETE CASCADE,
    function_type      VARCHAR(30) NOT NULL,
    PRIMARY KEY (contact_person_id, function_type)
);
```

**Step 2: Migration ausfuehren**

Run: `cat sql/migrations/006_business_partner.sql | docker exec -i timeseries-db psql -U postgres -d timeseries`
Expected: CREATE TABLE (3x), CREATE INDEX

**Step 3: Commit**

```bash
git add sql/migrations/006_business_partner.sql
git commit -m "feat: DB-Migration Geschaeftspartner-Tabellen"
```

---

### Task 3: JPA Entities + Enum

**Files:**
- Create: `src/main/java/de/projekt/businesspartner/model/ContactFunction.java`
- Create: `src/main/java/de/projekt/businesspartner/model/ContactPerson.java`
- Create: `src/main/java/de/projekt/businesspartner/model/BusinessPartner.java`

**Step 1: ContactFunction Enum**

```java
package de.projekt.businesspartner.model;

public enum ContactFunction {
    ABRECHNUNG("Abrechnung"),
    BK_VERANTWORTLICHER("BK-Verantwortlicher");

    private final String label;

    ContactFunction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

**Step 2: ContactPerson Entity**

```java
package de.projekt.businesspartner.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contact_person")
public class ContactPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String street;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(length = 100)
    private String city;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "contact_person_function",
        joinColumns = @JoinColumn(name = "contact_person_id")
    )
    @Column(name = "function_type")
    @Enumerated(EnumType.STRING)
    private Set<ContactFunction> functions = new HashSet<>();

    // Getters + Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Set<ContactFunction> getFunctions() { return functions; }
    public void setFunctions(Set<ContactFunction> functions) { this.functions = functions; }
}
```

**Step 3: BusinessPartner Entity**

```java
package de.projekt.businesspartner.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "business_partner")
public class BusinessPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_name", nullable = false, unique = true, length = 50)
    private String shortName;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private List<ContactPerson> contacts = new ArrayList<>();

    // Getters + Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<ContactPerson> getContacts() { return contacts; }
    public void setContacts(List<ContactPerson> contacts) { this.contacts = contacts; }
}
```

**Step 4: Build pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/de/projekt/businesspartner/model/
git commit -m "feat: JPA Entities BusinessPartner, ContactPerson, ContactFunction"
```

---

### Task 4: Repository + Service + DTOs

**Files:**
- Create: `src/main/java/de/projekt/businesspartner/repository/BusinessPartnerRepository.java`
- Create: `src/main/java/de/projekt/businesspartner/rest/dto/ContactPersonDto.java`
- Create: `src/main/java/de/projekt/businesspartner/rest/dto/BusinessPartnerDto.java`
- Create: `src/main/java/de/projekt/businesspartner/service/BusinessPartnerService.java`

**Step 1: Repository**

```java
package de.projekt.businesspartner.repository;

import de.projekt.businesspartner.model.BusinessPartner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {
    boolean existsByShortName(String shortName);
    boolean existsByShortNameAndIdNot(String shortName, Long id);
}
```

**Step 2: ContactPersonDto**

```java
package de.projekt.businesspartner.rest.dto;

import java.util.Set;

public class ContactPersonDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String street;
    private String zipCode;
    private String city;
    private Set<String> functions;

    // Getters + Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Set<String> getFunctions() { return functions; }
    public void setFunctions(Set<String> functions) { this.functions = functions; }
}
```

**Step 3: BusinessPartnerDto**

```java
package de.projekt.businesspartner.rest.dto;

import java.util.List;

public class BusinessPartnerDto {
    private Long id;
    private String shortName;
    private String name;
    private String notes;
    private List<ContactPersonDto> contacts;

    // Getters + Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<ContactPersonDto> getContacts() { return contacts; }
    public void setContacts(List<ContactPersonDto> contacts) { this.contacts = contacts; }
}
```

**Step 4: BusinessPartnerService**

```java
package de.projekt.businesspartner.service;

import de.projekt.businesspartner.model.BusinessPartner;
import de.projekt.businesspartner.model.ContactFunction;
import de.projekt.businesspartner.model.ContactPerson;
import de.projekt.businesspartner.repository.BusinessPartnerRepository;
import de.projekt.businesspartner.rest.dto.BusinessPartnerDto;
import de.projekt.businesspartner.rest.dto.ContactPersonDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class BusinessPartnerService {

    private final BusinessPartnerRepository repository;

    public BusinessPartnerService(BusinessPartnerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BusinessPartner> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public BusinessPartnerDto findById(Long id) {
        BusinessPartner entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id));
        return toDto(entity);
    }

    public BusinessPartnerDto create(BusinessPartnerDto dto) {
        if (repository.existsByShortName(dto.getShortName())) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }
        BusinessPartner entity = toEntity(dto);
        entity.setId(null);
        return toDto(repository.save(entity));
    }

    public BusinessPartnerDto update(Long id, BusinessPartnerDto dto) {
        BusinessPartner existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id));

        if (repository.existsByShortNameAndIdNot(dto.getShortName(), id)) {
            throw new IllegalStateException("Kurzbezeichnung bereits vergeben: " + dto.getShortName());
        }

        existing.setShortName(dto.getShortName());
        existing.setName(dto.getName());
        existing.setNotes(dto.getNotes());

        // Ansprechpartner synchronisieren: bestehende updaten, neue hinzufuegen, fehlende entfernen
        existing.getContacts().clear();
        if (dto.getContacts() != null) {
            for (ContactPersonDto cpDto : dto.getContacts()) {
                existing.getContacts().add(toContactEntity(cpDto));
            }
        }

        return toDto(repository.save(existing));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Geschaeftspartner nicht gefunden: id=" + id);
        }
        repository.deleteById(id);
    }

    // --- Mapping ---

    private BusinessPartnerDto toDto(BusinessPartner entity) {
        BusinessPartnerDto dto = new BusinessPartnerDto();
        dto.setId(entity.getId());
        dto.setShortName(entity.getShortName());
        dto.setName(entity.getName());
        dto.setNotes(entity.getNotes());
        dto.setContacts(entity.getContacts().stream()
                .map(this::toContactDto)
                .toList());
        return dto;
    }

    private ContactPersonDto toContactDto(ContactPerson entity) {
        ContactPersonDto dto = new ContactPersonDto();
        dto.setId(entity.getId());
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setPhone(entity.getPhone());
        dto.setStreet(entity.getStreet());
        dto.setZipCode(entity.getZipCode());
        dto.setCity(entity.getCity());
        dto.setFunctions(entity.getFunctions().stream()
                .map(Enum::name)
                .collect(Collectors.toSet()));
        return dto;
    }

    private BusinessPartner toEntity(BusinessPartnerDto dto) {
        BusinessPartner entity = new BusinessPartner();
        entity.setShortName(dto.getShortName());
        entity.setName(dto.getName());
        entity.setNotes(dto.getNotes());
        if (dto.getContacts() != null) {
            for (ContactPersonDto cpDto : dto.getContacts()) {
                entity.getContacts().add(toContactEntity(cpDto));
            }
        }
        return entity;
    }

    private ContactPerson toContactEntity(ContactPersonDto dto) {
        ContactPerson entity = new ContactPerson();
        entity.setId(dto.getId());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setStreet(dto.getStreet());
        entity.setZipCode(dto.getZipCode());
        entity.setCity(dto.getCity());
        if (dto.getFunctions() != null) {
            entity.setFunctions(dto.getFunctions().stream()
                    .map(ContactFunction::valueOf)
                    .collect(Collectors.toSet()));
        }
        return entity;
    }
}
```

**Step 5: Build pruefen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/de/projekt/businesspartner/
git commit -m "feat: BusinessPartner Repository, Service, DTOs"
```

---

### Task 5: REST-Controller

**Files:**
- Create: `src/main/java/de/projekt/businesspartner/rest/BusinessPartnerController.java`

**Step 1: Controller implementieren**

```java
package de.projekt.businesspartner.rest;

import de.projekt.businesspartner.model.BusinessPartner;
import de.projekt.businesspartner.rest.dto.BusinessPartnerDto;
import de.projekt.businesspartner.service.BusinessPartnerService;
import de.projekt.timeseries.rest.dto.ColumnMeta;
import de.projekt.timeseries.rest.dto.TableResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/business-partners")
public class BusinessPartnerController {

    private static final List<ColumnMeta> COLUMNS = List.of(
            new ColumnMeta("id", "ID", "bp.id", "NUMBER"),
            new ColumnMeta("shortName", "Kurzbezeichnung", "bp.short_name", "TEXT"),
            new ColumnMeta("name", "Name", "bp.name", "TEXT")
    );

    private final BusinessPartnerService service;

    public BusinessPartnerController(BusinessPartnerService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<TableResponse> getAll() {
        List<Map<String, Object>> data = service.findAll().stream()
                .map(this::toRow)
                .toList();
        return ResponseEntity.ok(new TableResponse(COLUMNS, data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessPartnerDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<BusinessPartnerDto> create(@RequestBody BusinessPartnerDto dto) {
        BusinessPartnerDto created = service.create(dto);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessPartnerDto> update(@PathVariable Long id, @RequestBody BusinessPartnerDto dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toRow(BusinessPartner bp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", bp.getId());
        row.put("shortName", bp.getShortName());
        row.put("name", bp.getName());
        return row;
    }
}
```

**Step 2: Backend starten und Endpoints testen**

Run: `./gradlew bootRun`

Dann in anderem Terminal:
```bash
# Liste (leer)
curl -s http://localhost:8080/api/business-partners | python -m json.tool

# Anlegen
curl -s -X POST http://localhost:8080/api/business-partners \
  -H "Content-Type: application/json" \
  -d '{"shortName":"SWM","name":"Stadtwerke Muenchen","contacts":[{"firstName":"Max","lastName":"Mustermann","functions":["ABRECHNUNG"]}]}'

# Detail lesen
curl -s http://localhost:8080/api/business-partners/1
```

**Step 3: Commit**

```bash
git add src/main/java/de/projekt/businesspartner/rest/BusinessPartnerController.java
git commit -m "feat: BusinessPartner REST-Controller"
```

---

### Task 6: Frontend — API-Client + Types

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`

**Step 1: Types ergaenzen**

In `frontend/src/api/types.ts` am Ende hinzufuegen:

```typescript
// Business Partner
export interface ContactPersonDto {
  id: number | null;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  street: string | null;
  zipCode: string | null;
  city: string | null;
  functions: string[];
}

export interface BusinessPartnerDto {
  id: number | null;
  shortName: string;
  name: string;
  notes: string | null;
  contacts: ContactPersonDto[];
}
```

**Step 2: API-Funktionen ergaenzen**

In `frontend/src/api/client.ts` am Ende hinzufuegen:

```typescript
// ==================== Business Partners ====================

export async function fetchBusinessPartner(id: number, signal?: AbortSignal): Promise<BusinessPartnerDto> {
  const res = await authFetch(`/api/business-partners/${id}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function saveBusinessPartner(dto: BusinessPartnerDto): Promise<BusinessPartnerDto> {
  const isNew = dto.id === null;
  const url = isNew ? '/api/business-partners' : `/api/business-partners/${dto.id}`;
  const res = await authFetch(url, {
    method: isNew ? 'POST' : 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function deleteBusinessPartner(id: number): Promise<void> {
  const res = await authFetch(`/api/business-partners/${id}`, { method: 'DELETE' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}
```

**Step 3: TypeScript pruefen**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: Keine Fehler

**Step 4: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/client.ts
git commit -m "feat: BusinessPartner API-Client + Types"
```

---

### Task 7: Frontend — Uebersichtsseite

**Files:**
- Create: `frontend/src/pages/BusinessPartnerPage.tsx`
- Modify: `frontend/src/shell/tabTypes.tsx`
- Modify: `src/main/resources/sidebar.xml`

**Step 1: BusinessPartnerPage erstellen**

```tsx
import { OverviewPage } from '../shared/overview-page/OverviewPage';
import { useTabContext } from '../shell/TabContext';

const columnOverrides = { id: { hidden: true } };

export function BusinessPartnerPage({ tabId: _tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
  return (
    <OverviewPage
      pageKey="business-partners"
      apiUrl="/api/business-partners"
      onNew={() => openTab('business-partner-detail', { mode: 'new' })}
      newLabel="Neuer Geschaeftspartner"
      columnOverrides={columnOverrides}
      emptyMessage="Keine Geschaeftspartner vorhanden"
      onRowDoubleClick={(row) => openTab('business-partner-detail', { mode: 'edit', entityId: row.id })}
    />
  );
}
```

**Step 2: Tab-Typen registrieren**

In `frontend/src/shell/tabTypes.tsx`:

1. Import hinzufuegen:
```typescript
import { BusinessPartnerPage } from '../pages/BusinessPartnerPage';
```

2. Icon hinzufuegen (Handshake/People-Icon):
```typescript
const iconPartner = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </svg>
);
```

3. Tab-Typ in `tabTypes`-Array einfuegen (vor `einstellungen`):
```typescript
{ type: 'business-partners', label: 'Geschaeftspartner', icon: iconPartner, singleton: true, component: BusinessPartnerPage },
```

Der Detail-Tab-Typ kommt in Task 8.

**Step 3: Sidebar-Eintrag**

In `src/main/resources/sidebar.xml` unter `<folder id="stammdaten">`:
```xml
<item id="business-partners" tabType="business-partners" />
```

**Step 4: Commit**

```bash
git add frontend/src/pages/BusinessPartnerPage.tsx frontend/src/shell/tabTypes.tsx src/main/resources/sidebar.xml
git commit -m "feat: Geschaeftspartner Uebersichtsseite"
```

---

### Task 8: Frontend — Detailseite

**Files:**
- Create: `frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx`
- Create: `frontend/src/pages/business-partner/ContactPersonCard.tsx`
- Create: `frontend/src/pages/business-partner/ContactPersonCard.css`
- Modify: `frontend/src/shell/tabTypes.tsx`

**Step 1: ContactPersonCard Komponente**

`ContactPersonCard.tsx`:
```tsx
import { useState } from 'react';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { Chip, ChipGroup } from '../../shared/Chip';
import type { ContactPersonDto } from '../../api/types';
import './ContactPersonCard.css';

const CONTACT_FUNCTIONS = [
  { value: 'ABRECHNUNG', label: 'Abrechnung' },
  { value: 'BK_VERANTWORTLICHER', label: 'BK-Verantwortlicher' },
];

interface ContactPersonCardProps {
  contact: ContactPersonDto;
  disabled: boolean;
  onChange: (updated: ContactPersonDto) => void;
  onRemove: () => void;
}

export function ContactPersonCard({ contact, disabled, onChange, onRemove }: ContactPersonCardProps) {
  const [expanded, setExpanded] = useState(contact.id === null);

  const displayName = contact.firstName || contact.lastName
    ? `${contact.firstName} ${contact.lastName}`.trim()
    : 'Neuer Ansprechpartner';

  const update = (field: keyof ContactPersonDto, value: unknown) => {
    onChange({ ...contact, [field]: value });
  };

  const toggleFunction = (fn: string) => {
    const current = new Set(contact.functions);
    if (current.has(fn)) current.delete(fn);
    else current.add(fn);
    onChange({ ...contact, functions: Array.from(current) });
  };

  return (
    <Card>
      <div className="contact-card">
        <div className="contact-card-header" onClick={() => setExpanded(!expanded)}>
          <span className="contact-card-toggle">{expanded ? '\u25BC' : '\u25B6'}</span>
          <span className="contact-card-name">{displayName}</span>
          <ChipGroup>
            {contact.functions.map(fn => {
              const def = CONTACT_FUNCTIONS.find(f => f.value === fn);
              return <Chip key={fn}>{def?.label ?? fn}</Chip>;
            })}
          </ChipGroup>
          {!disabled && (
            <button
              className="contact-card-remove"
              onClick={e => { e.stopPropagation(); onRemove(); }}
              title="Entfernen"
            >
              &times;
            </button>
          )}
        </div>
        {expanded && (
          <div className="contact-card-body">
            <div className="contact-card-row">
              <FormField label="Vorname">
                <input value={contact.firstName} onChange={e => update('firstName', e.target.value)} disabled={disabled} />
              </FormField>
              <FormField label="Nachname">
                <input value={contact.lastName} onChange={e => update('lastName', e.target.value)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-row">
              <FormField label="E-Mail">
                <input value={contact.email ?? ''} onChange={e => update('email', e.target.value || null)} disabled={disabled} />
              </FormField>
              <FormField label="Telefon">
                <input value={contact.phone ?? ''} onChange={e => update('phone', e.target.value || null)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-row">
              <FormField label="Strasse">
                <input value={contact.street ?? ''} onChange={e => update('street', e.target.value || null)} disabled={disabled} />
              </FormField>
              <FormField label="PLZ">
                <input value={contact.zipCode ?? ''} onChange={e => update('zipCode', e.target.value || null)} disabled={disabled} style={{ maxWidth: '100px' }} />
              </FormField>
              <FormField label="Ort">
                <input value={contact.city ?? ''} onChange={e => update('city', e.target.value || null)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-functions">
              <span className="contact-card-functions-label">Funktionen:</span>
              {CONTACT_FUNCTIONS.map(fn => (
                <label key={fn.value} className="contact-card-checkbox">
                  <input
                    type="checkbox"
                    checked={contact.functions.includes(fn.value)}
                    onChange={() => toggleFunction(fn.value)}
                    disabled={disabled}
                  />
                  {fn.label}
                </label>
              ))}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
```

`ContactPersonCard.css`:
```css
.contact-card {
  padding: var(--space-sm);
}

.contact-card-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  cursor: pointer;
  padding: var(--space-xs) 0;
}

.contact-card-toggle {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  width: 16px;
}

.contact-card-name {
  font-weight: 600;
  flex: 1;
}

.contact-card-remove {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  font-size: 1.25rem;
  cursor: pointer;
  padding: 0 var(--space-xs);
  line-height: 1;
}
.contact-card-remove:hover {
  color: var(--color-error);
}

.contact-card-body {
  padding: var(--space-md) 0 var(--space-xs) calc(16px + var(--space-sm));
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.contact-card-row {
  display: flex;
  gap: var(--space-md);
}
.contact-card-row > * {
  flex: 1;
}

.contact-card-functions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding-top: var(--space-xs);
}

.contact-card-functions-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.contact-card-checkbox {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  font-size: var(--font-size-sm);
  cursor: pointer;
}
```

**Step 2: BusinessPartnerDetailPage**

```tsx
import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { Button } from '../../shared/Button';
import { ContactPersonCard } from './ContactPersonCard';
import { useTabContext } from '../../shell/TabContext';
import { fetchBusinessPartner, saveBusinessPartner, deleteBusinessPartner } from '../../api/client';
import type { BusinessPartnerDto, ContactPersonDto } from '../../api/types';

const emptyContact = (): ContactPersonDto => ({
  id: null,
  firstName: '',
  lastName: '',
  email: null,
  phone: null,
  street: null,
  zipCode: null,
  city: null,
  functions: [],
});

export function BusinessPartnerDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<BusinessPartnerDto>({
    id: null,
    shortName: '',
    name: '',
    notes: null,
    contacts: [],
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchBusinessPartner(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `GP: ${result.shortName}`);
      setLoading(false);
    }).catch(() => setLoading(false));
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const updateField = useCallback((field: keyof BusinessPartnerDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const updateContact = useCallback((index: number, updated: ContactPersonDto) => {
    setData(prev => {
      const contacts = [...prev.contacts];
      contacts[index] = updated;
      return { ...prev, contacts };
    });
    setDirty(true);
  }, []);

  const removeContact = useCallback((index: number) => {
    setData(prev => ({
      ...prev,
      contacts: prev.contacts.filter((_, i) => i !== index),
    }));
    setDirty(true);
  }, []);

  const addContact = useCallback(() => {
    setData(prev => ({
      ...prev,
      contacts: [...prev.contacts, emptyContact()],
    }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (!data.shortName.trim()) errors.push({ field: 'shortName', message: 'Kurzbezeichnung' });
    data.contacts.forEach((c, i) => {
      if (!c.firstName.trim()) errors.push({ field: `contact-${i}-firstName`, message: `Ansprechpartner ${i + 1}: Vorname` });
      if (!c.lastName.trim()) errors.push({ field: `contact-${i}-lastName`, message: `Ansprechpartner ${i + 1}: Nachname` });
    });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveBusinessPartner(data);
    setData(saved);
    updateTabLabel(tabId, `GP: ${saved.shortName}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteBusinessPartner(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('business-partner-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="business-partners"
      mode={mode}
      tabId={tabId}
      title={mode === 'new' ? 'Neuer Geschaeftspartner' : `Geschaeftspartner: ${data.shortName || '...'}`}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
      onDelete={handleDelete}
      onNew={handleNew}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <FormField label="Kurzbezeichnung">
              <input
                value={data.shortName}
                onChange={e => updateField('shortName', e.target.value)}
                disabled={isDisabled}
                maxLength={50}
              />
            </FormField>
            <FormField label="Name" style={{ flex: 1 }}>
              <input
                value={data.name}
                onChange={e => updateField('name', e.target.value)}
                disabled={isDisabled}
              />
            </FormField>
          </div>
          <FormField label="Notizen">
            <textarea
              value={data.notes ?? ''}
              onChange={e => updateField('notes', e.target.value || null)}
              disabled={isDisabled}
              rows={3}
              style={{ resize: 'vertical' }}
            />
          </FormField>
        </div>
      </Card>

      <div style={{ marginTop: 'var(--space-md)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)' }}>Ansprechpartner</h3>
          {!isDisabled && (
            <Button variant="ghost" onClick={addContact}>+ Ansprechpartner hinzufuegen</Button>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          {data.contacts.map((contact, index) => (
            <ContactPersonCard
              key={contact.id ?? `new-${index}`}
              contact={contact}
              disabled={isDisabled}
              onChange={updated => updateContact(index, updated)}
              onRemove={() => removeContact(index)}
            />
          ))}
          {data.contacts.length === 0 && (
            <div style={{ padding: 'var(--space-md)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
              Keine Ansprechpartner vorhanden
            </div>
          )}
        </div>
      </div>
    </DetailPage>
  );
}
```

**Step 3: Tab-Typ fuer Detail registrieren**

In `frontend/src/shell/tabTypes.tsx`:

1. Import:
```typescript
import { BusinessPartnerDetailPage } from '../pages/business-partner/BusinessPartnerDetailPage';
```

2. Tab-Typ in Array:
```typescript
{ type: 'business-partner-detail', label: 'Geschaeftspartner', icon: iconPartner, component: BusinessPartnerDetailPage },
```

**Step 4: TypeScript pruefen**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: Keine Fehler

**Step 5: Commit**

```bash
git add frontend/src/pages/business-partner/ frontend/src/shell/tabTypes.tsx
git commit -m "feat: Geschaeftspartner Detailseite mit Ansprechpartner-Cards"
```

---

### Task 9: Integration testen

**Step 1: Backend starten**

Run: `./gradlew bootRun`

**Step 2: Frontend starten (falls nicht laufend)**

Run: `cd frontend && npm run dev`

**Step 3: Manuell testen**

Checkliste:
- [ ] Sidebar: "Geschaeftspartner" unter "Stammdaten" sichtbar
- [ ] Klick oeffnet Uebersichtsseite (leer)
- [ ] Neu-Button oeffnet Detail-Tab im `new`-Modus
- [ ] Felder ausfuellen: Kurzbezeichnung, Name, Notizen
- [ ] Ansprechpartner hinzufuegen, Felder ausfuellen
- [ ] Speichern: Daten werden persistiert
- [ ] Uebersicht aktualisieren: neuer GP in der Liste
- [ ] Doppelklick auf Zeile: Detail-Tab im `edit`-Modus
- [ ] Aenderungen vornehmen, Speichern
- [ ] Loeschen-Button: Bestaetigungsdialog, GP wird entfernt
- [ ] Dirty-Guard: Ungespeicherte Aenderungen, Tab schliessen → Warnung

**Step 4: Commit (falls Fixes noetig)**

```bash
git add -A
git commit -m "fix: Geschaeftspartner Integration-Fixes"
```

---

### Task 10: CLAUDE.md + TODO.md aktualisieren

**Files:**
- Modify: `CLAUDE.md` (Projektstruktur erweitern um businesspartner-Package)
- Modify: `TODO.md` (J1-J3 abhaken, ggf. neue Folge-TODOs)
- Modify: `DONE.md` (Eintrag ergaenzen)

**Step 1: CLAUDE.md**

In der Projektstruktur ergaenzen:
```
    businesspartner/
        model/
            BusinessPartner.java       -- @Entity, JPA
            ContactPerson.java         -- @Entity, JPA
            ContactFunction.java       -- Enum (Abrechnung, BK-Verantwortlicher)
        repository/
            BusinessPartnerRepository.java -- JpaRepository
        service/
            BusinessPartnerService.java -- @Service, Validierung, Mapping
        rest/
            BusinessPartnerController.java -- @RestController /api/business-partners
            dto/
                BusinessPartnerDto.java
                ContactPersonDto.java
```

Architektur-Entscheidung dokumentieren:
- **JPA** fuer Detail-Entitaeten (CRUD), **Raw JDBC** fuer Uebersichten und Performance

Frontend-Struktur:
```
    pages/
      BusinessPartnerPage.tsx          -- GP-Uebersicht (OverviewPage + VirtualTable)
      business-partner/
        BusinessPartnerDetailPage.tsx  -- GP-Detail (DetailPage-Template)
        ContactPersonCard.tsx + .css   -- Aufklappbare Ansprechpartner-Card
```

**Step 2: TODO.md**

- [x] J1 — JPA/Hibernate eingerichtet
- [x] J2 — Geschaeftspartner Entity + Repository, REST-Controller
- [x] J3 — Frontend: Uebersichts- und Detailseite

**Step 3: DONE.md**

```
- 2026-03-07: Geschaeftspartner-Modul (JPA): Entities, Service, REST-API, Uebersichtsseite, Detailseite mit Ansprechpartner-Cards
```

**Step 4: Commit**

```bash
git add CLAUDE.md TODO.md DONE.md
git commit -m "docs: Geschaeftspartner-Modul dokumentiert, TODOs aktualisiert"
```
