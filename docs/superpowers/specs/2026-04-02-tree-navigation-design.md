# TreeNavigation — Wiederverwendbare Baum-Detailmaske

## Zusammenfassung

Wiederverwendbare Frontend-Komponente `TreeNavigation` für Detailmasken mit Baum-Navigation links und Frame-Bereich rechts. Alle Frames arbeiten auf einem gemeinsamen DTO — ein Save speichert alles in einer Transaktion. Erster Anwendungsfall: Geschäftspartner-Detailmaske.

## Entscheidungen

| Frage | Entscheidung |
|-------|-------------|
| Navigation zwischen Knoten | Freier Wechsel, invalide Knoten werden markiert, Validierung blockiert nur beim Speichern |
| Knoten mit/ohne Frame | Konfigurierbar pro Knoten (`hasFrame`-Flag) |
| Initialer Baum-Zustand | Komplett aufgeklappt, erster Knoten selektiert |
| Baum-Breite | Resizable per Drag-Splitter, Default 220px |
| Knoten-Darstellung | Ohne Icons, nur Text + Chevrons. Frei renderbar per `renderNode` für Badges/Warn-Icons |
| Architektur-Ansatz | Composition: `TreeNavigation` als Kind innerhalb der bestehenden `DetailPage` |

## Komponenten-Architektur

### Übersicht

```
DetailPage (bestehend, unverändert)
└── TreeNavigation (neu, in shared/)
    ├── TreePanel (links, Baum)
    │   └── TreeView (bestehend, wiederverwendet)
    ├── ResizeHandle (Splitter)
    └── FramePanel (rechts, aktiver Frame)
```

`DetailPage` bleibt Single Source of Truth für Toolbar, Save, Delete, CloseGuard. `TreeNavigation` ist ein reines Layout+Routing-Composite, das innerhalb von `DetailPage` als `children` gerendert wird.

### TreeNavigation

Generische Komponente in `frontend/src/shared/tree-navigation/`.

```typescript
interface TreeNodeDef {
  id: string;
  label: string;
  children?: TreeNodeDef[];
  hasFrame?: boolean; // default: true. false = nur Gruppierung, kein Frame
}

interface FrameProps<T> {
  data: T;
  onChange: (updated: T) => void;
  disabled: boolean; // true im view-Mode
}

interface TreeNavigationProps<T> {
  nodes: TreeNodeDef[];
  frames: Record<string, React.ComponentType<FrameProps<T>>>;
  data: T;
  onChange: (updated: T) => void;
  disabled?: boolean;
  validationErrors?: Record<string, string[]>; // knotenId → Fehlermeldungen
  renderNode?: (node: TreeNodeDef, hasErrors: boolean) => ReactNode;
  defaultWidth?: number; // Default: 220
}
```

**Verhalten:**
- Initialer Zustand: Baum komplett aufgeklappt, erster Knoten mit `hasFrame !== false` ist selektiert
- Klick auf Knoten mit Frame: Frame-Bereich wechselt, vorheriger Frame-State bleibt erhalten (kein Unmount/Remount — bedingtes Rendering via `display: none` oder Key-basiert)
- Klick auf Ordner-Knoten ohne Frame: klappt auf/zu, Frame bleibt unverändert
- `validationErrors`: Knoten mit Einträgen bekommen visuellen Indikator (z.B. roter Punkt oder Warn-Symbol)
- `renderNode`: Override für beliebige Knoten-Darstellung (Badges, Counts, Custom-Styling)

### TreePanel

Wrapper um die bestehende `TreeView`-Komponente mit `variant="light"` (da der Frame-Bereich den hellen Hintergrund von `DetailPage` nutzt).

- Nutzt `TreeView` mit `selectOnClick={true}` und `selectedId` für kontrollierte Selektion
- `onSelect`-Callback filtert Knoten ohne Frame heraus
- Einrückung: alle Ebene-1-Knoten bündig, Chevron nimmt festen Platz ein

### ResizeHandle

Draggable Splitter zwischen TreePanel und FramePanel.

- Cursor: `col-resize`
- Drag-Logik: `onMouseDown` → `mousemove`/`mouseup` Listener auf `document`
- Minimum-Breite Baum: 150px
- Maximum-Breite Baum: 400px
- Visueller Indikator: schmaler vertikaler Strich

### FramePanel

Container, der die Frame-Komponente des aktiven Knotens rendert.

- Bekommt `data`, `onChange`, `disabled` als Props und reicht sie an den aktiven Frame weiter
- Frame-Wechsel: alle Frames bleiben im DOM, nur der aktive ist sichtbar (`display: none` für inaktive). Dadurch bleibt lokaler State (z.B. aufgeklappte ContactPersonCards, Scroll-Position) erhalten

## Dateien und Ordnerstruktur

### Neue Dateien (shared)

```
frontend/src/shared/tree-navigation/
    TreeNavigation.tsx      -- Hauptkomponente (Layout: TreePanel | ResizeHandle | FramePanel)
    TreeNavigation.css      -- Styling (Flex-Layout, Splitter, Frame-Container)
    types.ts                -- TreeNodeDef, FrameProps, TreeNavigationProps
```

