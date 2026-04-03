import { useState, useCallback, useEffect, useMemo } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { TimeSeriesEditor } from '../../timeseries-editor/TimeSeriesEditor';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchIndex, saveIndex, deleteIndex } from '../../api/client';
import type { IndexDto } from '../../api/types';

const DIM_OPTIONS = [
  { value: 1, label: '15 Minuten' },
  { value: 2, label: '1 Stunde' },
  { value: 3, label: 'Tag' },
  { value: 4, label: 'Monat' },
  { value: 5, label: 'Jahr' },
];

export function IndexDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;
  const editorMode = params?.editorMode as 'view' | 'edit' | undefined;
  const dateFrom = params?.dateFrom as string | undefined;
  const dateTo = params?.dateTo as string | undefined;

  const [data, setData] = useState<IndexDto>({
    id: null,
    name: '',
    description: null,
    timeDim: 3,
    unitId: null,
    currencyId: null,
    tsId: null,
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchIndex(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Index: ${result.name}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const updateField = useCallback((field: keyof IndexDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (!data.timeDim) errors.push({ field: 'timeDim', message: 'Zeitdimension' });
    if (data.unitId == null && data.currencyId == null) {
      errors.push({ field: 'unitId', message: 'Einheit oder Waehrung muss gesetzt sein' });
    }
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveIndex(data);
    setData(saved);
    updateTabLabel(tabId, `Index: ${saved.name}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteIndex(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('index-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';
  const isExisting = data.id !== null;

  const editorStart = dateFrom ?? '2020-01-01';
  const editorEnd = dateTo ?? '2030-12-31';
  const tsIds = useMemo(() => data.tsId ? [data.tsId] : [], [data.tsId]);

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="indices"
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
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.name}
                  onChange={e => updateField('name', e.target.value)}
                  disabled={isDisabled}
                />
              </FormField>
            </div>
            <FormField label="Zeitdimension">
              <select
                value={data.timeDim ?? ''}
                onChange={e => updateField('timeDim', Number(e.target.value))}
                disabled={isDisabled || isExisting}
              >
                <option value="">-- Waehlen --</option>
                {DIM_OPTIONS.map(o => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            </FormField>
          </div>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <FormField label="Einheit (Unit-ID)">
              <input
                type="number"
                value={data.unitId ?? ''}
                onChange={e => updateField('unitId', e.target.value ? Number(e.target.value) : null)}
                disabled={isDisabled || isExisting}
              />
            </FormField>
            <FormField label="Waehrung (Currency-ID)">
              <input
                type="number"
                value={data.currencyId ?? ''}
                onChange={e => updateField('currencyId', e.target.value ? Number(e.target.value) : null)}
                disabled={isDisabled || isExisting}
              />
            </FormField>
          </div>
          <FormField label="Beschreibung">
            <input
              value={data.description ?? ''}
              onChange={e => updateField('description', e.target.value || null)}
              disabled={isDisabled}
            />
          </FormField>
        </div>
      </Card>

      {isExisting && data.tsId && editorMode && (
        <div style={{ marginTop: 'var(--space-md)' }}>
          <TimeSeriesEditor
            tsIds={tsIds}
            start={editorStart}
            end={editorEnd}
            readOnly={editorMode === 'view'}
          />
        </div>
      )}
    </DetailPage>
  );
}
