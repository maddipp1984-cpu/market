# DetailPage-Template Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Shared `<DetailPage>` Komponente als Rahmen fuer alle Detailmasken — Toolbar, Modi (view/edit/new), Validierung, Dirty-Warnung, Berechtigungen.

**Architecture:** Tab-System wird um Parameter erweitert (mode, entityId). Neue `<DetailPage>` Shared-Komponente stellt Toolbar und Rahmenlogik bereit. Der Inhalt (Felder, Layout) bleibt komplett in der Hand der jeweiligen Seite. Dirty-Warnung wird ueber einen Close-Guard im TabContext realisiert.

**Tech Stack:** React 18, TypeScript, bestehende Shared Components (Button, Card, StatusMessage), TabContext, AuthContext, MessageBarContext

**Design-Dokument:** `docs/plans/2026-03-07-detailpage-template-design.md`

---

### Task 1: Tab-System um Parameter erweitern

**Files:**
- Modify: `frontend/src/shell/TabContext.tsx`
- Modify: `frontend/src/shell/AppShell.tsx`
- Modify: `frontend/src/shell/tabTypes.tsx`

**Step 1: Tab-Interface um params erweitern**

In `TabContext.tsx` das `Tab`-Interface erweitern:

```ts
export interface Tab {
  id: string;
  type: string;
  label: string;
  icon: ReactNode;
  params?: Record<string, unknown>;  // NEU: mode, entityId etc.
}
```

**Step 2: openTab um params-Parameter erweitern**

`openTab` Signatur und `TabContextValue` anpassen:

```ts
interface TabContextValue {
  // ... bestehend ...
  openTab: (type: string, params?: Record<string, unknown>) => void;
  getTabParams: (tabId: string) => Record<string, unknown> | undefined;
}
```

Implementierung in `TabProvider`:

```ts
const openTab = useCallback((type: string, params?: Record<string, unknown>) => {
  const tabType = getTabType(type);
  if (!tabType) return;

  const newId = `tab-${++tabCounterRef.current}`;
  const newTab: Tab = { id: newId, type, label: tabType.label, icon: tabType.icon, params };

  setTabs(prev => {
    if (tabType.singleton) {
      const existing = prev.find(t => t.type === type);
      if (existing) {
        setActiveTabId(existing.id);
        return prev;
      }
    }
    setActiveTabId(newId);
    return [...prev, newTab];
  });
}, []);

const getTabParams = useCallback((tabId: string): Record<string, unknown> | undefined => {
  return tabs.find(t => t.id === tabId)?.params;
}, [tabs]);
```

`getTabParams` im Provider-Value ergaenzen.

**Step 3: TypeScript pruefung**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS (keine bestehenden Aufrufe von openTab brechen, da params optional ist)

**Step 4: Commit**

```bash
git add frontend/src/shell/TabContext.tsx
git commit -m "feat: Tab-System um optionale params erweitert"
```

---

### Task 2: Close-Guard im TabContext fuer Dirty-Warnung

**Files:**
- Modify: `frontend/src/shell/TabContext.tsx`

**Step 1: Close-Guard Registry hinzufuegen**

Im `TabProvider` einen Ref fuer Close-Guards anlegen:

```ts
const closeGuardsRef = useRef<Map<string, () => boolean>>(new Map());

const registerCloseGuard = useCallback((tabId: string, guard: () => boolean) => {
  closeGuardsRef.current.set(tabId, guard);
  return () => { closeGuardsRef.current.delete(tabId); };
}, []);
```

Der Guard gibt `true` zurueck wenn das Schliessen erlaubt ist, `false` wenn nicht.

**Step 2: closeTab mit Guard-Pruefung**

```ts
const closeTab = useCallback((id: string) => {
  const guard = closeGuardsRef.current.get(id);
  if (guard && !guard()) {
    return; // Guard hat das Schliessen verhindert
  }

  setTabs(prev => {
    if (prev.length <= 1) return prev;
    const idx = prev.findIndex(t => t.id === id);
    const next = prev.filter(t => t.id !== id);
    setActiveTabId(currentActive => {
      if (currentActive !== id) return currentActive;
      const newIdx = Math.min(idx, next.length - 1);
      return next[newIdx]?.id ?? null;
    });
    // Guard aufraeumen
    closeGuardsRef.current.delete(id);
    return next;
  });
}, []);
```

