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
