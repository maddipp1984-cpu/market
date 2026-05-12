import { useState } from 'react';
import './DateRangeDialog.css';

interface DateRangeDialogProps {
  onConfirm: (from: string, to: string) => void;
  onCancel: () => void;
}

export function DateRangeDialog({ onConfirm, onCancel }: DateRangeDialogProps) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const isValid = from && to && from <= to;

  return (
    <div className="date-range-backdrop" onClick={onCancel}>
      <div className="date-range-modal" onClick={e => e.stopPropagation()}>
        <h3 className="date-range-title">Zeitraum waehlen</h3>
        <div className="date-range-fields">
          <label className="date-range-field">
            <span className="date-range-field-label">Von</span>
            <input type="date" value={from} onChange={e => setFrom(e.target.value)} className="date-range-input" />
          </label>
          <label className="date-range-field">
            <span className="date-range-field-label">Bis</span>
            <input type="date" value={to} onChange={e => setTo(e.target.value)} className="date-range-input" />
          </label>
        </div>
        <div className="date-range-actions">
          <button onClick={onCancel} className="date-range-cancel-btn">Abbrechen</button>
          <button onClick={() => { if (from && to && from <= to) onConfirm(from, to); }}
            disabled={!isValid}
            className="date-range-confirm-btn">
            Oeffnen
          </button>
        </div>
      </div>
    </div>
  );
}
