import { useCallback } from 'react';
import { DetailPage, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { useDetailPage } from '../../shared/detail-page/useDetailPage';
import { LoadingIndicator } from '../../shared/LoadingIndicator';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { fetchSeriesType, saveSeriesType, deleteSeriesType } from '../../api/client';
import type { SeriesTypeDto } from '../../api/types';
import '../../shared/FormLayout.css';

const CATEGORIES = [
  { value: 1, label: 'Finanziell' },
  { value: 2, label: 'Physikalisch' },
];

export function ReihenartDetailPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const {
    mode, data, dirty, loading,
    updateField, handleSave, handleSaveSuccess, handleDelete,
  } = useDetailPage<SeriesTypeDto>({
    tabId,
    defaultData: { id: null, code: '', name: '', category: 1 },
    fetchFn: fetchSeriesType,
    saveFn: saveSeriesType,
    deleteFn: deleteSeriesType,
    pageKey: 'series-types',
    labelPrefix: 'Reihenart',
    labelField: 'code' as keyof SeriesTypeDto,
  });

  const handleNew = useCallback(() => {
    openTab('reihenart-detail', { mode: 'new' });
  }, [openTab]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.code.trim()) errors.push({ field: 'code', message: 'Kuerzel' });
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const isDisabled = mode === 'view';

  if (loading) return <LoadingIndicator />;

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
        <div className="form-section">
          <div className="form-row">
            <FormField label="Kuerzel">
              <input
                value={data.code}
                onChange={e => updateField('code', e.target.value)}
                disabled={isDisabled}
              />
            </FormField>
            <div className="form-row-grow">
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
