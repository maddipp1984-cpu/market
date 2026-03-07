import { useState, useCallback, useEffect, type ReactNode } from 'react';
import { Button } from '../Button';
import { useTabContext } from '../../shell/TabContext';
import { useAuth } from '../../auth/AuthContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { iconPlus, iconSave, iconTrash } from '../overview-page/icons';
import './DetailPage.css';

const iconSaveClose = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M16 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v6" />
    <polyline points="14 21 14 13 7 13 7 21" />
    <polyline points="7 3 7 8 13 8" />
    <path d="M18 18l4 4m0-4l-4 4" />
  </svg>
);

export interface ValidationResult {
  valid: boolean;
  errors: { field: string; message: string }[];
}

export type DetailMode = 'view' | 'edit' | 'new';

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
  children: ReactNode;
}

export function DetailPage({
  pageKey,
  mode,
  tabId,
  dirty,
  validate,
  onSave,
  onSaveSuccess,
  onDelete,
  onNew,
  extraActions,
  children,
}: DetailPageProps) {
  const { closeTab, registerCloseGuard, markOverviewStale } = useTabContext();
  const { canWrite, canDelete } = useAuth();
  const { showMessage } = useMessageBar();
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const isEditable = mode === 'edit' || mode === 'new';
  const hasWritePermission = canWrite(pageKey);
  const hasDeletePermission = canDelete(pageKey);

  // Close-Guard: warn on unsaved changes
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
      onSaveSuccess?.();
      markOverviewStale(pageKey);
      showMessage('Gespeichert', 'success');
      if (andClose) closeTab(tabId);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    } finally {
      setSaving(false);
    }
  }, [validate, onSave, onSaveSuccess, showMessage, closeTab, tabId]);

  const handleDelete = useCallback(async () => {
    if (!onDelete) return;
    setDeleting(true);
    try {
      await onDelete();
      markOverviewStale(pageKey);
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
          {onNew && hasWritePermission && (
            <Button variant="ghost" icon onClick={onNew} title="Neu" aria-label="Neu">
              {iconPlus}
            </Button>
          )}
          {isEditable && hasWritePermission && (
            <>
              <Button
                variant="primary"
                icon
                onClick={() => handleSave(false)}
                disabled={saving}
                title="Speichern"
                aria-label="Speichern"
              >
                {iconSave}
              </Button>
              <Button
                variant="ghost"
                icon
                onClick={() => handleSave(true)}
                disabled={saving}
                title="Speichern & Schliessen"
                aria-label="Speichern & Schliessen"
              >
                {iconSaveClose}
              </Button>
            </>
          )}
          {mode === 'edit' && hasDeletePermission && onDelete && (
            <Button
              variant="ghost"
              icon
              onClick={() => setConfirmDelete(true)}
              disabled={deleting}
              title="Loeschen"
              aria-label="Loeschen"
              className="detail-page-delete-btn"
            >
              {iconTrash}
            </Button>
          )}
          {extraActions}
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
              <Button variant="ghost" onClick={handleDelete} disabled={deleting} className="detail-page-delete-btn">
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
