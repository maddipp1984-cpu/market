import { useState, useCallback, useEffect, useMemo } from 'react';
import { DetailPage, type DetailMode, type ValidationResult } from '../../shared/detail-page/DetailPage';
import { Card } from '../../shared/Card';
import { FormField } from '../../shared/FormField';
import { useTabContext } from '../../shell/TabContext';
import { useMessageBar } from '../../shell/MessageBarContext';
import { fetchSchedule, saveSchedule, deleteSchedule, triggerSchedule, fetchJobCatalog } from '../../api/client';
import type { BatchScheduleDto, JobCatalogEntry, JobParameterDef } from '../../api/types';
import { Button } from '../../shared/Button';
import './BatchScheduleDetailPage.css';

const emptySchedule = (): BatchScheduleDto => ({
  id: null,
  jobKey: '',
  name: '',
  scheduleType: 'NONE',
  cronExpression: null,
  intervalSeconds: null,
  enabled: false,
  parameters: {},
});

function getDefaultParamValue(def: JobParameterDef): unknown {
  if (def.defaultValue !== null && def.defaultValue !== undefined) return def.defaultValue;
  switch (def.type) {
    case 'BOOLEAN': return false;
    case 'INTEGER': return 0;
    default: return '';
  }
}

export function BatchScheduleDetailPage({ tabId }: { tabId: string }) {
  const { getTabParams, openTab, closeTab, updateTabLabel } = useTabContext();
  const { showMessage } = useMessageBar();
  const params = getTabParams(tabId);
  const rawMode = (params?.mode as string) ?? 'view';
  const isTriggerMode = rawMode === 'trigger';
  const mode: DetailMode = isTriggerMode ? 'view' : (rawMode as DetailMode);
  const entityId = params?.entityId as number | undefined;

  const [data, setData] = useState<BatchScheduleDto>(emptySchedule);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(mode !== 'new');
  const [triggering, setTriggering] = useState(false);
  const [catalog, setCatalog] = useState<JobCatalogEntry[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);


  // Load job catalog
  useEffect(() => {
    let cancelled = false;
    setCatalogLoading(true);
    fetchJobCatalog().then(entries => {
      if (cancelled) return;
      setCatalog(entries);
      setCatalogLoading(false);
    }).catch(() => {
      setCatalogLoading(false);
    });
    return () => { cancelled = true; };
  }, []);

  // Load existing schedule
  useEffect(() => {
    if (mode === 'new' || !entityId) return;
    let cancelled = false;
    setLoading(true);
    fetchSchedule(entityId).then(result => {
      if (cancelled) return;
      setData(result);
      updateTabLabel(tabId, isTriggerMode ? `Start: ${result.name}` : `Planung: ${result.name}`);
      setLoading(false);
    }).catch((err) => {
      showMessage(err instanceof Error ? err.message : 'Laden fehlgeschlagen', 'error');
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [entityId, mode, tabId, updateTabLabel, showMessage]);

  // Selected job from catalog
  const selectedJob = useMemo(() => {
    return catalog.find(j => j.jobKey === data.jobKey) ?? null;
  }, [catalog, data.jobKey]);

  const updateField = useCallback(<K extends keyof BatchScheduleDto>(field: K, value: BatchScheduleDto[K]) => {
    setData(prev => ({ ...prev, [field]: value }));
    setDirty(true);
  }, []);

  const updateParam = useCallback((name: string, value: unknown) => {
    setData(prev => ({
      ...prev,
      parameters: { ...prev.parameters, [name]: value },
    }));
    setDirty(true);
  }, []);

  // When job selection changes, populate default parameters
  const handleJobChange = useCallback((jobKey: string) => {
    const job = catalog.find(j => j.jobKey === jobKey);
    const previousJob = catalog.find(j => j.jobKey === data.jobKey);
    const shouldUpdateName = !data.name || data.name === previousJob?.name;

    const defaults: Record<string, unknown> = {};
    if (job) {
      for (const p of job.parameters) {
        defaults[p.name] = getDefaultParamValue(p);
      }
    }
    setData(prev => ({
      ...prev,
      jobKey,
      name: shouldUpdateName ? (job?.name ?? '') : prev.name,
      parameters: defaults,
    }));
    setDirty(true);
  }, [catalog, data.jobKey, data.name]);

  const validate = useCallback((): ValidationResult => {
    const errors: { field: string; message: string }[] = [];
    if (!data.jobKey) errors.push({ field: 'jobKey', message: 'Job' });
    if (!data.name.trim()) errors.push({ field: 'name', message: 'Name' });
    if (data.scheduleType === 'CRON' && (!data.cronExpression || !data.cronExpression.trim())) {
      errors.push({ field: 'cronExpression', message: 'Cron-Expression' });
    }
    if (data.scheduleType === 'INTERVAL' && (!data.intervalSeconds || data.intervalSeconds <= 0)) {
      errors.push({ field: 'intervalSeconds', message: 'Intervall (> 0)' });
    }
    // Validate required parameters
    if (selectedJob) {
      for (const p of selectedJob.parameters) {
        if (p.required) {
          const val = data.parameters[p.name];
          if (val === null || val === undefined || val === '') {
            errors.push({ field: `param-${p.name}`, message: `Parameter: ${p.description || p.name}` });
          }
        }
      }
    }
    return { valid: errors.length === 0, errors };
  }, [data, selectedJob]);

  const handleSave = useCallback(async () => {
    const saved = await saveSchedule(data);
    setData(saved);
    updateTabLabel(tabId, `Planung: ${saved.name}`);
  }, [data, tabId, updateTabLabel]);

  const handleSaveSuccess = useCallback(() => {
    setDirty(false);
  }, []);

  const handleDelete = entityId ? async () => {
    await deleteSchedule(entityId);
  } : undefined;

  const handleNew = useCallback(() => {
    openTab('batch-schedule-detail', { mode: 'new' });
  }, [openTab]);

  const handleTrigger = useCallback(async () => {
    if (!entityId) return;
    // Validate required parameters
    if (selectedJob) {
      for (const p of selectedJob.parameters) {
        if (p.required) {
          const val = data.parameters[p.name];
          if (val === null || val === undefined || val === '') {
            showMessage(`Pflichtparameter "${p.description || p.name}" fehlt`, 'error');
            return;
          }
        }
      }
    }
    setTriggering(true);
    try {
      await triggerSchedule(entityId, data.parameters);
      showMessage('Job wird ausgefuehrt...', 'success');
      closeTab(tabId);
    } catch (err) {
      showMessage(err instanceof Error ? err.message : 'Ausfuehrung fehlgeschlagen', 'error');
    } finally {
      setTriggering(false);
    }
  }, [entityId, data.parameters, selectedJob, showMessage, closeTab, tabId]);

  const isDisabled = mode === 'view' && !isTriggerMode;
  const isScheduleDisabled = mode === 'view' || isTriggerMode;
  const isNew = mode === 'new';

  if (loading || catalogLoading) {
    return <div style={{ padding: 'var(--space-xl)', color: 'var(--color-text-secondary)' }}>Lade...</div>;
  }

  const iconPlay = (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polygon points="5 3 19 12 5 21 5 3" />
    </svg>
  );

  const triggerAction = isTriggerMode ? (
    <Button
      variant="primary"
      icon
      onClick={handleTrigger}
      disabled={triggering}
      title="Jetzt starten"
      aria-label="Jetzt starten"
    >
      {iconPlay}
    </Button>
  ) : null;

  return (
    <DetailPage
      pageKey="batch-schedules"
      mode={mode}
      tabId={tabId}
      dirty={isTriggerMode ? false : dirty}
      validate={validate}
      onSave={isTriggerMode ? undefined as unknown as () => Promise<void> : handleSave}
      onSaveSuccess={handleSaveSuccess}
      onDelete={isTriggerMode ? undefined : handleDelete}
      onNew={isTriggerMode ? undefined : handleNew}
      extraActions={triggerAction}
    >
      {/* Job & Schedule */}
      <Card>
        <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
            {isNew ? (
              <div style={{ flex: 1 }}>
                <FormField label="Job">
                  <select
                    value={data.jobKey}
                    onChange={e => handleJobChange(e.target.value)}
                  >
                    <option value="">-- Job waehlen --</option>
                    {catalog.map(j => (
                      <option key={j.jobKey} value={j.jobKey}>{j.name}</option>
                    ))}
                  </select>
                </FormField>
              </div>
            ) : (
              <div style={{ flex: 1 }}>
                <FormField label="Job">
                  <input value={selectedJob?.name ?? data.jobKey} disabled />
                </FormField>
              </div>
            )}
            <div style={{ flex: 1 }}>
              <FormField label="Name">
                <input
                  value={data.name}
                  onChange={e => updateField('name', e.target.value)}
                  disabled={isScheduleDisabled}
                />
              </FormField>
            </div>
          </div>

          {selectedJob?.description && (
            <div style={{
              margin: 0,
              padding: 'var(--space-sm) var(--space-md)',
              background: 'var(--color-bg, #f8f9fa)',
              borderLeft: '3px solid var(--color-primary)',
              borderRadius: 'var(--radius-sm)',
              color: 'var(--color-text-secondary)',
              fontSize: 'var(--font-size-sm)',
              lineHeight: 1.5,
            }}>
              {selectedJob.description}
            </div>
          )}

          <div style={{ display: 'flex', gap: 'var(--space-md)', alignItems: 'end' }}>
            <FormField label="Schedule-Typ">
              <select
                value={data.scheduleType}
                onChange={e => updateField('scheduleType', e.target.value as BatchScheduleDto['scheduleType'])}
                disabled={isScheduleDisabled}
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
                    onChange={e => updateField('cronExpression', e.target.value)}
                    disabled={isScheduleDisabled}
                    placeholder="0 0 2 * * ?"
                    title={'6 Felder, getrennt durch Leerzeichen:\n\n1. Sekunde    (0-59)\n2. Minute     (0-59)\n3. Stunde     (0-23)\n4. Tag        (1-31 oder ?)\n5. Monat      (1-12 oder JAN-DEC)\n6. Wochentag  (1-7 oder MON-SUN, ? = egal)\n\nSonderzeichen:\n*  = jeder Wert\n?  = kein bestimmter (Tag/Wochentag)\n/  = Schrittweite (z.B. */2 = alle 2)\n-  = Bereich (z.B. MON-FRI)\n\nBeispiele:\n0 0 6 * * ?       = Taeglich um 06:00\n0 0 */2 * * ?     = Alle 2 Stunden\n0 30 8 ? * MON    = Montags um 08:30\n0 0 0 1 * ?       = Monatlich am 1.\n0 0 22 ? * MON-FRI = Werktags um 22:00'}
                  />
                </FormField>
              </div>
            )}

            {data.scheduleType === 'INTERVAL' && (
              <FormField label="Intervall (Sekunden)">
                <input
                  type="number"
                  value={data.intervalSeconds ?? ''}
                  onChange={e => updateField('intervalSeconds', parseInt(e.target.value) || null)}
                  disabled={isScheduleDisabled}
                  min={1}
                  style={{ width: '120px' }}
                />
              </FormField>
            )}

            <label style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--space-xs)',
              cursor: isScheduleDisabled ? 'default' : 'pointer',
              marginLeft: 'auto',
              paddingBottom: '6px',
              fontSize: 'var(--font-size-base)',
              color: 'var(--color-text-primary)',
              whiteSpace: 'nowrap',
              alignSelf: 'end',
            }}>
              <input
                type="checkbox"
                checked={data.enabled}
                onChange={e => updateField('enabled', e.target.checked)}
                disabled={isScheduleDisabled}
                style={{ width: 'auto', margin: 0 }}
              />
              Aktiv
            </label>
          </div>
        </div>
      </Card>

      {/* Parameter */}
      {selectedJob && selectedJob.parameters.length > 0 && (
        <Card>
          <div style={{ padding: 'var(--space-md)', display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
            <h3 style={{ margin: 0, fontSize: 'var(--font-size-md)', color: 'var(--color-text-primary)' }}>
              Parameter
            </h3>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: 'var(--space-md)',
            }}>
              {selectedJob.parameters.map(paramDef => (
                <ParameterField
                  key={paramDef.name}
                  def={paramDef}
                  value={data.parameters[paramDef.name]}
                  onChange={val => updateParam(paramDef.name, val)}
                  disabled={isDisabled}
                />
              ))}
            </div>
          </div>
        </Card>
      )}
    </DetailPage>
  );
}

