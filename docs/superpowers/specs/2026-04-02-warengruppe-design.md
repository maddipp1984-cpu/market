# Design: Modul "Warengruppe" (Commodity Group)

## Zusammenfassung

Einfaches Stammdaten-Modul zur Pflege von Warengruppen. Jede Warengruppe hat einen eindeutigen Namen. Spaeter werden Waren einer Warengruppe zugeordnet (z.B. Gas -> H-Gas, L-Gas).

## Datenmodell

```sql
CREATE TABLE ts_commodity_group (
    commodity_group_id  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                TEXT NOT NULL UNIQUE
);
```

## Backend (Currency-Pattern)

```
de.market.commoditygroup/
    model/
        CommodityGroupEntity.java          -- @Entity auf ts_commodity_group
    repository/
        CommodityGroupJpaRepository.java   -- existsByName, existsByNameAndIdNot
        CommodityGroupOverviewRepository.java -- jOOQ, extends AbstractOverviewRepository
    service/
        CommodityGroupService.java         -- extends AbstractCrudService
    rest/
        CommodityGroupController.java      -- /api/commodity-groups
        dto/
            CommodityGroupDto.java         -- id, name
```

## REST-API

| Method | Pfad | Beschreibung |
|--------|------|-------------|
| GET | `/api/commodity-groups` | Uebersicht (TableResponse) |
| POST | `/api/commodity-groups/query` | Gefilterte Uebersicht |
| GET | `/api/commodity-groups/{id}` | Einzelner Datensatz (DTO) |
| POST | `/api/commodity-groups` | Anlegen |
| PUT | `/api/commodity-groups/{id}` | Aendern |
| DELETE | `/api/commodity-groups/{id}` | Loeschen (FK-Schutz via Exception) |

## Validierung

- `name`: Pflichtfeld, nicht leer, UNIQUE

## Frontend

- **WarengruppenPage.tsx** — OverviewPage (singleton Tab), Spalte: Name
- **WarengruppeDetailPage.tsx** — DetailPage mit einem Textfeld (Name)
- **Sidebar**: Unter "Stammdaten" in sidebar.xml + sidebarTree.ts Fallback
- **Tab-Types**: `warengruppen` (singleton) + `warengruppe-detail`

## Nicht im Scope

- Waren (kommen spaeter, werden einer Warengruppe zugeordnet)
- FK von anderen Tabellen auf ts_commodity_group
