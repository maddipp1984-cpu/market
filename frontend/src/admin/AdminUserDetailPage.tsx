import { useState, useCallback } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../shared/detail-page/DetailPage';
import { Card } from '../shared/Card';
import { FormField } from '../shared/FormField';
import { useTabContext } from '../shell/TabContext';

export function AdminUserDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, updateTabLabel } = useTabContext();
  const params = getTabParams(tabId);
  const mode = (params?.mode as DetailMode) ?? 'view';
  const userId = params?.entityId as string | undefined;

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [dirty, setDirty] = useState(false);

  // TODO: Bei mode edit/view Daten laden per userId

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!username.trim()) errors.push({ field: 'username', message: 'Username' });
    return { valid: errors.length === 0, errors };
  }, [username]);

  const handleSave = useCallback(async () => {
    // TODO: API-Call
    console.log('Save:', { username, email, userId });
    updateTabLabel(tabId, `Benutzer: ${username}`);
  }, [username, email, userId, updateTabLabel, tabId]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleNew = useCallback(() => {
    openTab('admin-user-detail', { mode: 'new' });
  }, [openTab]);

  return (
    <DetailPage
      pageKey="admin-users"
      mode={mode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
      onNew={handleNew}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <FormField label="Username">
            <input
              value={username}
              onChange={e => { setUsername(e.target.value); setDirty(true); }}
              disabled={mode === 'view'}
            />
          </FormField>
          <FormField label="E-Mail">
            <input
              value={email}
              onChange={e => { setEmail(e.target.value); setDirty(true); }}
              disabled={mode === 'view'}
            />
          </FormField>
        </div>
      </Card>
    </DetailPage>
  );
}
