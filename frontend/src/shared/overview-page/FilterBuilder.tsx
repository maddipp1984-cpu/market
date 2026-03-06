import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import type { ColumnMeta, ColumnType, FilterCondition, FilterPreset, PresetScope } from '../../api/types';
import { Button } from '../Button';
import { TreeView, type TreeNode } from '../TreeView';
import { iconPlus, iconPlay, iconReset, iconSave, iconOverwrite, iconClose, iconChevron, iconStar, iconStarFilled, iconTrash } from './icons';
import './FilterBuilder.css';

const OPERATORS_BY_TYPE: Record<ColumnType, { value: string; label: string }[]> = {
  TEXT: [
    { value: '=', label: 'gleich (=)' },
    { value: '!=', label: 'ungleich (!=)' },
    { value: 'LIKE', label: 'enthaelt (LIKE)' },
    { value: 'IN', label: 'in Liste (IN)' },
    { value: 'IS NULL', label: 'ist leer (IS NULL)' },
    { value: 'IS NOT NULL', label: 'ist nicht leer (IS NOT NULL)' },
  ],
  NUMBER: [
    { value: '=', label: 'gleich (=)' },
    { value: '!=', label: 'ungleich (!=)' },
    { value: '<', label: 'kleiner (<)' },
    { value: '>', label: 'groesser (>)' },
    { value: '<=', label: 'kleiner gleich (<=)' },
    { value: '>=', label: 'groesser gleich (>=)' },
    { value: 'BETWEEN', label: 'zwischen (BETWEEN)' },
    { value: 'IN', label: 'in Liste (IN)' },
    { value: 'IS NULL', label: 'ist leer (IS NULL)' },
    { value: 'IS NOT NULL', label: 'ist nicht leer (IS NOT NULL)' },
  ],
  DATE: [
    { value: '=', label: 'gleich (=)' },
    { value: '!=', label: 'ungleich (!=)' },
    { value: '<', label: 'vor (<)' },
    { value: '>', label: 'nach (>)' },
    { value: '<=', label: 'bis inkl. (<=)' },
    { value: '>=', label: 'ab inkl. (>=)' },
    { value: 'BETWEEN', label: 'zwischen (BETWEEN)' },
    { value: 'IS NULL', label: 'ist leer (IS NULL)' },
    { value: 'IS NOT NULL', label: 'ist nicht leer (IS NOT NULL)' },
  ],
};

interface FilterBuilderProps {
  columns: ColumnMeta[];
  hasActiveFilter: boolean;
  activeConditions?: FilterCondition[];
  onExecute: (conditions: FilterCondition[]) => void;
  onReset: () => void;
  onClose: () => void;
  presets?: FilterPreset[];
  onSavePreset?: (name: string, conditions: FilterCondition[], scope: PresetScope) => Promise<void>;
  onUpdatePreset?: (presetId: number, name: string, conditions: FilterCondition[], scope: PresetScope) => Promise<void>;
  onDeletePreset?: (presetId: number) => Promise<void>;
  onSetDefault?: (presetId: number) => Promise<void>;
  onClearDefault?: (presetId: number) => Promise<void>;
}

const isNullOp = (op: string) => op === 'IS NULL' || op === 'IS NOT NULL';

function buildWherePreview(conds: FilterCondition[]) {
  return conds.map((c, i) => {
    let op: string;
    if (isNullOp(c.operator)) {
      op = c.operator;
    } else if (c.operator === 'LIKE') {
      op = `LIKE '%${c.value}%'`;
    } else if (c.operator === 'IN') {
      op = `IN (${c.value})`;
    } else if (c.operator === 'BETWEEN') {
      op = `BETWEEN '${c.value}' AND '${c.value2}'`;
    } else {
      op = `${c.operator} '${c.value}'`;
    }
    const prefix = i > 0 && c.conjunction ? `${c.conjunction} ` : '';
    return `${prefix}${c.sqlColumn} ${op}`;
  }).join(' ');
}

