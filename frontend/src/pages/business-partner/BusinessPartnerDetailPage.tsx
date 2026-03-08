import { useState, useCallback, useEffect, useRef } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { Button } from '../../shared/Button';
import { ContactPersonCard } from './ContactPersonCard';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchBusinessPartner, saveBusinessPartner, deleteBusinessPartner } from '../../api/client';
import type { BusinessPartnerDto, ContactPersonDto } from '../../api/types';

const emptyContact = (): ContactPersonDto => ({
  id: null,
  firstName: '',
  lastName: '',
  email: null,
  phone: null,
  street: null,
  zipCode: null,
  city: null,
  functions: [],
});

export function BusinessPartnerDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const entityId = params?.entityId as number | undefined;
  const contactKeyCounter = useRef(0);

  const [data, setData] = useState<BusinessPartnerDto>({
    id: null,
    shortName: '',
    name: '',
    notes: null,
    contacts: [],
  });
  const [contactKeys, setContactKeys] = useState<string[]>([]);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');

  const nextKey = () => `ck-${contactKeyCounter.current++}`;

  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchBusinessPartner(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      setContactKeys(result.contacts.map(() => nextKey()));
      updateTabLabel(tabId, `GP: ${result.shortName}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel]);

  const updateField = useCallback((field: keyof BusinessPartnerDto, value: unknown) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const updateContact = useCallback((index: number, updated: ContactPersonDto) => {
    setData(prev => {
      const contacts = [...prev.contacts];
      contacts[index] = updated;
      return { ...prev, contacts };
    });
    setDirty(true);
  }, []);

  const removeContact = useCallback((index: number) => {
    setData(prev => ({
      ...prev,
      contacts: prev.contacts.filter((_, i) => i !== index),
    }));
    setContactKeys(prev => prev.filter((_, i) => i !== index));
    setDirty(true);
  }, []);

  const addContact = useCallback(() => {
    setData(prev => ({
      ...prev,
      contacts: [...prev.contacts, emptyContact()],
    }));
    setContactKeys(prev => [...prev, nextKey()]);
    setDirty(true);
  }, []);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (!data.shortName.trim()) errors.push({ field: 'shortName', message: 'Kurzbezeichnung' });
    data.contacts.forEach((c, i) => {
      if (!c.firstName.trim()) errors.push({ field: `contact-${i}-firstName`, message: `Ansprechpartner ${i + 1}: Vorname` });
      if (!c.lastName.trim()) errors.push({ field: `contact-${i}-lastName`, message: `Ansprechpartner ${i + 1}: Nachname` });
    });
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    const saved = await saveBusinessPartner(data);
    setData(saved);
    setContactKeys(saved.contacts.map(() => nextKey()));
    updateTabLabel(tabId, `GP: ${saved.shortName}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteBusinessPartner(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('business-partner-detail', { mode: 'new' });
  }, [openTab]);

  const isDisabled = mode === 'view';

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
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <FormField label="Kurzbezeichnung">
              <input
                value={data.shortName}
                onChange={e => updateField('shortName', e.target.value)}
                disabled={isDisabled}
                maxLength={50}
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
          </div>
          <FormField label="Notizen">
            <textarea
              value={data.notes ?? ''}
              onChange={e => updateField('notes', e.target.value || null)}
              disabled={isDisabled}
              rows={3}
              style={{ resize: 'vertical' }}
            />
          </FormField>
        </div>
      </Card>

      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
          <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)' }}>Ansprechpartner</h3>
          {!isDisabled && (
            <Button variant="ghost" onClick={addContact}>+ Ansprechpartner hinzufuegen</Button>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          {data.contacts.map((contact, index) => (
            <ContactPersonCard
              key={contactKeys[index] ?? `fallback-${index}`}
              contact={contact}
              disabled={isDisabled}
              onChange={updated => updateContact(index, updated)}
              onRemove={() => removeContact(index)}
            />
          ))}
          {data.contacts.length === 0 && (
            <div style={{ padding: 'var(--space-md)', color: 'var(--color-text-secondary)', textAlign: 'center' }}>
              Keine Ansprechpartner vorhanden
            </div>
          )}
        </div>
      </div>
    </DetailPage>
  );
}
