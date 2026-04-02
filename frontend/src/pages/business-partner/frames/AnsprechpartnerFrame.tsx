import { useCallback, useRef, useState } from 'react';
import { Button } from '../../../shared/Button';
import { ContactPersonCard } from '../ContactPersonCard';
import type { FrameProps } from '../../../shared/tree-navigation/types';
import type { BusinessPartnerDto, ContactPersonDto } from '../../../api/types';

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

export function AnsprechpartnerFrame({ data, onChange, disabled }: FrameProps<BusinessPartnerDto>) {
  const contactKeyCounter = useRef(0);
  const [contactKeys, setContactKeys] = useState<string[]>(
    () => data.contacts.map(() => `ck-${contactKeyCounter.current++}`)
  );

  const nextKey = () => `ck-${contactKeyCounter.current++}`;

  const updateContact = useCallback((index: number, updated: ContactPersonDto) => {
    const contacts = [...data.contacts];
    contacts[index] = updated;
    onChange({ ...data, contacts });
  }, [data, onChange]);

  const removeContact = useCallback((index: number) => {
    onChange({
      ...data,
      contacts: data.contacts.filter((_, i) => i !== index),
    });
    setContactKeys(prev => prev.filter((_, i) => i !== index));
  }, [data, onChange]);

  const addContact = useCallback(() => {
    onChange({
      ...data,
      contacts: [...data.contacts, emptyContact()],
    });
    setContactKeys(prev => [...prev, nextKey()]);
  }, [data, onChange]);

  // Sync contactKeys when data.contacts changes externally (e.g. after save/load)
  if (contactKeys.length !== data.contacts.length) {
    setContactKeys(data.contacts.map(() => nextKey()));
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
        <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)' }}>Ansprechpartner</h3>
        {!disabled && (
          <Button variant="ghost" onClick={addContact}>+ Ansprechpartner hinzufuegen</Button>
        )}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
        {data.contacts.map((contact, index) => (
          <ContactPersonCard
            key={contactKeys[index] ?? `fallback-${index}`}
            contact={contact}
            disabled={disabled}
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
  );
}