**Step 3: Interface erweitern**

```ts
interface TabContextValue {
  // ... bestehend ...
  registerCloseGuard: (tabId: string, guard: () => boolean) => () => void;
}
```

Im Provider-Value ergaenzen.

**Step 4: TypeScript pruefung**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/shell/TabContext.tsx
git commit -m "feat: Close-Guard Registry im TabContext fuer Dirty-Warnung"
```

---

### Task 3: DetailPage Komponente erstellen

**Files:**
- Create: `frontend/src/shared/detail-page/DetailPage.tsx`
- Create: `frontend/src/shared/detail-page/DetailPage.css`

**Step 1: Types und Interface definieren**

In `DetailPage.tsx`:

```ts
import { useState, useCallback, useEffect, type ReactNode } from 'react';
import { Button } from '../Button';
import { useTabContext } from '../../shell/TabContext';
import { useAuth } from '../../auth/AuthContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import './DetailPage.css';

export interface ValidationResult {
  valid: boolean;
  errors: { field: string; message: string }[];
}

export type DetailMode = 'view' | 'edit' | 'new';

interface DetailPageProps {
  pageKey: string;
  mode: DetailMode;
  tabId: string;
  title: string;
  dirty: boolean;
  validate: () => ValidationResult;
  onSave: () => Promise<void>;
  onDelete?: () => Promise<void>;
  onNew?: () => void;
  extraActions?: ReactNode;
  children: ReactNode;
}
```

**Step 2: Komponente implementieren**

```tsx
export function DetailPage({
  pageKey,
  mode,
  tabId,
  title,
  dirty,
  validate,
  onSave,
  onDelete,
  onNew,
  extraActions,
  children,
}: DetailPageProps) {
  const { closeTab, registerCloseGuard } = useTabContext();
  const { canWrite, canDelete } = useAuth();
  const { showMessage } = useMessageBar();
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const isEditable = mode === 'edit' || mode === 'new';
  const hasWritePermission = canWrite(pageKey);
  const hasDeletePermission = canDelete(pageKey);

  // Close-Guard registrieren: warnt bei ungespeicherten Aenderungen
  useEffect(() => {
    const unregister = registerCloseGuard(tabId, () => {
      if (!dirty) return true;
      return window.confirm('Ungespeicherte Aenderungen verwerfen?');
    });
    return unregister;
  }, [tabId, dirty, registerCloseGuard]);

  const handleSave = useCallback(async (andClose: boolean) => {
    const result = validate();
    if (!result.valid) {
      const fieldNames = result.errors.map(e => e.message).join(', ');
      showMessage(`Bitte Pflichtfelder ausfuellen: ${fieldNames}`, 'error');
      return;
    }

    setSaving(true);
    try {
      await onSave();
      showMessage('Gespeichert', 'success');
      if (andClose) closeTab(tabId);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    } finally {
      setSaving(false);
    }
  }, [validate, onSave, showMessage, closeTab, tabId]);

  const handleDelete = useCallback(async () => {
    if (!onDelete) return;
    setDeleting(true);
    try {
      await onDelete();
      showMessage('Geloescht', 'success');
      closeTab(tabId);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    } finally {
      setDeleting(false);
      setConfirmDelete(false);
    }
  }, [onDelete, showMessage, closeTab, tabId]);

  return (
    <div className="detail-page">
      <div className="detail-page-toolbar">
        <h2 className="detail-page-title">{title}</h2>
        <div className="detail-page-actions">
          {onNew && hasWritePermission && (
            <Button variant="ghost" onClick={onNew} title="Neu">Neu</Button>
          )}
          {isEditable && (
            <>
              <Button
                variant="primary"
                onClick={() => handleSave(false)}
                disabled={saving}
              >
                {saving ? 'Speichern...' : 'Speichern'}
              </Button>
              <Button
                variant="ghost"
                onClick={() => handleSave(true)}
                disabled={saving}
              >
                Speichern & Schliessen
              </Button>
            </>
          )}
          {mode === 'edit' && hasDeletePermission && onDelete && (
            <Button
              variant="ghost"
              onClick={() => setConfirmDelete(true)}
              disabled={deleting}
              className="detail-page-delete-btn"
            >
              Loeschen
            </Button>
          )}
          {extraActions}
        </div>
      </div>

      <div className="detail-page-content">
        {children}
      </div>

      {confirmDelete && (
        <div className="detail-page-modal-backdrop" onClick={() => setConfirmDelete(false)}>
          <div className="detail-page-modal" onClick={e => e.stopPropagation()}>
            <h3>Wirklich loeschen?</h3>
            <p>Dieser Vorgang kann nicht rueckgaengig gemacht werden.</p>
            <div className="detail-page-modal-actions">
              <Button variant="primary" onClick={handleDelete} disabled={deleting}>
                {deleting ? 'Loeschen...' : 'Ja, loeschen'}
              </Button>
              <Button variant="ghost" onClick={() => setConfirmDelete(false)}>Abbrechen</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

**Step 3: CSS erstellen**

`DetailPage.css`:

```css
.detail-page {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: var(--space-lg);
  gap: var(--space-md);
  overflow: auto;
}

.detail-page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-shrink: 0;
}

.detail-page-title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.detail-page-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.detail-page-delete-btn {
  color: var(--color-error, #ef4444);
}

.detail-page-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

/* Loeschen-Bestaetigungsdialog */
.detail-page-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.detail-page-modal {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-lg);
  min-width: 360px;
  box-shadow: var(--shadow-lg, 0 8px 24px rgba(0, 0, 0, 0.2));
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.detail-page-modal h3 {
  margin: 0;
}

.detail-page-modal p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.detail-page-modal-actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}
```

**Step 4: TypeScript pruefung**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/shared/detail-page/
git commit -m "feat: DetailPage Shared-Komponente (Toolbar, Modi, Validierung, Dirty-Guard)"
```

---

### Task 4: Smoke-Test — AdminUsersPage auf DetailPage umbauen (Proof of Concept)

**Zweck:** Verifizieren, dass das Template funktioniert, indem eine einfache Dummy-Detailseite fuer einen Benutzer erstellt wird. Kein vollstaendiger Umbau, nur ein Proof of Concept.

**Files:**
- Create: `frontend/src/admin/AdminUserDetailPage.tsx`
- Modify: `frontend/src/shell/tabTypes.tsx` (neuer Tab-Typ `admin-user-detail`)

**Step 1: Minimale Detailseite erstellen**

```tsx
import { useState, useCallback } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../shared/detail-page/DetailPage';
import { Card } from '../shared/Card';
import { FormField } from '../shared/FormField';
import { useTabContext } from '../shell/TabContext';

export function AdminUserDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab } = useTabContext();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const userId = params?.entityId as string | undefined;

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [dirty, setDirty] = useState(false);

  // TODO: Bei mode edit/view Daten laden per userId

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!username.trim()) errors.push({ field: 'username', message: 'Username' });
    return { valid: errors.length === 0, errors };
  }, [username]);

  const handleSave = useCallback(async () => {
    // TODO: API-Call
    console.log('Save:', { username, email, userId });
  }, [username, email, userId]);

  const handleNew = useCallback(() => {
    openTab('admin-user-detail', { mode: 'new' });
  }, [openTab]);

  return (
    <DetailPage
      pageKey="admin-users"
      mode={mode}
      tabId={tabId}
      title={mode === 'new' ? 'Neuer Benutzer' : `Benutzer: ${username || '...'}`}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onNew={handleNew}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <FormField label="Username">
            <input
              value={username}
              onChange={e => { setUsername(e.target.value); setDirty(true); }}
              disabled={mode === 'view'}
            />
          </FormField>
          <FormField label="E-Mail">
            <input
              value={email}
              onChange={e => { setEmail(e.target.value); setDirty(true); }}
              disabled={mode === 'view'}
            />
          </FormField>
        </div>
      </Card>
    </DetailPage>
  );
}
```

**Step 2: Tab-Typ registrieren**

In `tabTypes.tsx` Import und Eintrag hinzufuegen:

```ts
import { AdminUserDetailPage } from '../admin/AdminUserDetailPage';

