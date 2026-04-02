import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { TreeNavigation } from '../../shared/tree-navigation/TreeNavigation';
import { StammdatenFrame } from './frames/StammdatenFrame';
import { AnsprechpartnerFrame } from './frames/AnsprechpartnerFrame';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
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
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<BusinessPartnerDto>({
    id: null,
    shortName: '',
    name: '',
    notes: null,
    contacts: [],
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');
  const [validationErrors, setValidationErrors] = useState<Record<string, string[]>>({});

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchBusinessPartner(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `GP: ${result.shortName}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const handleDataChange = useCallback((updated: BusinessPartnerDto) => {
    setData(updated);
    setDirty(true);
    setValidationErrors({});
  }, []);

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

  const handleSave = useCallback(async () => {
    const saved = await saveBusinessPartner(data);
    setData(saved);
    updateTabLabel(tabId, `GP: ${saved.shortName}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
    setValidationErrors({});
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteBusinessPartner(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('business-partner-detail', { mode: 'new' });
  }, [openTab]);

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="business-partners"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
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
