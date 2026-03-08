import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchCurrency, saveCurrency, deleteCurrency } from '../../api/client';
import type { CurrencyDto } from '../../api/types';

export function CurrencyDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<CurrencyDto>({
    id: null,
    isoCode: '',
    description: '',
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchCurrency(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Waehrung: ${result.isoCode}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const updateField = useCallback((field: keyof CurrencyDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.isoCode.trim()) errors.push({ field: 'isoCode', message: 'ISO-Code' });
    if (data.isoCode.trim().length > 3) errors.push({ field: 'isoCode', message: 'ISO-Code (max. 3 Zeichen)' });
    if (!data.description.trim()) errors.push({ field: 'description', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveCurrency(data);
    setData(saved);
    updateTabLabel(tabId, `Waehrung: ${saved.isoCode}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteCurrency(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('currency-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="currencies"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
      onDelete={handleDelete}
      onNew={handleNew}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <FormField label="ISO-Code">
              <input
                value={data.isoCode}
                onChange={e => updateField('isoCode', e.target.value.toUpperCase())}
                disabled={isDisabled}
                maxLength={3}
                style={{ width: '80px' }}
              />
            </FormField>
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.description}
                  onChange={e => updateField('description', e.target.value)}
                  disabled={isDisabled}
                />
              </FormField>
            </div>
          </div>
        </div>
      </Card>
    </DetailPage>
  );
}
