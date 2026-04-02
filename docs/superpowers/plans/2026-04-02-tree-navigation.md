# TreeNavigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wiederverwendbare `TreeNavigation`-Komponente fuer Detailmasken mit Baum-Navigation + Frame-Switching, erster Anwendungsfall: Geschaeftspartner-Umbau.

**Architecture:** Composition-Ansatz — `TreeNavigation` wird als Kind innerhalb der bestehenden `DetailPage` gerendert. Besteht aus TreePanel (nutzt bestehende `TreeView`), ResizeHandle (Drag-Splitter) und FramePanel (rendert aktiven Frame). Alle Frames teilen sich ein DTO, ein Save speichert alles.

**Tech Stack:** React 18, TypeScript, Headless Tree (bestehend), CSS Custom Properties (Design Tokens)

---

### Task 1: Types definieren

**Files:**
- Create: `frontend/src/shared/tree-navigation/types.ts`

- [ ] **Step 1: Type-Datei erstellen**

```typescript
// frontend/src/shared/tree-navigation/types.ts
import type { ReactNode } from 'react';

export interface TreeNodeDef {
  id: string;
  label: string;
  children?: TreeNodeDef[];
  hasFrame?: boolean;
}

export interface FrameProps<T> {
  data: T;
  onChange: (updated: T) => void;
  disabled: boolean;
}

export interface TreeNavigationProps<T> {
  nodes: TreeNodeDef[];
  frames: Record<string, React.ComponentType<FrameProps<T>>>;
  data: T;
  onChange: (updated: T) => void;
  disabled?: boolean;
  validationErrors?: Record<string, string[]>;
  renderNode?: (node: TreeNodeDef, hasErrors: boolean) => ReactNode;
  defaultWidth?: number;
}
```

- [ ] **Step 2: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS (keine Fehler)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/shared/tree-navigation/types.ts
git commit -m "feat: TreeNavigation Types (TreeNodeDef, FrameProps, TreeNavigationProps)"
```

---

### Task 2: TreeNavigation CSS

**Files:**
- Create: `frontend/src/shared/tree-navigation/TreeNavigation.css`

- [ ] **Step 1: CSS-Datei erstellen**

```css
/* frontend/src/shared/tree-navigation/TreeNavigation.css */
.tree-navigation {
  display: flex;
  flex: 1;
  min-height: 0;
}

.tree-navigation-panel {
  overflow-y: auto;
  border-right: 1px solid var(--color-border);
  padding: var(--space-sm) 0;
  flex-shrink: 0;
}

.tree-navigation-resize-handle {
  width: 4px;
  cursor: col-resize;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface);
  transition: background var(--transition-fast);
}

.tree-navigation-resize-handle:hover,
.tree-navigation-resize-handle--active {
  background: var(--color-primary-hover-bg);
}

.tree-navigation-resize-handle-indicator {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border);
  transition: background var(--transition-fast);
}

.tree-navigation-resize-handle:hover .tree-navigation-resize-handle-indicator,
.tree-navigation-resize-handle--active .tree-navigation-resize-handle-indicator {
  background: var(--color-primary);
}

.tree-navigation-frame {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: var(--space-lg);
}

.tree-navigation-frame-hidden {
  display: none;
}

