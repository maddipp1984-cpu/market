import { useState } from 'react';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { Chip, ChipGroup } from '../../shared/Chip';
import type { ContactPersonDto } from '../../api/types';
import './ContactPersonCard.css';

const CONTACT_FUNCTIONS = [
  { value: 'ABRECHNUNG', label: 'Abrechnung' },
  { value: 'BK_VERANTWORTLICHER', label: 'BK-Verantwortlicher' },
];

interface ContactPersonCardProps {
  contact: ContactPersonDto;
  disabled: boolean;
  onChange: (updated: ContactPersonDto) => void;
  onRemove: () => void;
}

export function ContactPersonCard({ contact, disabled, onChange, onRemove }: ContactPersonCardProps) {
  const [expanded, setExpanded] = useState(contact.id === null);

  const displayName = contact.firstName || contact.lastName
    ? `${contact.firstName} ${contact.lastName}`.trim()
    : 'Neuer Ansprechpartner';

  const update = (field: keyof ContactPersonDto, value: unknown) => {
    onChange({ ...contact, [field]: value });
  };

  const toggleFunction = (fn: string) => {
    const current = new Set(contact.functions);
    if (current.has(fn)) current.delete(fn);
    else current.add(fn);
    onChange({ ...contact, functions: Array.from(current) });
  };

  return (
    <Card>
      <div className="contact-card">
        <div className="contact-card-header" onClick={() => setExpanded(!expanded)}>
          <span className="contact-card-toggle">{expanded ? '\u25BC' : '\u25B6'}</span>
          <span className="contact-card-name">{displayName}</span>
          <ChipGroup>
            {contact.functions.map(fn => {
              const def = CONTACT_FUNCTIONS.find(f => f.value === fn);
              return <Chip key={fn} label={def?.label ?? fn} value="" />;
            })}
          </ChipGroup>
          {!disabled && (
            <button
              className="contact-card-remove"
              onClick={e => { e.stopPropagation(); onRemove(); }}
              title="Entfernen"
            >
              &times;
            </button>
          )}
        </div>
        {expanded && (
          <div className="contact-card-body">
            <div className="contact-card-row">
              <FormField label="Vorname">
                <input value={contact.firstName} onChange={e => update('firstName', e.target.value)} disabled={disabled} />
              </FormField>
              <FormField label="Nachname">
                <input value={contact.lastName} onChange={e => update('lastName', e.target.value)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-row">
              <FormField label="E-Mail">
                <input value={contact.email ?? ''} onChange={e => update('email', e.target.value || null)} disabled={disabled} />
              </FormField>
              <FormField label="Telefon">
                <input value={contact.phone ?? ''} onChange={e => update('phone', e.target.value || null)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-row">
              <FormField label="Strasse">
                <input value={contact.street ?? ''} onChange={e => update('street', e.target.value || null)} disabled={disabled} />
              </FormField>
              <FormField label="PLZ">
                <input value={contact.zipCode ?? ''} onChange={e => update('zipCode', e.target.value || null)} disabled={disabled} style={{ maxWidth: '100px' }} />
              </FormField>
              <FormField label="Ort">
                <input value={contact.city ?? ''} onChange={e => update('city', e.target.value || null)} disabled={disabled} />
              </FormField>
            </div>
            <div className="contact-card-functions">
              <span className="contact-card-functions-label">Funktionen:</span>
              {CONTACT_FUNCTIONS.map(fn => (
                <label key={fn.value} className="contact-card-checkbox">
                  <input
                    type="checkbox"
                    checked={contact.functions.includes(fn.value)}
                    onChange={() => toggleFunction(fn.value)}
                    disabled={disabled}
                  />
                  {fn.label}
                </label>
              ))}
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}