// In tabTypes Array:
{ type: 'admin-user-detail', label: 'Benutzer', icon: iconSettings, component: AdminUserDetailPage },
```

**Step 3: Manueller Test**

1. Backend + Frontend starten
2. In der Browser-Console oder ueber einen temporaeren Button: `openTab('admin-user-detail', { mode: 'new' })` aufrufen
3. Pruefen: Toolbar zeigt Neu/Speichern/Speichern&Schliessen
4. Username leer lassen, Speichern klicken → MessageBar zeigt Fehler
5. Username eingeben, Speichern klicken → MessageBar zeigt "Gespeichert"
6. Etwas aendern, Tab schliessen → Dirty-Dialog erscheint

**Step 4: TypeScript pruefung**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/admin/AdminUserDetailPage.tsx frontend/src/shell/tabTypes.tsx
git commit -m "feat: AdminUserDetailPage Proof-of-Concept mit DetailPage-Template"
```

---

### Task 5: OverviewPage um Zeilen-Aktionen erweitern (Vorbereitung)

**Files:**
- Modify: `frontend/src/shared/overview-page/OverviewPage.tsx`
- Modify: `frontend/src/shared/overview-page/VirtualTable.tsx`
- Modify: `frontend/src/shared/overview-page/VirtualTable.css`