/* Validierungs-Indikator am Baum-Knoten */
.tree-node-error-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-error-text);
  flex-shrink: 0;
  margin-left: var(--space-xs);
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/shared/tree-navigation/TreeNavigation.css
git commit -m "feat: TreeNavigation CSS (Layout, ResizeHandle, FramePanel)"
```

---

### Task 3: TreeNavigation Komponente

**Files:**
- Create: `frontend/src/shared/tree-navigation/TreeNavigation.tsx`

- [ ] **Step 1: Komponente erstellen**

```typescript
// frontend/src/shared/tree-navigation/TreeNavigation.tsx
import { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import { TreeView, type TreeNode } from '../TreeView';
import type { TreeNavigationProps, TreeNodeDef } from './types';
import './TreeNavigation.css';

const MIN_WIDTH = 150;
const MAX_WIDTH = 400;

function findFirstFrameNode(nodes: TreeNodeDef[]): string | null {
  for (const node of nodes) {
    if (node.hasFrame !== false) return node.id;
    if (node.children) {
      const found = findFirstFrameNode(node.children);
      if (found) return found;
    }
  }
  return null;
}

function collectAllIds(nodes: TreeNodeDef[]): string[] {
  const ids: string[] = [];
  for (const node of nodes) {
    ids.push(node.id);
    if (node.children) ids.push(...collectAllIds(node.children));
  }
  return ids;
}

function collectFrameIds(nodes: TreeNodeDef[]): string[] {
  const ids: string[] = [];
  for (const node of nodes) {
    if (node.hasFrame !== false) ids.push(node.id);
    if (node.children) ids.push(...collectFrameIds(node.children));
  }
  return ids;
}

function toTreeNodes(defs: TreeNodeDef[]): TreeNode[] {
  return defs.map(def => ({
    id: def.id,
    label: def.label,
    children: def.children ? toTreeNodes(def.children) : undefined,
  }));
}

export function TreeNavigation<T>({
  nodes,
  frames,
  data,
  onChange,
  disabled = false,
  validationErrors,
  renderNode,
  defaultWidth = 220,
}: TreeNavigationProps<T>) {
  const [activeNodeId, setActiveNodeId] = useState<string>(
    () => findFirstFrameNode(nodes) ?? ''
  );
  const [panelWidth, setPanelWidth] = useState(defaultWidth);
  const [dragging, setDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Build a Set of node IDs that have hasFrame === false for quick lookup
  const noFrameIds = useMemo(() => {
    const set = new Set<string>();
    function walk(defs: TreeNodeDef[]) {
      for (const def of defs) {
        if (def.hasFrame === false) set.add(def.id);
        if (def.children) walk(def.children);
      }
    }
    walk(nodes);
    return set;
  }, [nodes]);

  // Convert TreeNodeDef[] to TreeNode[] for TreeView
  const treeData = useMemo(() => toTreeNodes(nodes), [nodes]);

  // All IDs expanded by default
  const defaultExpanded = useMemo(() => collectAllIds(nodes), [nodes]);

  // All frame IDs for rendering hidden frames
  const frameIds = useMemo(() => collectFrameIds(nodes), [nodes]);

  // Build a flat map of nodeId -> TreeNodeDef for renderNode lookup
  const nodeDefMap = useMemo(() => {
    const map = new Map<string, TreeNodeDef>();
    function walk(defs: TreeNodeDef[]) {
      for (const def of defs) {
        map.set(def.id, def);
        if (def.children) walk(def.children);
      }
    }
    walk(nodes);
    return map;
  }, [nodes]);

  const handleSelect = useCallback((node: TreeNode) => {
    if (!noFrameIds.has(node.id)) {
      setActiveNodeId(node.id);
    }
  }, [noFrameIds]);

  // Resize drag logic
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setDragging(true);
    const startX = e.clientX;
    const startWidth = panelWidth;

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta));
      setPanelWidth(newWidth);
    };

    const handleMouseUp = () => {
      setDragging(false);
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [panelWidth]);

  // Custom renderNode to show validation error dots
  const treeRenderNode = useCallback((node: TreeNode, _item: import('@headless-tree/core').ItemInstance<TreeNode>) => {
    const def = nodeDefMap.get(node.id);
    const hasErrors = !!(validationErrors && validationErrors[node.id]?.length);

    if (renderNode && def) {
      return renderNode(def, hasErrors);
    }

    return (
      <>
        <span className="tree-label">{node.label}</span>
        {hasErrors && <span className="tree-node-error-dot" />}
      </>
    );
  }, [nodeDefMap, validationErrors, renderNode]);

  return (
    <div className="tree-navigation" ref={containerRef}>
      <div className="tree-navigation-panel" style={{ width: panelWidth }}>
        <TreeView
          data={treeData}
          variant="light"
          defaultExpanded={defaultExpanded}
          selectOnClick
          selectedId={activeNodeId}
          onSelect={handleSelect}
          renderNode={treeRenderNode}
        />
      </div>

      <div
        className={`tree-navigation-resize-handle ${dragging ? 'tree-navigation-resize-handle--active' : ''}`}
        onMouseDown={handleMouseDown}
      >
        <div className="tree-navigation-resize-handle-indicator" />
      </div>

      <div className="tree-navigation-frame">
        {frameIds.map(id => {
          const FrameComponent = frames[id];
          if (!FrameComponent) return null;
          return (
            <div key={id} className={id !== activeNodeId ? 'tree-navigation-frame-hidden' : undefined}>
              <FrameComponent data={data} onChange={onChange} disabled={disabled} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/shared/tree-navigation/TreeNavigation.tsx
git commit -m "feat: TreeNavigation Komponente (TreePanel + ResizeHandle + FramePanel)"
```

---

### Task 4: DetailPage CSS anpassen

**Files:**
- Modify: `frontend/src/shared/detail-page/DetailPage.css`

Die `detail-page-content` hat `padding: var(--space-lg)`, aber `TreeNavigation` bringt eigenes Padding im FramePanel mit. Wir fuegen eine Modifier-Klasse hinzu.

- [ ] **Step 1: CSS erweitern**

Am Ende von `frontend/src/shared/detail-page/DetailPage.css` anfuegen:

```css
/* Kein Padding wenn TreeNavigation als Kind vorhanden */
.detail-page-content--no-padding {
  padding: 0;
  gap: 0;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/shared/detail-page/DetailPage.css
git commit -m "feat: DetailPage CSS Modifier fuer TreeNavigation (no-padding)"
```

---

### Task 5: StammdatenFrame extrahieren

**Files:**
- Create: `frontend/src/pages/business-partner/frames/StammdatenFrame.tsx`

Extrahiert aus `BusinessPartnerDetailPage.tsx` Zeilen 142-173 (Card mit Kurzbezeichnung, Name, Notizen).

- [ ] **Step 1: StammdatenFrame erstellen**

```typescript
// frontend/src/pages/business-partner/frames/StammdatenFrame.tsx
import { useCallback } from 'react';
import { Card } from '../../../shared/Card';
import { FormField } from '../../../shared/FormField';
import type { FrameProps } from '../../../shared/tree-navigation/types';
import type { BusinessPartnerDto } from '../../../api/types';

export function StammdatenFrame({ data, onChange, disabled }: FrameProps<BusinessPartnerDto>) {
  const updateField = useCallback((field: keyof BusinessPartnerDto, value: unknown) => {
    onChange({ ...data, [field]: value });
  }, [data, onChange]);

  return (
    <Card>
      <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
        <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
          <FormField label="Kurzbezeichnung">
            <input
              value={data.shortName}
              onChange={e => updateField('shortName', e.target.value)}
              disabled={disabled}
              maxLength={50}
            />
          </FormField>
          <div style={{ flex: 1 }}>
            <FormField label="Name">
              <input
                value={data.name}
                onChange={e => updateField('name', e.target.value)}
                disabled={disabled}
              />
            </FormField>
          </div>
        </div>
        <FormField label="Notizen">
          <textarea
            value={data.notes ?? ''}
            onChange={e => updateField('notes', e.target.value || null)}
            disabled={disabled}
            rows={3}
            style={{ resize: 'vertical' }}
          />
        </FormField>
      </div>
    </Card>
  );
}
```

- [ ] **Step 2: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/business-partner/frames/StammdatenFrame.tsx
git commit -m "feat: StammdatenFrame (extrahiert aus BusinessPartnerDetailPage)"
```

---

### Task 6: AnsprechpartnerFrame extrahieren

**Files:**
- Create: `frontend/src/pages/business-partner/frames/AnsprechpartnerFrame.tsx`

Extrahiert aus `BusinessPartnerDetailPage.tsx` Zeilen 175-198 (Ansprechpartner-Sektion mit ContactPersonCards).

- [ ] **Step 1: AnsprechpartnerFrame erstellen**

```typescript
// frontend/src/pages/business-partner/frames/AnsprechpartnerFrame.tsx
import { useCallback, useRef, useState } from 'react';
import { Button } from '../../../shared/Button';
import { ContactPersonCard } from '../ContactPersonCard';
import type { FrameProps } from '../../../shared/tree-navigation/types';
import type { BusinessPartnerDto, ContactPersonDto } from '../../../api/types';

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

export function AnsprechpartnerFrame({ data, onChange, disabled }: FrameProps<BusinessPartnerDto>) {
  const contactKeyCounter = useRef(0);
  const [contactKeys, setContactKeys] = useState<string[]>(
    () => data.contacts.map(() => `ck-${contactKeyCounter.current++}`)
  );

  const nextKey = () => `ck-${contactKeyCounter.current++}`;

  const updateContact = useCallback((index: number, updated: ContactPersonDto) => {
    const contacts = [...data.contacts];
    contacts[index] = updated;
    onChange({ ...data, contacts });
  }, [data, onChange]);

  const removeContact = useCallback((index: number) => {
    onChange({
      ...data,
      contacts: data.contacts.filter((_, i) => i !== index),
    });
    setContactKeys(prev => prev.filter((_, i) => i !== index));
  }, [data, onChange]);

  const addContact = useCallback(() => {
    onChange({
      ...data,
      contacts: [...data.contacts, emptyContact()],
    });
    setContactKeys(prev => [...prev, nextKey()]);
  }, [data, onChange]);

  // Sync contactKeys when data.contacts changes externally (e.g. after save/load)
  if (contactKeys.length !== data.contacts.length) {
    setContactKeys(data.contacts.map(() => nextKey()));
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
        <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)' }}>Ansprechpartner</h3>
        {!disabled && (
          <Button variant="ghost" onClick={addContact}>+ Ansprechpartner hinzufuegen</Button>
        )}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
        {data.contacts.map((contact, index) => (
          <ContactPersonCard
            key={contactKeys[index] ?? `fallback-${index}`}
            contact={contact}
            disabled={disabled}
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
  );
}
```

- [ ] **Step 2: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/business-partner/frames/AnsprechpartnerFrame.tsx
git commit -m "feat: AnsprechpartnerFrame (extrahiert aus BusinessPartnerDetailPage)"
```

---

### Task 7: BusinessPartnerDetailPage umbauen

**Files:**
- Modify: `frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx`

Ersetzt den bisherigen Inhalt (Card + Ansprechpartner-Sektion) durch `TreeNavigation`.

- [ ] **Step 1: BusinessPartnerDetailPage umschreiben**

Den gesamten Inhalt von `frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx` ersetzen:

```typescript
// frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx
import { useState, useCallback, useEffect, useRef } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { TreeNavigation } from '../../shared/tree-navigation/TreeNavigation';
import { StammdatenFrame } from './frames/StammdatenFrame';
import { AnsprechpartnerFrame } from './frames/AnsprechpartnerFrame';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchBusinessPartner, saveBusinessPartner, deleteBusinessPartner } from '../../api/client';
import type { BusinessPartnerDto } from '../../api/types';
import type { TreeNodeDef, FrameProps } from '../../shared/tree-navigation/types';

const treeNodes: TreeNodeDef[] = [
  {
    id: 'stammdaten',
    label: 'Stammdaten',
    children: [
      { id: 'ansprechpartner', label: 'Ansprechpartner' },
    ],
  },
];

const frames: Record<string, React.ComponentType<FrameProps<BusinessPartnerDto>>> = {
  stammdaten: StammdatenFrame,
  ansprechpartner: AnsprechpartnerFrame,
};

export function BusinessPartnerDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
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
  const [validationErrors, setValidationErrors] = useState<Record<string, string[]>>({});

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchBusinessPartner(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `GP: ${result.shortName}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const handleDataChange = useCallback((updated: BusinessPartnerDto) => {
    setData(updated);
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: Record<string, string[]> = {};

    if (!data.shortName.trim()) {
      errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Kurzbezeichnung'];
    }
    if (!data.name.trim()) {
      errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Name'];
    }

    data.contacts.forEach((c, i) => {
      if (!c.firstName.trim()) {
        errors['ansprechpartner'] = [...(errors['ansprechpartner'] || []), `Ansprechpartner ${i + 1}: Vorname`];
      }
      if (!c.lastName.trim()) {
        errors['ansprechpartner'] = [...(errors['ansprechpartner'] || []), `Ansprechpartner ${i + 1}: Nachname`];
      }
    });

    setValidationErrors(errors);

    const allErrors = Object.entries(errors).flatMap(([, msgs]) =>
      msgs.map(m => ({ field: '', message: m }))
    );
    return { valid: allErrors.length === 0, errors: allErrors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveBusinessPartner(data);
    setData(saved);
    updateTabLabel(tabId, `GP: ${saved.shortName}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
    setValidationErrors({});
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteBusinessPartner(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('business-partner-detail', { mode: 'new' });
  }, [openTab]);

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="business-partners"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
      onDelete={handleDelete}
      onNew={handleNew}
    >
      <TreeNavigation<BusinessPartnerDto>
        nodes={treeNodes}
        frames={frames}
        data={data}
        onChange={handleDataChange}
        disabled={mode === 'view'}
        validationErrors={validationErrors}
      />
    </DetailPage>
  );
}
```

- [ ] **Step 2: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx
git commit -m "feat: BusinessPartnerDetailPage auf TreeNavigation umgebaut"
```

---

### Task 8: DetailPage CSS Modifier anwenden

**Files:**
- Modify: `frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx`

Die `TreeNavigation` braucht die volle Hoehe ohne Padding von `detail-page-content`. Dafuer muss `DetailPage` die Modifier-Klasse unterstuetzen.

- [ ] **Step 1: DetailPage um contentClassName-Prop erweitern**

In `frontend/src/shared/detail-page/DetailPage.tsx`, den Props-Typ erweitern:

```typescript
interface DetailPageProps {
  pageKey: string;
  mode: DetailMode;
  tabId: string;
  dirty: boolean;
  validate: () => ValidationResult;
  onSave: () => Promise<void>;
  onSaveSuccess?: () => void;
  onDelete?: () => Promise<void>;
  onNew?: () => void;
  extraActions?: ReactNode;
  contentClassName?: string;
  children: ReactNode;
}
```

In der Destrukturierung `contentClassName` hinzufuegen und im JSX verwenden:

```tsx
// Aendere die content-div Zeile von:
<div className="detail-page-content">
// zu:
<div className={`detail-page-content ${contentClassName ?? ''}`}>
```

- [ ] **Step 2: BusinessPartnerDetailPage anpassen**

In `frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx`, dem `<DetailPage>`-Aufruf hinzufuegen:

```tsx
<DetailPage
  pageKey="business-partners"
  mode={mode}
  tabId={tabId}
  dirty={dirty}
  validate={validate}
  onSave={handleSave}
  onSaveSuccess={handleSaveSuccess}
  onDelete={handleDelete}
  onNew={handleNew}
  contentClassName="detail-page-content--no-padding"
>
```

- [ ] **Step 3: TypeScript-Check**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/shared/detail-page/DetailPage.tsx frontend/src/pages/business-partner/BusinessPartnerDetailPage.tsx
git commit -m "feat: DetailPage contentClassName-Prop, TreeNavigation nutzt no-padding"
```

---

### Task 9: Manueller Test + Feinschliff

**Files:** Keine neuen Dateien — visueller Test und ggf. CSS-Korrekturen.

- [ ] **Step 1: Dev-Server starten und testen**

Run: `cd frontend && npm run dev`

Testschritte:
1. Geschaeftspartner-Uebersicht oeffnen
2. Existierenden GP per Doppelklick oeffnen → Baum links mit "Stammdaten" (selektiert) und "Ansprechpartner" sichtbar
3. Auf "Ansprechpartner" klicken → Frame wechselt, Ansprechpartner-Liste sichtbar
4. Zurueck auf "Stammdaten" klicken → Stammdaten-Formular wieder da
5. Daten aendern → Dirty-Warnung bei Tab-Schliessen aktiv
6. Speichern → ein API-Call, alle Daten gespeichert
7. Neuen GP anlegen → Baum da, leere Felder
8. ResizeHandle ziehen → Baum-Breite aendert sich
9. Validierung: Name leer lassen + Speichern → roter Punkt am "Stammdaten"-Knoten

- [ ] **Step 2: Commit (falls CSS-Korrekturen noetig)**

```bash
git add -A
git commit -m "fix: TreeNavigation CSS-Feinschliff nach visuellem Test"
```

---

### Task 10: CLAUDE.md + DONE.md aktualisieren

**Files:**
- Modify: `frontend/CLAUDE.md`
- Modify: `DONE.md`

- [ ] **Step 1: Frontend CLAUDE.md ergaenzen**

In `frontend/CLAUDE.md` unter "Konvention: Templates" den Eintrag fuer TreeNavigation ergaenzen:

```markdown
- **Baum-Detailmasken** (Detailansicht mit Baum-Navigation links + Frame-Bereich rechts) nutzen `<TreeNavigation>` (`shared/tree-navigation/`) innerhalb von `<DetailPage>`. Definiert `TreeNodeDef[]` fuer den Baum und eine `frames`-Map (knotenId → Frame-Komponente). Alle Frames teilen ein DTO. Validierungsfehler werden per `validationErrors`-Prop als rote Punkte am Knoten angezeigt. ResizeHandle erlaubt variable Baum-Breite.
```

- [ ] **Step 2: DONE.md ergaenzen**

```markdown
## 2026-04-02 TreeNavigation-Komponente
- Wiederverwendbare `TreeNavigation` in `shared/tree-navigation/` (TreePanel + ResizeHandle + FramePanel)
- Geschaeftspartner-Detailmaske auf TreeNavigation umgebaut (Stammdaten + Ansprechpartner als Frames)
- DetailPage um `contentClassName`-Prop erweitert
```

- [ ] **Step 3: Commit**

```bash
git add frontend/CLAUDE.md DONE.md
git commit -m "docs: TreeNavigation in CLAUDE.md + DONE.md dokumentiert"
```
