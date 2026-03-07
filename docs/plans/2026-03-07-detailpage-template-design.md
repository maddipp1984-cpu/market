# Design: DetailPage-Template

## Uebersicht
Shared-Komponente `<DetailPage>` als Rahmen fuer alle Detailmasken im Zeitreihensystem.
Steuert Toolbar, Modi, Loading/Error, Dirty-Warnung, Validierung und Berechtigungen.
Das innere Layout (Felder, Gruppen, Anordnung) bleibt komplett in der Hand der jeweiligen Seite.

## Modi
| Modus | Beschreibung | Quelle |
|-------|-------------|--------|
| `view` | Nur Anzeige, alle Felder readonly | Uebersicht: Zeile anklicken/anzeigen |
| `edit` | Bearbeiten eines bestehenden Datensatzes | Uebersicht: Bearbeiten-Aktion |
| `new` | Leere Maske, neuer Datensatz | Uebersicht oder DetailPage: Neu-Button |

Modus wird beim Tab-Oeffnen aus der Uebersicht festgelegt und ist innerhalb der Detailmaske nicht aenderbar.

## Standard-Toolbar
| Button | Sichtbar wenn | Berechtigung |
|--------|--------------|--------------|
| Neu | immer | `canWrite` |
| Speichern | `edit` oder `new` | - |
| Speichern & Schliessen | `edit` oder `new` | - |
| Loeschen | `edit` | `canDelete` |

Zusaetzlich: `extraActions`-Prop fuer seitenspezifische Buttons in der Toolbar.

## Props
```ts
interface DetailPageProps {
  pageKey: string;              // fuer Berechtigungspruefung (canWrite/canDelete)
  mode: 'view' | 'edit' | 'new';
  tabId: string;                // fuer Tab-Label + Tab-Schliessen
  title: string;
  dirty: boolean;               // Dirty-Flag, von der Detailseite gesteuert
  validate: () => ValidationResult;  // Pflicht! Validierung vor Speichern
  onSave: () => Promise<void>;
  onDelete?: () => Promise<void>;
  onNew?: () => void;           // oeffnet neuen Tab im new-Modus
  extraActions?: React.ReactNode;
  children: React.ReactNode;    // freies Layout, keine Vorgaben
}

interface ValidationResult {
  valid: boolean;
  errors: { field: string; message: string }[];
}
```

## Verhalten

### Speichern
1. `validate()` aufrufen
2. Bei `valid: false`: Fehler via MessageBar anzeigen ("Bitte Pflichtfelder ausfuellen"), NICHT speichern
3. Bei `valid: true`: Loading-State auf Button, `onSave()` aufrufen
4. Erfolg: MessageBar "Gespeichert" (success)
5. Fehler: MessageBar mit Fehlermeldung (error)

### Speichern & Schliessen
Wie Speichern, bei Erfolg zusaetzlich Tab schliessen.

### Loeschen
1. Bestaetigungsdialog ("Wirklich loeschen?")
2. Bei Ja: Loading-State, `onDelete()` aufrufen
3. Erfolg: MessageBar "Geloescht" (success), Tab schliessen
4. Fehler: MessageBar mit Fehlermeldung (error)

### Neu
`onNew()` oeffnet einen neuen Tab im `new`-Modus (gleicher Tab-Typ).

### Dirty-Erkennung
Wenn der User einen Tab mit `dirty=true` schliesst: Bestaetigungsdialog ("Ungespeicherte Aenderungen verwerfen?").

### Validierung und Feldmarkierung
- `validate()` ist ein Pflicht-Prop (wie eine abstrakte Methode in Java)
- Die Detailseite implementiert die Validierungslogik und kennt ihre Pflichtfelder
- Bei Validierungsfehlern markiert die Detailseite die betroffenen Felder selbst (z.B. roter Rahmen)
- Die `errors`-Liste wird der Detailseite als State zugaenglich gemacht

### Berechtigungen
- `canWrite(pageKey)` = false: Neu-Button und Speichern-Buttons nicht sichtbar
- `canDelete(pageKey)` = false: Loeschen-Button nicht sichtbar
- Im `view`-Modus: Speichern/Loeschen-Buttons ohnehin nicht sichtbar

## Integration

### Tab-System
Das Tab-System muss erweitert werden, damit beim Oeffnen eines Tabs Parameter mitgegeben werden koennen:
- `mode: 'view' | 'edit' | 'new'`
- `entityId: number | string` (bei edit/view)

### OverviewPage
OverviewPage braucht eine Moeglichkeit, bei Klick auf eine Zeile einen Detail-Tab zu oeffnen (mit mode + entityId). Z.B. ein `onRowClick`-Prop oder `onRowAction`-Prop.

### Hybrid-Ansatz (Daten)
- Backend liefert Daten als JSON (+ ggf. Validierungsregeln)
- Frontend definiert Layout und Feld-Anordnung frei (children)
- Konsistent mit OverviewPage-Ansatz (Backend liefert Daten, Frontend steuert Darstellung)

## Abgrenzung
- Kein festes Feld-Layout (einspaltig, zweispaltig etc.) — jede Maske ist individuell
- Kein automatisches Formular-Rendering — die Seite baut ihr Layout selbst
- Das Template ist nur der Rahmen (Toolbar + Verhalten), nicht der Inhalt