**Step 1: onRowDoubleClick Prop in OverviewPage**

OverviewPage erhaelt einen optionalen Callback:

```ts
interface OverviewPageProps {
  // ... bestehend ...
  onRowDoubleClick?: (row: Record<string, unknown>) => void;
}
```

Wird an VirtualTable durchgereicht.

**Step 2: VirtualTable um Row-Events erweitern**

```ts
interface VirtualTableProps<T extends Record<string, any>> {
  // ... bestehend ...
  onRowDoubleClick?: (row: T) => void;
}
```

Im `<tr>` der tbody:

```tsx
<tr
  key={row.id}
  className={i % 2 !== 0 ? 'odd' : undefined}
  onDoubleClick={() => onRowDoubleClick?.(row.original)}
  style={onRowDoubleClick ? { cursor: 'pointer' } : undefined}
>
```

**Step 3: TypeScript pruefung**

Run: `cd frontend && node node_modules/typescript/lib/tsc.js --noEmit`
Expected: PASS

**Step 4: Commit**

```bash
git add frontend/src/shared/overview-page/OverviewPage.tsx frontend/src/shared/overview-page/VirtualTable.tsx
git commit -m "feat: OverviewPage/VirtualTable um onRowDoubleClick erweitert"
```

---

### Task 6: CLAUDE.md und Dokumentation aktualisieren

**Files:**
- Modify: `frontend/CLAUDE.md`
- Modify: `DONE.md`

**Step 1: Frontend CLAUDE.md aktualisieren**

Unter "Konvention: Templates" den DetailPage-Eintrag ergaenzen:

```
- **Detailmasken** (Einzelobjekt anzeigen/bearbeiten/neu) nutzen `<DetailPage>` (`shared/detail-page/`) — liefert Standard-Toolbar (Neu/Speichern/Speichern&Schliessen/Loeschen), Modi (view/edit/new), Validierung vor Speichern, Dirty-Warnung bei Tab-Schliessen und Berechtigungspruefung automatisch. Modus wird beim Tab-Oeffnen ueber params festgelegt. Jede Detailseite implementiert validate() als Pflicht-Prop.
```

In der Projektstruktur ergaenzen:

```
    shared/
      detail-page/
        DetailPage.tsx + DetailPage.css  -- Template fuer Detailmasken (Toolbar, Modi, Validierung)
```

**Step 2: DONE.md Eintrag**

```
## 2026-03-07 — DetailPage-Template + Tab-Parameter
- **DetailPage** (`shared/detail-page/`): Shared-Komponente fuer Detailmasken mit Standard-Toolbar, Modi (view/edit/new), Validierung, Dirty-Warnung, Berechtigungspruefung
- **Tab-System erweitert**: `openTab(type, params)` uebergibt Parameter (mode, entityId), `getTabParams(tabId)` liest sie
- **Close-Guard**: `registerCloseGuard()` ermoeglicht Dirty-Warnung beim Tab-Schliessen
- **OverviewPage**: `onRowDoubleClick` Prop fuer Navigation zu Detailmasken
- **AdminUserDetailPage**: Proof-of-Concept Detailseite mit DetailPage-Template
```

**Step 3: Commit**

```bash
git add frontend/CLAUDE.md DONE.md
git commit -m "docs: DetailPage-Template dokumentiert"
```
