import { useState, useEffect, useCallback } from 'react';
import { adminGetGroups, adminGetGroup, adminCreateGroup, adminUpdateGroup, adminDeleteGroup, adminAddMember, adminRemoveMember, adminGetUsers, type AdminGroup, type AdminGroupDetail, type AdminUser } from '../api/client';
import { Card } from '../shared/Card';
import { Button } from '../shared/Button';
import { StatusMessage } from '../shared/StatusMessage';
import { useMessageBar } from '../shell/MessageBarContext';
import './Admin.css';

export function AdminGroupsPage({ tabId: _tabId }: { tabId: string }) {
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [selectedGroup, setSelectedGroup] = useState<AdminGroupDetail | null>(null);
  const [allUsers, setAllUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newGroup, setNewGroup] = useState({ name: '', description: '' });
  const [editName, setEditName] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [addUserId, setAddUserId] = useState('');
  const { showMessage } = useMessageBar();

  const loadGroups = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [g, u] = await Promise.all([adminGetGroups(), adminGetUsers()]);
      setGroups(g);
      setAllUsers(u);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadGroups(); }, [loadGroups]);

  const selectGroup = useCallback(async (id: number) => {
    try {
      const detail = await adminGetGroup(id);
      setSelectedGroup(detail);
      setEditName(detail.name);
      setEditDesc(detail.description ?? '');
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  }, [showMessage]);

  const handleCreate = async () => {
    if (!newGroup.name) return;
    try {
      const { groupId } = await adminCreateGroup(newGroup.name, newGroup.description);
      showMessage(`Gruppe "${newGroup.name}" angelegt`, 'success');
      setShowCreate(false);
      setNewGroup({ name: '', description: '' });
      await loadGroups();
      selectGroup(groupId);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleUpdate = async () => {
    if (!selectedGroup || !editName) return;
    try {
      await adminUpdateGroup(selectedGroup.groupId, editName, editDesc);
      showMessage('Gruppe aktualisiert', 'success');
      loadGroups();
      selectGroup(selectedGroup.groupId);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleDelete = async () => {
    if (!selectedGroup) return;
    if (!confirm(`Gruppe "${selectedGroup.name}" wirklich loeschen?`)) return;
    try {
      await adminDeleteGroup(selectedGroup.groupId);
      showMessage('Gruppe geloescht', 'success');
      setSelectedGroup(null);
      loadGroups();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleAddMember = async () => {
    if (!selectedGroup || !addUserId) return;
    try {
      await adminAddMember(selectedGroup.groupId, addUserId);
      showMessage('Mitglied hinzugefuegt', 'success');
      setAddUserId('');
      selectGroup(selectedGroup.groupId);
      loadGroups();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const handleRemoveMember = async (userId: string) => {
    if (!selectedGroup) return;
    try {
      await adminRemoveMember(selectedGroup.groupId, userId);
      showMessage('Mitglied entfernt', 'success');
      selectGroup(selectedGroup.groupId);
      loadGroups();
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  const memberIds = new Set(selectedGroup?.members.map(m => m.userId) ?? []);
  const availableUsers = allUsers.filter(u => !memberIds.has(u.userId));

  return (
    <div className="admin-page">
      <div className="admin-toolbar">
        <h2>Gruppenverwaltung</h2>
        <Button variant="primary" onClick={() => setShowCreate(true)}>Neue Gruppe</Button>
      </div>

      {showCreate && (
        <Card>
          <div className="admin-form">
            <h3>Neue Gruppe</h3>
            <div className="admin-form-row">
              <label>Name</label>
              <input value={newGroup.name} onChange={e => setNewGroup(g => ({ ...g, name: e.target.value }))} />
            </div>
            <div className="admin-form-row">
              <label>Beschreibung</label>
              <input value={newGroup.description} onChange={e => setNewGroup(g => ({ ...g, description: e.target.value }))} />
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
        <div className="admin-split">
          <div className="admin-split-left">
            <Card>
              <div className="admin-group-list">
                {groups.map(g => (
                  <div
                    key={g.groupId}
                    className={`admin-group-item ${selectedGroup?.groupId === g.groupId ? 'selected' : ''}`}
                    onClick={() => selectGroup(g.groupId)}
                  >
                    <span className="admin-group-name">{g.name}</span>
                    <span className="admin-group-count">{g.memberCount}</span>
                  </div>
                ))}
                {groups.length === 0 && <div className="admin-empty">Keine Gruppen</div>}
              </div>
            </Card>
          </div>

          <div className="admin-split-right">
            {selectedGroup ? (
              <Card>
                <div className="admin-form">
                  <h3>Gruppe bearbeiten</h3>
                  <div className="admin-form-row">
                    <label>Name</label>
                    <input value={editName} onChange={e => setEditName(e.target.value)} />
                  </div>
                  <div className="admin-form-row">
                    <label>Beschreibung</label>
                    <input value={editDesc} onChange={e => setEditDesc(e.target.value)} />
                  </div>
                  <div className="admin-form-actions">
                    <Button variant="primary" onClick={handleUpdate}>Speichern</Button>
                    <Button variant="ghost" onClick={handleDelete}>Loeschen</Button>
                  </div>

                  <h4>Mitglieder ({selectedGroup.members.length})</h4>
                  <div className="admin-member-list">
                    {selectedGroup.members.map(m => (
                      <div key={m.userId} className="admin-member-item">
                        <span>{m.username}</span>
                        <button className="admin-link" onClick={() => handleRemoveMember(m.userId)}>Entfernen</button>
                      </div>
                    ))}
                  </div>
                  <div className="admin-form-row admin-add-member">
                    <select value={addUserId} onChange={e => setAddUserId(e.target.value)}>
                      <option value="">User waehlen...</option>
                      {availableUsers.map(u => (
                        <option key={u.userId} value={u.userId}>{u.username}</option>
                      ))}
                    </select>
                    <Button variant="ghost" onClick={handleAddMember} disabled={!addUserId}>Hinzufuegen</Button>
                  </div>
                </div>
              </Card>
            ) : (
              <Card><div className="admin-empty">Gruppe auswaehlen</div></Card>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