export function FilterBuilder({ columns, hasActiveFilter, activeConditions, onExecute, onReset, onClose, presets, onSavePreset, onUpdatePreset, onDeletePreset, onSetDefault, onClearDefault }: FilterBuilderProps) {
  const sortedColumns = useMemo(() => [...columns].sort((a, b) => a.label.localeCompare(b.label, 'de')), [columns]);
  const [conditions, setConditions] = useState<FilterCondition[]>([]);
  const [presetError, setPresetError] = useState<string | null>(null);
  const errorTimerRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (activeConditions) {
      setConditions(activeConditions);
    }
  }, [activeConditions]);

  useEffect(() => {
    return () => {
      if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
    };
  }, []);

  const showPresetError = useCallback((msg: string) => {
    setPresetError(msg);
    if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
    errorTimerRef.current = setTimeout(() => setPresetError(null), 3000);
  }, []);
  const [selectedColumn, setSelectedColumn] = useState('');
  const [selectedOperator, setSelectedOperator] = useState('=');
  const [inputValue, setInputValue] = useState('');
  const [inputValue2, setInputValue2] = useState('');
  const [conjunction, setConjunction] = useState('AND');
  const [sqlOpen, setSqlOpen] = useState(false);
  const [selectedPresetId, setSelectedPresetId] = useState<number | null>(null);
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveName, setSaveName] = useState('');
  const [saveScope, setSaveScope] = useState<PresetScope>('USER');

  const columnType: ColumnType = useMemo(() => {
    return columns.find(c => c.sqlColumn === selectedColumn)?.type ?? 'TEXT';
  }, [columns, selectedColumn]);

  const operators = useMemo(() => OPERATORS_BY_TYPE[columnType], [columnType]);

  const handleColumnChange = useCallback((col: string) => {
    setSelectedColumn(col);
    const newType = columns.find(c => c.sqlColumn === col)?.type ?? 'TEXT';
    const newOps = OPERATORS_BY_TYPE[newType];
    if (!newOps.some(op => op.value === selectedOperator)) {
      setSelectedOperator(newOps[0].value);
    }
    setInputValue('');
    setInputValue2('');
  }, [columns, selectedOperator]);

  const handleOperatorChange = useCallback((op: string) => {
    setSelectedOperator(op);
    setInputValue('');
    setInputValue2('');
  }, []);

  const handleAdd = useCallback(() => {
    if (!selectedColumn) return;
    if (!isNullOp(selectedOperator)) {
      if (!inputValue.trim()) return;
      if (selectedOperator === 'BETWEEN' && !inputValue2.trim()) return;
    }
    const newCondition: FilterCondition = {
      sqlColumn: selectedColumn,
      operator: selectedOperator,
      value: isNullOp(selectedOperator) ? '' : inputValue.trim(),
      value2: selectedOperator === 'BETWEEN' ? inputValue2.trim() : undefined,
      conjunction: conditions.length > 0 ? conjunction : undefined,
    };
    setConditions([...conditions, newCondition]);
    setInputValue('');
    setInputValue2('');
  }, [selectedColumn, selectedOperator, inputValue, inputValue2, conjunction, conditions]);

  const handleRemove = useCallback((index: number) => {
    const updated = conditions.filter((_, i) => i !== index);
    if (updated.length > 0 && updated[0].conjunction) {
      updated[0] = { ...updated[0], conjunction: undefined };
    }
    setConditions(updated);
  }, [conditions]);

  const handleExecute = useCallback(() => {
    if (conditions.length > 0) {
      onExecute(conditions);
      onClose();
    }
  }, [conditions, onExecute, onClose]);

  const handleReset = useCallback(() => {
    setConditions([]);
    setSelectedPresetId(null);
    onReset();
    onClose();
  }, [onReset, onClose]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAdd();
    }
  }, [handleAdd]);

  const columnLabel = (sqlCol: string) => columns.find(c => c.sqlColumn === sqlCol)?.label ?? sqlCol;

  const chipLabel = (c: FilterCondition) => {
    const label = columnLabel(c.sqlColumn);
    if (isNullOp(c.operator)) return `${label} ${c.operator}`;
    if (c.operator === 'BETWEEN') return `${label} BETWEEN '${c.value}' AND '${c.value2}'`;
    return `${label} ${c.operator} ${c.value}`;
  };

  const canAdd = selectedColumn && (
    isNullOp(selectedOperator) ||
    (inputValue.trim() && (selectedOperator !== 'BETWEEN' || inputValue2.trim()))
  );

  const hasFilter = conditions.length > 0;
  const showReset = hasFilter || hasActiveFilter;

  const isDate = columnType === 'DATE';
  const isBetween = selectedOperator === 'BETWEEN';
  const showValueInput = !isNullOp(selectedOperator);

  // Preset tree data
  const presetTreeData: TreeNode[] = useMemo(() => {
    const globalPresets = (presets ?? []).filter(p => p.scope === 'GLOBAL');
    const userPresets = (presets ?? []).filter(p => p.scope === 'USER');
    return [
      {
        id: 'global',
        label: 'Global',
        children: globalPresets.map(p => ({
          id: String(p.presetId),
          label: (p.isDefault ? '\u2605 ' : '') + p.name,
        })),
      },
      {
        id: 'user',
        label: 'Eigene',
        children: userPresets.map(p => ({
          id: String(p.presetId),
          label: (p.isDefault ? '\u2605 ' : '') + p.name,
        })),
      },
    ];
  }, [presets]);

  const handlePresetSelect = useCallback((node: TreeNode) => {
    const id = Number(node.id);
    if (isNaN(id)) return;
    setSelectedPresetId(id);
    const preset = presets?.find(p => p.presetId === id);
    if (preset) {
      setConditions(preset.conditions);
    }
  }, [presets]);

  const selectedPreset = presets?.find(p => p.presetId === selectedPresetId) ?? null;

  const handleOverwrite = useCallback(async () => {
    if (!selectedPreset || !onUpdatePreset) return;
    try {
      await onUpdatePreset(selectedPreset.presetId, selectedPreset.name, conditions, selectedPreset.scope);
    } catch (e) {
      console.error('Preset update failed:', e);
      showPresetError('Preset konnte nicht aktualisiert werden');
    }
  }, [selectedPreset, conditions, onUpdatePreset, showPresetError]);

  const handleToggleDefault = useCallback(async () => {
    if (!selectedPreset) return;
    try {
      if (selectedPreset.isDefault) {
        await onClearDefault?.(selectedPreset.presetId);
      } else {
        await onSetDefault?.(selectedPreset.presetId);
      }
    } catch (e) {
      console.error('Preset default toggle failed:', e);
      showPresetError('Default konnte nicht geaendert werden');
    }
  }, [selectedPreset, onSetDefault, onClearDefault, showPresetError]);

  const handleDeletePreset = useCallback(async () => {
    if (!selectedPreset || !onDeletePreset) return;
    try {
      await onDeletePreset(selectedPreset.presetId);
      setSelectedPresetId(null);
    } catch (e) {
      console.error('Preset delete failed:', e);
      showPresetError('Preset konnte nicht geloescht werden');
    }
  }, [selectedPreset, onDeletePreset, showPresetError]);

  const handleSave = useCallback(async () => {
    if (!saveName.trim() || !onSavePreset) return;
    try {
      await onSavePreset(saveName.trim(), conditions, saveScope);
      setSaveName('');
      setSaveOpen(false);
    } catch (e) {
      console.error('Preset save failed:', e);
      showPresetError('Preset konnte nicht gespeichert werden');
    }
  }, [saveName, conditions, saveScope, onSavePreset, showPresetError]);

  const renderValueInput = () => {
    if (!showValueInput) return null;
    if (isDate && isBetween) {
      return (
        <div className="filter-between-row">
          <input type="datetime-local" className="filter-input" value={inputValue} onChange={e => setInputValue(e.target.value)} onKeyDown={handleKeyDown} />
          <span className="filter-between-label">bis</span>
          <input type="datetime-local" className="filter-input" value={inputValue2} onChange={e => setInputValue2(e.target.value)} onKeyDown={handleKeyDown} />
        </div>
      );
    }
    if (isDate) {
      return <input type="datetime-local" className="filter-input" value={inputValue} onChange={e => setInputValue(e.target.value)} onKeyDown={handleKeyDown} />;
    }
    if (isBetween) {
      return (
        <div className="filter-between-row">
          <input type="text" className="filter-input" placeholder="Von..." value={inputValue} onChange={e => setInputValue(e.target.value)} onKeyDown={handleKeyDown} />
          <span className="filter-between-label">bis</span>
          <input type="text" className="filter-input" placeholder="Bis..." value={inputValue2} onChange={e => setInputValue2(e.target.value)} onKeyDown={handleKeyDown} />
        </div>
      );
    }
    return (
      <textarea
        className="filter-input filter-value-textarea"
        placeholder="Wert..."
        value={inputValue}
        onChange={e => setInputValue(e.target.value)}
        onKeyDown={handleKeyDown}
        rows={selectedOperator === 'IN' ? 6 : 2}
      />
    );
  };

  return (
    <div className="filter-builder">
      {/* Toolbar */}
      <div className="filter-toolbar">
        <Button variant="ghost" icon onClick={handleExecute} disabled={!hasFilter} title="Filter ausfuehren" aria-label="Filter ausfuehren">
          {iconPlay}
        </Button>
        <Button variant="ghost" icon onClick={handleReset} disabled={!showReset} title="Filter zuruecksetzen" aria-label="Filter zuruecksetzen">
          {iconReset}
        </Button>
        <Button variant="ghost" icon onClick={() => setSaveOpen(o => !o)} disabled={!hasFilter} title="Neues Preset speichern" aria-label="Neues Preset speichern">
          {iconSave}
        </Button>
        <Button variant="ghost" icon onClick={handleOverwrite} disabled={!hasFilter || !selectedPreset} title="Preset ueberschreiben" aria-label="Preset ueberschreiben">
          {iconOverwrite}
        </Button>
        <Button variant="ghost" icon onClick={handleToggleDefault} disabled={!selectedPreset} title={selectedPreset?.isDefault ? 'Default entfernen' : 'Als Default setzen'} aria-label="Default-Toggle">
          {selectedPreset?.isDefault ? iconStarFilled : iconStar}
        </Button>
        <Button variant="ghost" icon onClick={handleDeletePreset} disabled={!selectedPreset} title="Preset loeschen" aria-label="Preset loeschen" className="filter-toolbar-danger">
          {iconTrash}
        </Button>
        <span className="filter-toolbar-spacer" />
        <Button variant="ghost" icon onClick={onClose} title="Schliessen" aria-label="Schliessen">
          {iconClose}
        </Button>
      </div>

      {/* Body: Tree + Inputs */}
      <div className="filter-body">
        {/* Preset Tree */}
        <div className="filter-preset-tree">
          <TreeView
            data={presetTreeData}
            variant="light"
            defaultExpanded={['global', 'user']}
            onSelect={handlePresetSelect}
            selectOnClick
            selectedId={selectedPresetId != null ? String(selectedPresetId) : null}
          />
        </div>

        {/* Filter Inputs */}
        <div className="filter-inputs">
          {presetError && <div className="filter-preset-error">{presetError}</div>}

          <div className="filter-builder-row">
            <select value={selectedColumn} onChange={e => handleColumnChange(e.target.value)}>
              <option value="">Spalte...</option>
              {sortedColumns.map(col => (
                <option key={col.sqlColumn} value={col.sqlColumn}>{col.label}</option>
              ))}
            </select>
            <select value={selectedOperator} onChange={e => handleOperatorChange(e.target.value)}>
              {operators.map(op => (
                <option key={op.value} value={op.value}>{op.label}</option>
              ))}
            </select>
            {conditions.length > 0 && (
              <select value={conjunction} onChange={e => setConjunction(e.target.value)}>
                <option value="AND">AND</option>
                <option value="OR">OR</option>
              </select>
            )}
          </div>

          {renderValueInput()}

          <div className="filter-builder-add">
            <Button variant="ghost" icon onClick={handleAdd} disabled={!canAdd} title="Bedingung hinzufuegen" aria-label="Bedingung hinzufuegen">
              {iconPlus}
            </Button>
          </div>

          {conditions.length > 0 && (
            <div className="filter-chips">
              {conditions.map((c, i) => (
                <span key={i} className="filter-chip">
                  {c.conjunction && <span className="filter-chip-conjunction">{c.conjunction}</span>}
                  {chipLabel(c)}
                  <button className="filter-chip-remove" onClick={() => handleRemove(i)} aria-label="Entfernen">&times;</button>
                </span>
              ))}
            </div>
          )}

          <div className="filter-sql-section">
            <button
              className={`filter-sql-toggle ${sqlOpen ? 'open' : ''}`}
              onClick={() => setSqlOpen(!sqlOpen)}
            >
              {iconChevron} SQL-Ansicht
            </button>
            {sqlOpen && (
              <textarea
                className="filter-input filter-sql-textarea"
                value={buildWherePreview(conditions)}
                readOnly
                placeholder="WHERE-Bedingung..."
              />
            )}
          </div>
        </div>
      </div>

      {/* Save-Panel */}
      {saveOpen && onSavePreset && (
        <div className="filter-preset-save">
          <div className="filter-preset-save-row">
            <input
              type="text"
              className="filter-input"
              placeholder="Preset-Name..."
              value={saveName}
              onChange={e => setSaveName(e.target.value)}
              onKeyDown={async e => {
                if (e.key === 'Enter' && saveName.trim()) {
                  await handleSave();
                }
              }}
            />
            <select className="filter-input" value={saveScope} onChange={e => setSaveScope(e.target.value as PresetScope)}>
              <option value="USER">Nur fuer mich</option>
              <option value="GLOBAL">Fuer alle</option>
            </select>
            <Button variant="primary" disabled={!saveName.trim()} onClick={handleSave}>
              Speichern
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
