import { useState, useEffect, useCallback } from 'react';
import { adminGetGroups, adminGetGroup, adminGetResources, adminSetPermissions, adminSetFieldRestrictions, type AdminGroup, type AdminGroupDetail, type AdminResource } from '../api/client';
import { Card } from '../shared/Card';
import { Button } from '../shared/Button';
import { StatusMessage } from '../shared/StatusMessage';
import { useMessageBar } from '../shell/MessageBarContext';
import './Admin.css';

interface PermRow {
  resourceKey: string;
  objectTypeId: number | null;
  canRead: boolean;
  canWrite: boolean;
  canDelete: boolean;
}

const OBJECT_TYPES = [
  { id: 1, label: 'Vertrag VHP' },
  { id: 2, label: 'Vertrag' },
  { id: 3, label: 'Vertragsanschluss' },
  { id: 4, label: 'Anschluss' },
];

export function AdminPermissionsPage({ tabId: _tabId }: { tabId: string }) {
  const [groups, setGroups] = useState<AdminGroup[]>([]);
  const [resources, setResources] = useState<AdminResource[]>([]);
  const [selectedGroupId, setSelectedGroupId] = useState<number | null>(null);
  const [groupDetail, setGroupDetail] = useState<AdminGroupDetail | null>(null);
  const [permRows, setPermRows] = useState<PermRow[]>([]);
  const [fieldRestrictions, setFieldRestrictions] = useState<{ resourceKey: string; fieldKey: string; objectTypeId: number | null }[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [expandedResources, setExpandedResources] = useState<Set<string>>(new Set());
  const { showMessage } = useMessageBar();

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        const [g, r] = await Promise.all([adminGetGroups(), adminGetResources()]);
        setGroups(g);
        setResources(r);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const loadGroupDetail = useCallback(async (groupId: number) => {
    try {
      const detail = await adminGetGroup(groupId);
      setGroupDetail(detail);

      // Build permission rows from existing data
      const rows: PermRow[] = [];
      for (const res of resources) {
        // Resource-level permission
        const resPerm = detail.permissions.find(p => p.resourceKey === res.resourceKey && p.objectTypeId == null);
        rows.push({
          resourceKey: res.resourceKey,
          objectTypeId: null,
          canRead: resPerm?.canRead ?? false,
          canWrite: resPerm?.canWrite ?? false,
          canDelete: resPerm?.canDelete ?? false,
        });
        if (res.hasTypeScope) {
          for (const ot of OBJECT_TYPES) {
            const typePerm = detail.permissions.find(p => p.resourceKey === res.resourceKey && p.objectTypeId === ot.id);
            rows.push({
              resourceKey: res.resourceKey,
              objectTypeId: ot.id,
              canRead: typePerm?.canRead ?? false,
              canWrite: typePerm?.canWrite ?? false,
              canDelete: typePerm?.canDelete ?? false,
            });
          }
        }
      }
      setPermRows(rows);
      setFieldRestrictions(detail.fieldRestrictions.map(r => ({ resourceKey: r.resourceKey, fieldKey: r.fieldKey, objectTypeId: r.objectTypeId })));
      setDirty(false);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  }, [resources, showMessage]);

  const handleGroupChange = (groupId: number) => {
    setSelectedGroupId(groupId);
    loadGroupDetail(groupId);
  };

  const togglePerm = (resourceKey: string, objectTypeId: number | null, field: 'canRead' | 'canWrite' | 'canDelete') => {
    setPermRows(rows => rows.map(r => {
      if (r.resourceKey === resourceKey && r.objectTypeId === objectTypeId) {
        return { ...r, [field]: !r[field] };
      }
      return r;
    }));
    setDirty(true);
  };

  const toggleExpand = (resourceKey: string) => {
    setExpandedResources(prev => {
      const next = new Set(prev);
      if (next.has(resourceKey)) next.delete(resourceKey);
      else next.add(resourceKey);
      return next;
    });
  };

  const handleSave = async () => {
    if (selectedGroupId == null) return;
    try {
      // Only send rows where at least one permission is set
      const activePerms = permRows.filter(r => r.canRead || r.canWrite || r.canDelete);
      await adminSetPermissions(selectedGroupId, activePerms);
      await adminSetFieldRestrictions(selectedGroupId, fieldRestrictions);
      showMessage('Berechtigungen gespeichert', 'success');
      setDirty(false);
    } catch (e) {
      showMessage(e instanceof Error ? e.message : String(e), 'error');
    }
  };

  if (loading) return <StatusMessage type="info">Lade...</StatusMessage>;
  if (error) return <StatusMessage type="error">{error}</StatusMessage>;

  return (
    <div className="admin-page">
      <div className="admin-toolbar">
        <h2>Berechtigungen</h2>
        <div className="admin-toolbar-right">
          <select
            value={selectedGroupId ?? ''}
            onChange={e => e.target.value && handleGroupChange(Number(e.target.value))}
            className="admin-group-select"
          >
            <option value="">Gruppe waehlen...</option>
            {groups.map(g => (
              <option key={g.groupId} value={g.groupId}>{g.name}</option>
            ))}
          </select>
          {dirty && <Button variant="primary" onClick={handleSave}>Speichern</Button>}
        </div>
      </div>

      {selectedGroupId && groupDetail && (
        <Card>
          <table className="admin-table admin-perm-table">
            <thead>
              <tr>
                <th>Resource</th>
                <th>Lesen</th>
                <th>Schreiben</th>
                <th>Loeschen</th>
              </tr>
            </thead>
            <tbody>
              {resources.map(res => {
                const resRow = permRows.find(r => r.resourceKey === res.resourceKey && r.objectTypeId == null);
                const expanded = expandedResources.has(res.resourceKey);
                return [
                  <tr key={res.resourceKey} className="perm-resource-row">
                    <td>
                      {res.hasTypeScope && (
                        <button className="admin-expand-btn" onClick={() => toggleExpand(res.resourceKey)}>
                          {expanded ? '▼' : '▶'}
                        </button>
                      )}
                      <strong>{res.label}</strong>
                    </td>
                    <td><input type="checkbox" checked={resRow?.canRead ?? false} onChange={() => togglePerm(res.resourceKey, null, 'canRead')} /></td>
                    <td><input type="checkbox" checked={resRow?.canWrite ?? false} onChange={() => togglePerm(res.resourceKey, null, 'canWrite')} /></td>
                    <td><input type="checkbox" checked={resRow?.canDelete ?? false} onChange={() => togglePerm(res.resourceKey, null, 'canDelete')} /></td>
                  </tr>,
                  ...(res.hasTypeScope && expanded ? OBJECT_TYPES.map(ot => {
                    const typeRow = permRows.find(r => r.resourceKey === res.resourceKey && r.objectTypeId === ot.id);
                    return (
                      <tr key={`${res.resourceKey}-${ot.id}`} className="perm-type-row">
                        <td className="perm-type-label">{ot.label}</td>
                        <td><input type="checkbox" checked={typeRow?.canRead ?? false} onChange={() => togglePerm(res.resourceKey, ot.id, 'canRead')} /></td>
                        <td><input type="checkbox" checked={typeRow?.canWrite ?? false} onChange={() => togglePerm(res.resourceKey, ot.id, 'canWrite')} /></td>
                        <td><input type="checkbox" checked={typeRow?.canDelete ?? false} onChange={() => togglePerm(res.resourceKey, ot.id, 'canDelete')} /></td>
                      </tr>
                    );
                  }) : []),
                ];
              })}
            </tbody>
          </table>
        </Card>
      )}

      {!selectedGroupId && (
        <Card><div className="admin-empty">Bitte eine Gruppe auswaehlen</div></Card>
      )}
    </div>
  );
}
