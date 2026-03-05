# Architektur-Diagramm: Request-Flow

## Schichten-Uebersicht

```mermaid
graph LR
    Client([HTTP Client]) --> Tomcat

    subgraph Spring Boot
        Tomcat --> TSC[TimeSeriesController]
        Tomcat --> OC[ObjectController]
        Tomcat -.->|Fehler| GEH[GlobalExceptionHandler]

        TSC --> TSS[TimeSeriesService]
        OC --> TSS

        TSS --> HR[HeaderRepository]
        TSS --> OR[ObjectRepository]
        TSS --> TSR[TimeSeriesRepository]

        HR --> DS[(DataSource)]
        OR --> DS
        TSR --> DS
    end

    DS --> DB[(TimescaleDB)]
```

## Detaillierter Request-Flow: Zeitreihen

```mermaid
flowchart TD
    subgraph REST["REST-Schicht (TimeSeriesController)"]
        A1["POST /api/timeseries\ncreate()"]
        A2["GET /api/timeseries/{tsId}\ngetById()"]
        A3["GET /api/timeseries?key=...\ngetByKey()"]
        A4["POST /api/timeseries/{tsId}/values\nwriteDay()"]
        A5["GET /api/timeseries/{tsId}/values\nread()"]
        A6["DELETE /api/timeseries/{tsId}\ndelete()"]
        A7["DELETE /api/timeseries/{tsId}/values\ndeleteValues()"]
        A8["GET /api/timeseries/{tsId}/count\ncount()"]
    end

    subgraph SERVICE["Service-Schicht (TimeSeriesService)"]
        S1[createTimeSeries]
        S2[getHeaderById]
        S3[getHeader ByKey]
        S4[writeDay]
        S5[read]
        S6[deleteTimeSeries]
        S7[deleteValues]
        S8[count]
    end

    subgraph REPO["Repository-Schicht"]
        HR1["HeaderRepository\n.create()"]
        HR2["HeaderRepository\n.findById()"]
        HR3["HeaderRepository\n.findByKey()"]
        HR4["HeaderRepository\n.delete()"]
        TSR1["TimeSeriesRepository\n.writeDay()"]
        TSR2["TimeSeriesRepository\n.read()"]
        TSR3["TimeSeriesRepository\n.delete()"]
        TSR4["TimeSeriesRepository\n.count()"]
    end

    subgraph DB["Datenbank (TimescaleDB)"]
        TH[(ts_header)]
        TV15[(ts_values_15min)]
        TV1H[(ts_values_1h)]
        TVD[(ts_values_day)]
        TVM[(ts_values_month)]
        TVY[(ts_values_year)]
    end

    A1 --> S1 --> HR1 --> TH
    A2 --> S2 --> HR2 --> TH
    A3 --> S3 --> HR3 --> TH
    A4 --> S4 --> TSR1 --> TV15 & TV1H
    A5 --> S5 --> TSR2 --> TV15 & TV1H
    A6 --> S6 --> TSR3 & HR4
    TSR3 --> TV15 & TV1H
    HR4 --> TH
    A7 --> S7 --> TSR3
    A8 --> S8 --> TSR4 --> TV15 & TV1H & TVD & TVM & TVY

    S4 -.->|requireHeader| HR2
    S5 -.->|requireHeader| HR2
    S6 -.->|requireHeader| HR2
    S7 -.->|requireHeader| HR2
    S8 -.->|requireHeader| HR2
```

## Detaillierter Request-Flow: Objekte

```mermaid
flowchart TD
    subgraph REST["REST-Schicht (ObjectController)"]
        B1["POST /api/objects\ncreate()"]
        B2["GET /api/objects/{id}\ngetById()"]
        B3["GET /api/objects?key=...\ngetByKey()"]
        B4["GET /api/objects?type=...\ngetByType()"]
        B5["GET /api/objects/{id}/timeseries\ngetTimeSeries()"]
        B6["PUT /api/objects/{id}/timeseries/{tsId}\nassign()"]
        B7["DELETE /api/objects/{id}/timeseries/{tsId}\nunassign()"]
        B8["DELETE /api/objects/{id}\ndelete()"]
    end

    subgraph SERVICE["Service-Schicht (TimeSeriesService)"]
        S1[createObject]
        S2[getObject ById]
        S3[getObject ByKey]
        S4[getObjectsByType]
        S5[getTimeSeriesByObject]
        S6[assignToObject]
        S7[removeFromObject]
        S8[deleteObject]
    end

    subgraph REPO["Repository-Schicht"]
        OR1["ObjectRepository\n.create()"]
        OR2["ObjectRepository\n.findById()"]
        OR3["ObjectRepository\n.findByKey()"]
        OR4["ObjectRepository\n.findByType()"]
        OR5["ObjectRepository\n.delete()"]
        HR1["HeaderRepository\n.findByObjectId()"]
        HR2["HeaderRepository\n.updateObjectId()"]
        HR3["HeaderRepository\n.findById()"]
    end

    subgraph DB["Datenbank"]
        TO[(ts_object)]
        TH[(ts_header)]
    end

    B1 --> S1 --> OR1 --> TO
    B2 --> S2 --> OR2 --> TO
    B3 --> S3 --> OR3 --> TO
    B4 --> S4 --> OR4 --> TO
    B5 --> S5 --> HR1 --> TH
    B6 --> S6 --> HR2 --> TH
    S6 -.->|requireHeader| HR3
    S6 -.->|requireObject| OR2
    B7 --> S7 --> HR2
    B8 --> S8 --> OR5 --> TO
    S8 -.->|prueft Zuordnungen| HR1
```

## Exception-Flow

```mermaid
flowchart LR
    subgraph Controller
        C[REST Endpoint]
    end

    subgraph Errors
        E1[MethodArgumentNotValidException]
        E2[IllegalArgumentException]
        E3[IllegalStateException]
        E4[SQLException]
    end

    subgraph GEH[GlobalExceptionHandler]
        H1["handleValidation()\n400 + Feldnamen"]
        H2["handleBadRequest()\n400 + Meldung"]
        H3["handleConflict()\n409 + Meldung"]
        H4["handleSqlException()\n500 + generische Meldung"]
    end

    C -->|"@Valid fehlschlaegt"| E1 --> H1
    C -->|"Nicht gefunden / ungueltig"| E2 --> H2
    C -->|"Objekt hat Zuordnungen"| E3 --> H3
    C -->|"DB-Fehler"| E4 --> H4
```

## Bean-Wiring (Dependency Injection)

```mermaid
graph TD
    DS["DataSource\n(Spring auto-config\naus application.properties)"]

    HR["HeaderRepository\n@Repository"] -->|constructor| DS
    OR["ObjectRepository\n@Repository"] -->|constructor| DS
    TSR["TimeSeriesRepository\n@Repository"] -->|constructor| DS

    TSS["TimeSeriesService\n@Service"] -->|constructor| HR & OR & TSR

    TSC["TimeSeriesClient\n@Component"] -->|constructor| TSS
    CTRL1["TimeSeriesController\n@RestController"] -->|constructor| TSS
    CTRL2["ObjectController\n@RestController"] -->|constructor| TSS
```
