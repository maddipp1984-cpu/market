import { Button } from './Button';
import './ConfirmDialog.css';

interface ConfirmDialogProps {
  title?: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  title = 'Wirklich loeschen?',
  message = 'Dieser Vorgang kann nicht rueckgaengig gemacht werden.',
  confirmLabel = 'Ja, loeschen',
  cancelLabel = 'Abbrechen',
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <div className="confirm-dialog-backdrop" onClick={onCancel}>
      <div className="confirm-dialog-modal" onClick={e => e.stopPropagation()}>
        <h3 className="confirm-dialog-title">{title}</h3>
        <p className="confirm-dialog-message">{message}</p>
        <div className="confirm-dialog-actions">
          <Button variant="danger" onClick={onConfirm} disabled={loading}>
            {loading ? 'Loeschen...' : confirmLabel}
          </Button>
          <Button variant="ghost" onClick={onCancel} disabled={loading}>
            {cancelLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