### Neue Dateien (Geschäftspartner-Umbau)

```
frontend/src/pages/business-partner/frames/
    StammdatenFrame.tsx     -- Kurzbezeichnung, Name, Notizen
    AnsprechpartnerFrame.tsx -- Liste von ContactPersonCards (bestehende Komponente)
```

### Geänderte Dateien

```
frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx
    -- Umbau: TreeNavigation statt direkter Formular-Rendering
    -- Baum-Definition + Frame-Map
    -- Validierung über alle Frames aggregiert

frontend/src/shared/TreeView.tsx
    -- Keine Änderung nötig (renderNode + selectedId bereits vorhanden)

frontend/src/shared/detail-page/DetailPage.tsx
    -- Keine Änderung nötig

frontend/src/shared/detail-page/DetailPage.css
    -- Kleine Anpassung: detail-page-content Padding entfernen wenn TreeNavigation
       als Kind vorhanden (TreeNavigation bringt eigenes Padding im FramePanel mit)
```

## Geschäftspartner-Umbau (erster Anwendungsfall)

### Baum-Definition

```typescript
const treeNodes: TreeNodeDef[] = [
  {
    id: 'stammdaten',
    label: 'Stammdaten',
    children: [
      { id: 'ansprechpartner', label: 'Ansprechpartner' },
    ],
  },
];
```

Bewusst einfach gehalten — weitere Knoten (Adressen, Verträge etc.) werden ergänzt wenn die Backend-Daten dafür existieren.

### Frame-Map

```typescript
const frames = {
  stammdaten: StammdatenFrame,
  ansprechpartner: AnsprechpartnerFrame,
};
```

### StammdatenFrame

Extrahiert aus dem bestehenden `BusinessPartnerDetailPage`:
- Kurzbezeichnung (shortName)
- Name
- Notizen (notes)

### AnsprechpartnerFrame

Extrahiert aus dem bestehenden `BusinessPartnerDetailPage`:
- Liste von `ContactPersonCard`-Komponenten (bestehend, unverändert)
- "Hinzufügen"-Button
- Leerer-Zustand-Meldung

### Validierung

Der Container (`BusinessPartnerDetailPage`) aggregiert Validierung über alle Frames:

```typescript
function validate(): ValidationResult {
  const errors: Record<string, string[]> = {};
  
  if (!data.shortName.trim()) {
    errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Kurzbezeichnung ist erforderlich'];
  }
  if (!data.name.trim()) {
    errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Name ist erforderlich'];
  }
  
  data.contacts.forEach((c, i) => {
    if (!c.firstName.trim() || !c.lastName.trim()) {
      errors['ansprechpartner'] = [...(errors['ansprechpartner'] || []), `Ansprechpartner ${i + 1}: Vor- und Nachname erforderlich`];
    }
  });
  
  setValidationErrors(errors);
  
  return {
    valid: Object.keys(errors).length === 0,
    errors: Object.entries(errors).flatMap(([, msgs]) => 
      msgs.map(m => ({ field: '', message: m }))
    ),
  };
}
```

### Datenfluss

```
BusinessPartnerDetailPage (State: BusinessPartnerDto)
├── DetailPage (Toolbar, Save, Delete, CloseGuard)
│   └── TreeNavigation (nodes, frames, data, onChange, validationErrors)
│       ├── TreePanel → TreeView (Knoten-Auswahl)
│       ├── ResizeHandle
│       └── FramePanel
│           ├── StammdatenFrame (data.shortName, data.name, data.notes)
│           └── AnsprechpartnerFrame (data.contacts)
```

Ein einziger State-Holder, ein Save-Button, eine Transaktion. Die API-Schnittstelle (`saveBusinessPartner`) bleibt unverändert — das DTO enthält bereits alle Daten inklusive Kontakte.

## Styling

- Nutzt ausschließlich CSS Custom Properties aus `tokens.css`
- TreeNavigation-Container: `display: flex`, volle Höhe des `detail-page-content`
- TreePanel: feste/variable Breite, `overflow-y: auto`, heller Hintergrund (`--color-surface-raised` oder `--color-surface`)
- ResizeHandle: 4px breit, `cursor: col-resize`, subtiler visueller Indikator
- FramePanel: `flex: 1`, eigenes Padding (`--space-lg`), `overflow-y: auto`
- Aktiver Knoten: Hervorhebung per `--color-primary` Hintergrund
- Fehler-Indikator: roter Punkt oder `--color-error` am Knoten

## Abgrenzung

- **Kein neues Tab**: TreeNavigation arbeitet innerhalb eines bestehenden Tabs
- **Kein eigener Save-Mechanismus**: Save kommt von DetailPage
- **Kein Backend-Umbau**: Das BusinessPartner-DTO und die API bleiben unverändert
- **Keine neuen Knoten ohne Backend-Daten**: Der Baum zeigt nur Stammdaten + Ansprechpartner, weitere Knoten kommen mit zukünftigen Features
