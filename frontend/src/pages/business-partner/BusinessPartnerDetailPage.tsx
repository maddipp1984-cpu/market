import { useState, useCallback } from 'react';
import { DetailPage, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { useDetailPage } from '../../shared/detail-page/useDetailPage';
import { LoadingIndicator } from '../../shared/LoadingIndicator';
import { TreeNavigation } from '../../shared/tree-navigation/TreeNavigation';
import { StammdatenFrame } from './frames/StammdatenFrame';
import { AnsprechpartnerFrame } from './frames/AnsprechpartnerFrame';
import { useTabContext } from '../../shell/TabContext';
import { fetchBusinessPartner, saveBusinessPartner, deleteBusinessPartner } from '../../api/client';
import type { BusinessPartnerDto } from '../../api/types';
import type { TreeNodeDef, FrameProps } from '../../shared/tree-navigation/types';

const treeNodes: TreeNodeDef[] = [
  {
    id: 'stammdaten',
    label: 'Stammdaten',
    children: [
      { id: 'ansprechpartner', label: 'Ansprechpartner' },
    ],
  },
];

const frames: Record<string, React.ComponentType<FrameProps<BusinessPartnerDto>>> = {
  stammdaten: StammdatenFrame,
  ansprechpartner: AnsprechpartnerFrame,
};

export function BusinessPartnerDetailPage({ tabId }: { tabId: string }) {
  const { openTab } = useTabContext();
  const [validationErrors, setValidationErrors] = useState<Record<string, string[]>>({});

  const {
    mode, data, dirty, loading,
    setData, setDirty, handleSave, handleSaveSuccess, handleDelete,
  } = useDetailPage<BusinessPartnerDto>({
    tabId,
    defaultData: { id: null, shortName: '', name: '', notes: null, contacts: [] },
    fetchFn: fetchBusinessPartner,
    saveFn: saveBusinessPartner,
    deleteFn: deleteBusinessPartner,
    pageKey: 'business-partners',
    labelPrefix: 'GP',
    labelField: 'shortName' as keyof BusinessPartnerDto,
  });

  const handleDataChange = useCallback((updated: BusinessPartnerDto) => {
    setData(updated);
    setDirty(true);
    setValidationErrors({});
  }, [setData, setDirty]);

  const validate = useCallback((): ValidationResult => {
    const errors: Record<string, string[]> = {};

    if (!data.shortName.trim()) {
      errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Kurzbezeichnung'];
    }
    if (!data.name.trim()) {
      errors['stammdaten'] = [...(errors['stammdaten'] || []), 'Name'];
    }

    data.contacts.forEach((c, i) => {
      if (!c.firstName.trim()) {
        errors['ansprechpartner'] = [...(errors['ansprechpartner'] || []), `Ansprechpartner ${i + 1}: Vorname`];
      }
      if (!c.lastName.trim()) {
        errors['ansprechpartner'] = [...(errors['ansprechpartner'] || []), `Ansprechpartner ${i + 1}: Nachname`];
      }
    });

    setValidationErrors(errors);

    const allErrors = Object.entries(errors).flatMap(([, msgs]) =>
      msgs.map(m => ({ field: '', message: m }))
    );
    return { valid: allErrors.length === 0, errors: allErrors };
  }, [data]);

  const handleNew = useCallback(() => {
    openTab('business-partner-detail', { mode: 'new' });
  }, [openTab]);

  if (loading) return <LoadingIndicator />;

  return (
    <DetailPage
      pageKey="business-partners"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={() => {
        handleSaveSuccess();
        setValidationErrors({});
      }}
      onDelete={handleDelete}
      onNew={handleNew}
      contentClassName="detail-page-content--no-padding"
    >
      <TreeNavigation<BusinessPartnerDto>
        nodes={treeNodes}
        frames={frames}
        data={data}
        onChange={handleDataChange}
        disabled={mode === 'view'}
        validationErrors={validationErrors}
      />
    </DetailPage>
  );
}
