import { useState, useEffect, useCallback } from 'react';
import { adminGetUsers, adminCreateUser, adminSetAdmin, adminSetEnabled, adminResetPassword, type AdminUser } from '../api/client';
import { Card } from '../shared/Card';
import { Button } from '../shared/Button';
import { StatusMessage } from '../shared/StatusMessage';
import { useMessageBar } from '../shell/MessageBarContext';
import './Admin.css';

export function AdminUsersPage({ tabId: _tabId }: { tabId: string }) {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newUser, setNewUser] = useState({ username: '', email: '', password: '' });
  const [resetPwUser, setResetPwUser] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const { showMessage } = useMessageBar();

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await adminGetUsers());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadUsers(); }, [loadUsers]);

  const handleCreate = async () => {
    if (!newUser.username || !newUser.password) return;
    try {
      await adminCreateUser(newUser.username, newUser.email, newUser.password);
      showMessage(`User "${newUser.username}" angelegt`, 'success');
      setShowCreate(false);
      setNewUser({ username: '', email: '', password: '' });
      loadUsers();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleToggleAdmin = async (user: AdminUser) => {
    try {
      await adminSetAdmin(user.userId, !user.isAdmin);
      showMessage(`${user.username}: Admin ${!user.isAdmin ? 'aktiviert' : 'deaktiviert'}`, 'success');
      loadUsers();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleToggleEnabled = async (user: AdminUser, enabled: boolean) => {
    try {
      await adminSetEnabled(user.userId, enabled);
      showMessage(`${user.username}: ${enabled ? 'aktiviert' : 'deaktiviert'}`, 'success');
      loadUsers();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleResetPassword = async () => {
    if (!resetPwUser || !newPassword) return;
    try {
      await adminResetPassword(resetPwUser, newPassword);
      showMessage('Passwort zurueckgesetzt', 'success');
      setResetPwUser(null);
      setNewPassword('');
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-toolbar">
        <h2>Benutzerverwaltung</h2>
        <Button variant="primary" onClick={() => setShowCreate(true)}>Neuer User</Button>
      </div>

      {showCreate && (
        <Card>
          <div className="admin-form">
            <h3>Neuer Benutzer</h3>
            <div className="admin-form-row">
              <label>Username</label>
              <input value={newUser.username} onChange={e => setNewUser(u => ({ ...u, username: e.target.value }))} />
            </div>
            <div className="admin-form-row">
              <label>E-Mail</label>
              <input value={newUser.email} onChange={e => setNewUser(u => ({ ...u, email: e.target.value }))} />
            </div>
            <div className="admin-form-row">
              <label>Passwort</label>
              <input type="password" value={newUser.password} onChange={e => setNewUser(u => ({ ...u, password: e.target.value }))} />
            </div>
            <div className="admin-form-actions">
              <Button variant="primary" onClick={handleCreate}>Anlegen</Button>
              <Button variant="ghost" onClick={() => setShowCreate(false)}>Abbrechen</Button>
            </div>
          </div>
        </Card>
      )}

      {loading && <StatusMessage type="info">Lade...</StatusMessage>}
      {error && <StatusMessage type="error">{error}</StatusMessage>}

      {!loading && !error && (
        <Card>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>E-Mail</th>
                <th>Admin</th>
                <th>Gruppen</th>
                <th>Aktionen</th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <tr key={u.userId}>
                  <td>{u.username}</td>
                  <td>{u.email ?? '—'}</td>
                  <td>
                    <input type="checkbox" checked={u.isAdmin} onChange={() => handleToggleAdmin(u)} />
                  </td>
                  <td>{u.groupCount}</td>
                  <td className="admin-actions">
                    <button className="admin-link" onClick={() => handleToggleEnabled(u, true)} title="Aktivieren">Aktivieren</button>
                    <button className="admin-link" onClick={() => handleToggleEnabled(u, false)} title="Deaktivieren">Deaktivieren</button>
                    <button className="admin-link" onClick={() => { setResetPwUser(u.userId); setNewPassword(''); }} title="Passwort">PW Reset</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {resetPwUser && (
        <div className="admin-modal-backdrop" onClick={() => setResetPwUser(null)}>
          <div className="admin-modal" onClick={e => e.stopPropagation()}>
            <h3>Passwort zuruecksetzen</h3>
            <div className="admin-form-row">
              <label>Neues Passwort</label>
              <input type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} autoFocus />
            </div>
            <div className="admin-form-actions">
              <Button variant="primary" onClick={handleResetPassword}>Setzen</Button>
              <Button variant="ghost" onClick={() => setResetPwUser(null)}>Abbrechen</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
