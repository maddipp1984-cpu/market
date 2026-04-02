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
