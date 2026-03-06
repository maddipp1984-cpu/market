import { useState, useCallback, useMemo } from 'react';
import type { ColumnMeta, FilterCondition } from '../../api/types';
import { Button } from '../Button';
import { iconPlus } from './icons';
import './FilterBuilder.css';

const OPERATORS = [
  { value: '=', label: 'gleich (=)' },
  { value: '!=', label: 'ungleich (!=)' },
  { value: '<', label: 'kleiner (<)' },
  { value: '>', label: 'groesser (>)' },
  { value: 'LIKE', label: 'enthaelt (LIKE)' },
  { value: 'IN', label: 'in Liste (IN)' },
];

interface FilterBuilderProps {
  columns: ColumnMeta[];
  hasActiveFilter: boolean;
  onExecute: (conditions: FilterCondition[]) => void;
  onReset: () => void;
  onClose: () => void;
}

const iconChevron = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

export function FilterBuilder({ columns, hasActiveFilter, onExecute, onReset, onClose }: FilterBuilderProps) {
  const sortedColumns = useMemo(() => [...columns].sort((a, b) => a.label.localeCompare(b.label, 'de')), [columns]);
  const [conditions, setConditions] = useState<FilterCondition[]>([]);
  const [selectedColumn, setSelectedColumn] = useState('');
  const [selectedOperator, setSelectedOperator] = useState('=');
  const [inputValue, setInputValue] = useState('');
  const [conjunction, setConjunction] = useState('AND');
  const [sqlOpen, setSqlOpen] = useState(false);

  const buildWherePreview = useCallback((conds: FilterCondition[]) => {
    return conds.map((c, i) => {
      const op = c.operator === 'LIKE' ? `LIKE '%${c.value}%'` :
                 c.operator === 'IN' ? `IN (${c.value})` :
                 `${c.operator} '${c.value}'`;
      const prefix = i > 0 && c.conjunction ? `${c.conjunction} ` : '';
      return `${prefix}${c.sqlColumn} ${op}`;
    }).join(' ');
  }, []);

  const handleAdd = useCallback(() => {
    if (!selectedColumn || !inputValue.trim()) return;
    const newCondition: FilterCondition = {
      sqlColumn: selectedColumn,
      operator: selectedOperator,
      value: inputValue.trim(),
      conjunction: conditions.length > 0 ? conjunction : undefined,
    };
    setConditions([...conditions, newCondition]);
    setInputValue('');
  }, [selectedColumn, selectedOperator, inputValue, conjunction, conditions]);

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
    onReset();
    onClose();
  }, [onReset, onClose]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAdd();
    }
  }, [handleAdd]);

  const hasFilter = conditions.length > 0;
  const showReset = hasFilter || hasActiveFilter;
  const columnLabel = (sqlCol: string) => columns.find(c => c.sqlColumn === sqlCol)?.label ?? sqlCol;

  return (
    <div className="filter-builder">
      {/* Titel */}
      <div className="filter-builder-title">
        <span>Filter</span>
        <button className="filter-builder-close" onClick={onClose} aria-label="Schliessen">&times;</button>
      </div>
      {/* Eingabezeile */}
      <div className="filter-builder-row">
        <select value={selectedColumn} onChange={e => setSelectedColumn(e.target.value)}>
          <option value="">Spalte...</option>
          {sortedColumns.map(col => (
            <option key={col.sqlColumn} value={col.sqlColumn}>{col.label}</option>
          ))}
        </select>
        <select value={selectedOperator} onChange={e => setSelectedOperator(e.target.value)}>
          {OPERATORS.map(op => (
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
      <textarea
        className="filter-value-textarea"
        placeholder="Wert..."
        value={inputValue}
        onChange={e => setInputValue(e.target.value)}
        onKeyDown={handleKeyDown}
        rows={10}
      />
      <div className="filter-builder-add">
        <Button variant="ghost" icon onClick={handleAdd} disabled={!selectedColumn || !inputValue.trim()} title="Bedingung hinzufuegen" aria-label="Bedingung hinzufuegen">
          {iconPlus}
        </Button>
      </div>

      {/* Chips */}
      {conditions.length > 0 && (
        <div className="filter-chips">
          {conditions.map((c, i) => (
            <span key={i} className="filter-chip">
              {c.conjunction && <span className="filter-chip-conjunction">{c.conjunction}</span>}
              {columnLabel(c.sqlColumn)} {c.operator} {c.value}
              <button className="filter-chip-remove" onClick={() => handleRemove(i)} aria-label="Entfernen">&times;</button>
            </span>
          ))}
        </div>
      )}

      {/* SQL-Vorschau */}
      <div className="filter-sql-section">
        <button
          className={`filter-sql-toggle ${sqlOpen ? 'open' : ''}`}
          onClick={() => setSqlOpen(!sqlOpen)}
        >
          {iconChevron} SQL-Ansicht
        </button>
        {sqlOpen && (
          <textarea
            className="filter-sql-textarea"
            value={buildWherePreview(conditions)}
            readOnly
            placeholder="WHERE-Bedingung..."
          />
        )}
      </div>

      {/* Aktionsleiste */}
      {(hasFilter || showReset) && (
        <div className="filter-actions">
          {hasFilter && <Button variant="primary" onClick={handleExecute}>Ausfuehren</Button>}
          {showReset && <Button variant="ghost" onClick={handleReset}>Zuruecksetzen</Button>}
        </div>
      )}
    </div>
  );
}
