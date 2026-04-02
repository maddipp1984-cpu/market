import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchSeriesType, saveSeriesType, deleteSeriesType } from '../../api/client';
import type { SeriesTypeDto } from '../../api/types';

const CATEGORIES = [
  { value: 1, label: 'Finanziell' },
  { value: 2, label: 'Physikalisch' },
];

export function ReihenartDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<SeriesTypeDto>({
    id: null,
    code: '',
    name: '',
    category: 1,
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchSeriesType(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Reihenart: ${result.code}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel, showMessage]);

  const updateField = useCallback((field: keyof SeriesTypeDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.code.trim()) errors.push({ field: 'code', message: 'Kuerzel' });
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveSeriesType(data);
    setData(saved);
    updateTabLabel(tabId, `Reihenart: ${saved.code}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteSeriesType(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('reihenart-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="series-types"
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
            <FormField label="Kuerzel">
              <input
                value={data.code}
                onChange={e => updateField('code', e.target.value)}
                disabled={isDisabled}
              />
            </FormField>
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.name}
                  onChange={e => updateField('name', e.target.value)}
                  disabled={isDisabled}
                />
              </FormField>
            </div>
            <FormField label="Kategorie">
              <select
                value={data.category}
                onChange={e => updateField('category', Number(e.target.value))}
                disabled={isDisabled}
              >
                {CATEGORIES.map(c => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </FormField>
          </div>
        </div>
      </Card>
    </DetailPage>
  );
}
