import { useCallback } from 'react';
import { DetailPage, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { useDetailPage } from '../../shared/detail-page/useDetailPage';
import { LoadingIndicator } from '../../shared/LoadingIndicator';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { fetchCurrency, saveCurrency, deleteCurrency } from '../../api/client';
import type { CurrencyDto } from '../../api/types';
import '../../shared/FormLayout.css';

export function CurrencyDetailPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const {
    mode, data, dirty, loading,
    updateField, handleSave, handleSaveSuccess, handleDelete,
  } = useDetailPage<CurrencyDto>({
    tabId,
    defaultData: { id: null, isoCode: '', description: '' },
    fetchFn: fetchCurrency,
    saveFn: saveCurrency,
    deleteFn: deleteCurrency,
    pageKey: 'currencies',
    labelPrefix: 'Waehrung',
    labelField: 'isoCode' as keyof CurrencyDto,
  });

  const handleNew = useCallback(() => {
    openTab('currency-detail', { mode: 'new' });
  }, [openTab]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.isoCode.trim()) errors.push({ field: 'isoCode', message: 'ISO-Code' });
    if (data.isoCode.trim().length > 3) errors.push({ field: 'isoCode', message: 'ISO-Code (max. 3 Zeichen)' });
    if (!data.description.trim()) errors.push({ field: 'description', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const isDisabled = mode === 'view';

  if (loading) return <LoadingIndicator />;

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
        <div className="form-section">
          <div className="form-row">
            <FormField label="ISO-Code">
              <input
                value={data.isoCode}
                onChange={e => updateField('isoCode', e.target.value.toUpperCase())}
                disabled={isDisabled}
                maxLength={3}
                style={{ width: '80px' }}
              />
            </FormField>
            <div className="form-row-grow">
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
