import { useCallback, useMemo } from 'react';
import { DetailPage, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { useDetailPage } from '../../shared/detail-page/useDetailPage';
import { LoadingIndicator } from '../../shared/LoadingIndicator';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { TimeSeriesEditor } from '../../timeseries-editor/TimeSeriesEditor';
import { useTabContext } from '../../shell/TabContext';
import { fetchIndex, saveIndex, deleteIndex } from '../../api/client';
import type { IndexDto } from '../../api/types';
import '../../shared/FormLayout.css';

const DIM_OPTIONS = [
  { value: 1, label: '15 Minuten' },
  { value: 2, label: '1 Stunde' },
  { value: 3, label: 'Tag' },
  { value: 4, label: 'Monat' },
  { value: 5, label: 'Jahr' },
];

export function IndexDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab } = useTabContext();
  const params = getTabParams(tabId);
  const editorMode = params?.editorMode as 'view' | 'edit' | undefined;
  const dateFrom = params?.dateFrom as string | undefined;
  const dateTo = params?.dateTo as string | undefined;

  const {
    mode, data, dirty, loading,
    updateField, handleSave, handleSaveSuccess, handleDelete,
  } = useDetailPage<IndexDto>({
    tabId,
    defaultData: { id: null, name: '', description: null, timeDim: 3, unitId: null, currencyId: null, tsId: null },
    fetchFn: fetchIndex,
    saveFn: saveIndex,
    deleteFn: deleteIndex,
    pageKey: 'indices',
    labelPrefix: 'Index',
  });

  const handleNew = useCallback(() => {
    openTab('index-detail', { mode: 'new' });
  }, [openTab]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (!data.timeDim) errors.push({ field: 'timeDim', message: 'Zeitdimension' });
    if (data.unitId == null && data.currencyId == null) {
      errors.push({ field: 'unitId', message: 'Einheit oder Waehrung muss gesetzt sein' });
    }
    return { valid: errors.length === 0, errors };
  }, [data]);

  const isDisabled = mode === 'view';
  const isExisting = data.id !== null;

  const editorStart = dateFrom ?? '2020-01-01';
  const editorEnd = dateTo ?? '2030-12-31';
  const tsIds = useMemo(() => data.tsId ? [data.tsId] : [], [data.tsId]);

  if (loading) return <LoadingIndicator />;

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
        <div className="form-section">
          <div className="form-row">
            <div className="form-row-grow">
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
          <div className="form-row">
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
        <div className="form-card-gap">
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