// --- Dynamic parameter field component ---

interface ParameterFieldProps {
  def: JobParameterDef;
  value: unknown;
  onChange: (value: unknown) => void;
  disabled: boolean;
}

function ParameterField({ def, value, onChange, disabled }: ParameterFieldProps) {
  const defaultPlaceholder = def.defaultValue !== null && def.defaultValue !== undefined
    ? String(def.defaultValue)
    : undefined;

  const labelEl = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
      <span style={{
        fontSize: 'var(--font-size-sm)',
        fontWeight: 600,
        color: 'var(--color-text-primary)',
        textTransform: 'uppercase',
        letterSpacing: '0.025em',
      }}>
        {def.name}{def.required ? ' *' : ''}
      </span>
      {def.description && (
        <span style={{
          fontSize: 'var(--font-size-xs, 0.75rem)',
          color: 'var(--color-text-secondary)',
          fontWeight: 400,
        }}>
          {def.description}
        </span>
      )}
    </div>
  );

  const wrapperStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  };

  switch (def.type) {
    case 'BOOLEAN':
      return (
        <label style={{ ...wrapperStyle, cursor: disabled ? 'default' : 'pointer' }}>
          {labelEl}
          <input
            type="checkbox"
            checked={Boolean(value)}
            onChange={e => onChange(e.target.checked)}
            disabled={disabled}
            style={{ width: 'auto' }}
          />
        </label>
      );

    case 'INTEGER':
      return (
        <label style={wrapperStyle}>
          {labelEl}
          <input
            className="param-input"
            type="number"
            value={value !== null && value !== undefined ? String(value) : ''}
            onChange={e => onChange(e.target.value === '' ? null : parseInt(e.target.value))}
            disabled={disabled}
            placeholder={defaultPlaceholder ?? ''}
          />
        </label>
      );

    case 'DATE':
      return (
        <label style={wrapperStyle}>
          {labelEl}
          <input
            className="param-input"
            type="date"
            value={value !== null && value !== undefined ? String(value) : ''}
            onChange={e => onChange(e.target.value || null)}
            disabled={disabled}
          />
        </label>
      );

    case 'ENUM':
      return (
        <label style={wrapperStyle}>
          {labelEl}
          <select
            className="param-input"
            value={value !== null && value !== undefined ? String(value) : ''}
            onChange={e => onChange(e.target.value || null)}
            disabled={disabled}
          >
            {!def.required && <option value="">-- keine Auswahl --</option>}
            {def.enumValues?.map(ev => (
              <option key={ev} value={ev}>{ev}</option>
            ))}
          </select>
        </label>
      );

    case 'STRING':
    case 'PATTERN':
    default:
      return (
        <label style={wrapperStyle}>
          {labelEl}
          <input
            className="param-input"
            type="text"
            value={value !== null && value !== undefined ? String(value) : ''}
            onChange={e => onChange(e.target.value)}
            disabled={disabled}
            placeholder={defaultPlaceholder ?? ''}
          />
        </label>
      );
  }
}
