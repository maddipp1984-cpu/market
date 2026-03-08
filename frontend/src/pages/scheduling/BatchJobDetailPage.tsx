import { useState, useCallback, useEffect } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchBatchJob, updateBatchJob, triggerBatchJob, fetchBatchJobHistory, fetchBatchJobLog } from '../../api/client';
import type { BatchJobDto, JobExecutionDto } from '../../api/types';

export function BatchJobDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<BatchJobDto>({
    id: null, jobKey: '', name: '', description: null,
    jobClass: '', scheduleType: 'NONE', cronExpression: null,
    intervalSeconds: null, enabled: false,
  });
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(true);
  const [history, setHistory] = useState<JobExecutionDto[]>([]);
  const [triggering, setTriggering] = useState(false);
  const [logContent, setLogContent] = useState<string | null>(null);
  const [logExecId, setLogExecId] = useState<number | null>(null);

  const loadHistory = useCallback(async (id: number) => {
    try {
      const h = await fetchBatchJobHistory(id);
      setHistory(h);
    } catch { /* ignore */ }
  }, []);

  useEffect(() => {
    if (!entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchBatchJob(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, `Job: ${result.name}`);
      setLoading(false);
      loadHistory(entityId);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, tabId, updateTabLabel, loadHistory, showMessage]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (data.scheduleType === 'CRON' && (!data.cronExpression || !data.cronExpression.trim())) {
      errors.push({ field: 'cronExpression', message: 'Cron-Expression ist Pflichtfeld' });
    }
    if (data.scheduleType === 'INTERVAL' && (!data.intervalSeconds || data.intervalSeconds <= 0)) {
      errors.push({ field: 'intervalSeconds', message: 'Intervall muss groesser 0 sein' });
    }
    return { valid: errors.length === 0, errors };
  }, [data]);

  const handleSave = useCallback(async () => {
    if (!entityId) return;
    const saved = await updateBatchJob(entityId, data);
    setData(saved);
    updateTabLabel(tabId, `Job: ${saved.name}`);
  }, [data, entityId, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleTrigger = useCallback(async () => {
    if (!entityId) return;
    setTriggering(true);
    try {
      await triggerBatchJob(entityId);
      showMessage('Job wird ausgefuehrt...', 'success');
      // Reload history after short delay
      setTimeout(() => loadHistory(entityId), 2000);
    } catch (err) {
      showMessage(err instanceof Error ? err.message : 'Ausfuehrung fehlgeschlagen', 'error');
    } finally {
      setTriggering(false);
    }
  }, [entityId, showMessage, loadHistory]);

  const handleShowLog = useCallback(async (execId: number) => {
    if (!entityId) return;
    try {
      const content = await fetchBatchJobLog(entityId, execId);
      setLogContent(content);
      setLogExecId(execId);
    } catch {
      showMessage('Log konnte nicht geladen werden', 'error');
    }
  }, [entityId, showMessage]);

  if (loading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  return (
    <DetailPage
      pageKey="batch-jobs"
      mode={'edit' as DetailMode}
      tabId={tabId}
      dirty={dirty}
      validate={validate}
      onSave={handleSave}
      onSaveSuccess={handleSaveSuccess}
    >
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input value={data.name} disabled />
              </FormField>
            </div>
            <div style={{ flex: 1 }}>
              <FormField label="Job-Key">
                <input value={data.jobKey} disabled />
              </FormField>
            </div>
          </div>

          <FormField label="Beschreibung">
            <input value={data.description || ''} disabled />
          </FormField>

          <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'end' }}>
            <FormField label="Aktiv">
              <input
                type="checkbox"
                checked={data.enabled}
                onChange={e => { setData(prev => ({ ...prev, enabled: e.target.checked })); setDirty(true); }}
                style={{ width: 'auto' }}
              />
            </FormField>

            <FormField label="Schedule-Typ">
              <select
                value={data.scheduleType}
                onChange={e => {
                  const val = e.target.value as BatchJobDto['scheduleType'];
                  setData(prev => ({ ...prev, scheduleType: val }));
                  setDirty(true);
                }}
              >
                <option value="NONE">Kein Schedule</option>
                <option value="CRON">Cron</option>
                <option value="INTERVAL">Intervall</option>
              </select>
            </FormField>

            {data.scheduleType === 'CRON' && (
              <div style={{ flex: 1 }}>
                <FormField label="Cron-Expression">
                  <input
                    value={data.cronExpression || ''}
                    onChange={e => { setData(prev => ({ ...prev, cronExpression: e.target.value })); setDirty(true); }}
                    placeholder="0 0 2 * * ?"
                  />
                </FormField>
              </div>
            )}

            {data.scheduleType === 'INTERVAL' && (
              <FormField label="Intervall (Sekunden)">
                <input
                  type="number"
                  value={data.intervalSeconds || ''}
                  onChange={e => { setData(prev => ({ ...prev, intervalSeconds: parseInt(e.target.value) || null })); setDirty(true); }}
                  min={1}
                  style={{ width: '120px' }}
                />
              </FormField>
            )}

            <button
              onClick={handleTrigger}
              disabled={triggering}
              style={{
                padding: 'var(--space-xs) var(--space-md)',
                background: 'var(--color-primary)',
                color: 'white',
                border: 'none',
                borderRadius: 'var(--radius-sm)',
                cursor: triggering ? 'not-allowed' : 'pointer',
                whiteSpace: 'nowrap',
                height: 'fit-content',
              }}
            >
              {triggering ? 'Wird ausgefuehrt...' : 'Jetzt ausfuehren'}
            </button>
          </div>
        </div>
      </Card>

      {/* History */}
      <Card>
        <div style={{ padding: 'var(--space-md)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)', color: 'var(--color-text-primary)' }}>
              Ausfuehrungshistorie
            </h3>
            {entityId && (
              <button
                onClick={() => loadHistory(entityId)}
                style={{
                  padding: 'var(--space-xs) var(--space-sm)',
                  background: 'none',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-sm)',
                  cursor: 'pointer',
                  color: 'var(--color-text-secondary)',
                  fontSize: 'var(--font-size-sm)',
                }}
              >
                Aktualisieren
              </button>
            )}
          </div>

          {history.length === 0 ? (
            <div style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
              Noch keine Ausfuehrungen
            </div>
          ) : (
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 'var(--font-size-sm)' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
                  <th style={{ textAlign: 'left', padding: 'var(--space-xs) var(--space-sm)' }}>Zeitpunkt</th>
                  <th style={{ textAlign: 'left', padding: 'var(--space-xs) var(--space-sm)' }}>Dauer</th>
                  <th style={{ textAlign: 'left', padding: 'var(--space-xs) var(--space-sm)' }}>Status</th>
                  <th style={{ textAlign: 'right', padding: 'var(--space-xs) var(--space-sm)' }}>Datensaetze</th>
                  <th style={{ textAlign: 'left', padding: 'var(--space-xs) var(--space-sm)' }}>Ausgeloest</th>
                  <th style={{ textAlign: 'left', padding: 'var(--space-xs) var(--space-sm)' }}>Fehler</th>
                  <th style={{ textAlign: 'center', padding: 'var(--space-xs) var(--space-sm)' }}>Log</th>
                </tr>
              </thead>
              <tbody>
                {history.map(exec => {
                  const start = new Date(exec.startTime);
                  const end = exec.endTime ? new Date(exec.endTime) : null;
                  const durationMs = end ? end.getTime() - start.getTime() : null;
                  const durationStr = durationMs !== null
                    ? durationMs < 1000 ? `${durationMs}ms`
                      : durationMs < 60000 ? `${(durationMs / 1000).toFixed(1)}s`
                        : `${Math.floor(durationMs / 60000)}m ${Math.round((durationMs % 60000) / 1000)}s`
                    : '-';

                  const statusColor = exec.status === 'COMPLETED' ? 'var(--color-success, #22c55e)'
                    : exec.status === 'FAILED' ? 'var(--color-error, #ef4444)'
                      : 'var(--color-warning, #f59e0b)';

                  return (
                    <tr key={exec.id} style={{ borderBottom: '1px solid var(--color-border-light, var(--color-border))' }}>
                      <td style={{ padding: 'var(--space-xs) var(--space-sm)' }}>
                        {start.toLocaleString('de-DE')}
                      </td>
                      <td style={{ padding: 'var(--space-xs) var(--space-sm)' }}>{durationStr}</td>
                      <td style={{ padding: 'var(--space-xs) var(--space-sm)' }}>
                        <span style={{ color: statusColor, fontWeight: 600 }}>{exec.status}</span>
                      </td>
                      <td style={{ textAlign: 'right', padding: 'var(--space-xs) var(--space-sm)' }}>
                        {exec.recordsAffected ?? '-'}
                      </td>
                      <td style={{ padding: 'var(--space-xs) var(--space-sm)' }}>{exec.triggeredBy}</td>
                      <td style={{ padding: 'var(--space-xs) var(--space-sm)', color: 'var(--color-error, #ef4444)', maxWidth: '200px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {exec.errorMessage || ''}
                      </td>
                      <td style={{ textAlign: 'center', padding: 'var(--space-xs) var(--space-sm)' }}>
                        {exec.logFile && (
                          <button
                            onClick={() => handleShowLog(exec.id)}
                            style={{
                              background: 'none',
                              border: 'none',
                              color: 'var(--color-primary)',
                              cursor: 'pointer',
                              textDecoration: 'underline',
                              fontSize: 'var(--font-size-sm)',
                            }}
                          >
                            Log
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}

          {/* Log Modal */}
          {logContent !== null && (
            <div style={{
              position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
              background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center',
              zIndex: 1000,
            }}
              onClick={() => { setLogContent(null); setLogExecId(null); }}
            >
              <div
                style={{
                  background: 'var(--color-surface, white)', borderRadius: 'var(--radius-md)',
                  padding: 'var(--space-md)', maxWidth: '800px', width: '90%', maxHeight: '80vh',
                  display: 'flex', flexDirection: 'column',
                }}
                onClick={e => e.stopPropagation()}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
                  <h3 style={{ margin: 0 }}>Log (Execution #{logExecId})</h3>
                  <button
                    onClick={() => { setLogContent(null); setLogExecId(null); }}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: 'var(--color-text-secondary)' }}
                  >
                    ✕
                  </button>
                </div>
                <pre style={{
                  flex: 1, overflow: 'auto', background: 'var(--color-bg, #f8f9fa)',
                  padding: 'var(--space-sm)', borderRadius: 'var(--radius-sm)',
                  fontSize: 'var(--font-size-sm)', fontFamily: 'monospace',
                  whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                  margin: 0,
                }}>
                  {logContent}
                </pre>
              </div>
            </div>
          )}
        </div>
      </Card>
    </DetailPage>
  );
}
