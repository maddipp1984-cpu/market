import { useCallback } from 'react';
import { DetailPage, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { useDetailPage } from '../../shared/detail-page/useDetailPage';
import { LoadingIndicator } from '../../shared/LoadingIndicator';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { fetchCommodityGroup, saveCommodityGroup, deleteCommodityGroup } from '../../api/client';
import type { CommodityGroupDto } from '../../api/types';
import '../../shared/FormLayout.css';

export function WarengruppeDetailPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();

  const {
    mode, data, dirty, loading,
    updateField, handleSave, handleSaveSuccess, handleDelete,
  } = useDetailPage<CommodityGroupDto>({
    tabId,
    defaultData: { id: null, name: '' },
    fetchFn: fetchCommodityGroup,
    saveFn: saveCommodityGroup,
    deleteFn: deleteCommodityGroup,
    pageKey: 'commodity-groups',
    labelPrefix: 'Warengruppe',
  });

  const handleNew = useCallback(() => {
    openTab('warengruppe-detail', { mode: 'new' });
  }, [openTab]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const isDisabled = mode === 'view';

  if (loading) return <LoadingIndicator />;

  return (
    <DetailPage
      pageKey="commodity-groups"
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
          <FormField label="Name">
            <input
              value={data.name}
              onChange={e => updateField('name', e.target.value)}
              disabled={isDisabled}
            />
          </FormField>
        </div>
      </Card>
    </DetailPage>
  );
}
