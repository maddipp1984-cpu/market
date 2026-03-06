import { useState, useCallback, useMemo } from 'react';
import type { ColumnMeta, ColumnType, FilterCondition } from '../../api/types';
import { Button } from '../Button';
import { iconPlus } from './icons';
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
  onExecute: (conditions: FilterCondition[]) => void;
  onReset: () => void;
  onClose: () => void;
}

const iconChevron = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

const isNullOp = (op: string) => op === 'IS NULL' || op === 'IS NOT NULL';

export function FilterBuilder({ columns, hasActiveFilter, onExecute, onReset, onClose }: FilterBuilderProps) {
  const sortedColumns = useMemo(() => [...columns].sort((a, b) => a.label.localeCompare(b.label, 'de')), [columns]);
  const [conditions, setConditions] = useState<FilterCondition[]>([]);
  const [selectedColumn, setSelectedColumn] = useState('');
  const [selectedOperator, setSelectedOperator] = useState('=');
  const [inputValue, setInputValue] = useState('');
  const [inputValue2, setInputValue2] = useState('');
  const [conjunction, setConjunction] = useState('AND');
  const [sqlOpen, setSqlOpen] = useState(false);

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

  const buildWherePreview = useCallback((conds: FilterCondition[]) => {
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
    onReset();
    onClose();
  }, [onReset, onClose]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAdd();
    }
  }, [handleAdd]);

  const chipLabel = (c: FilterCondition) => {
    const colLabel = columnLabel(c.sqlColumn);
    if (isNullOp(c.operator)) return `${colLabel} ${c.operator}`;
    if (c.operator === 'BETWEEN') return `${colLabel} BETWEEN '${c.value}' AND '${c.value2}'`;
    return `${colLabel} ${c.operator} ${c.value}`;
  };

  const canAdd = selectedColumn && (
    isNullOp(selectedOperator) ||
    (inputValue.trim() && (selectedOperator !== 'BETWEEN' || inputValue2.trim()))
  );

  const hasFilter = conditions.length > 0;
  const showReset = hasFilter || hasActiveFilter;
  const columnLabel = (sqlCol: string) => columns.find(c => c.sqlColumn === sqlCol)?.label ?? sqlCol;

  const isDate = columnType === 'DATE';
  const isBetween = selectedOperator === 'BETWEEN';
  const showValueInput = !isNullOp(selectedOperator);

  return (
    <div className="filter-builder">
      {/* Titel */}
      <div className="filter-builder-title">
        <span>Filter</span>
        <button className="filter-builder-close" onClick={onClose} aria-label="Schliessen">&times;</button>
      </div>
      {/* Eingabezeile */}
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

      {/* Wert-Eingabe */}
      {showValueInput && (
        isDate ? (
          isBetween ? (
            <div className="filter-between-row">
              <input
                type="datetime-local"
                className="filter-date-input"
                value={inputValue}
                onChange={e => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
              />
              <span className="filter-between-label">bis</span>
              <input
                type="datetime-local"
                className="filter-date-input"
                value={inputValue2}
                onChange={e => setInputValue2(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </div>
          ) : (
            <input
              type="datetime-local"
              className="filter-date-input"
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
            />
          )
        ) : isBetween ? (
          <div className="filter-between-row">
            <input
              type="text"
              className="filter-between-input"
              placeholder="Von..."
              value={inputValue}
              onChange={e => setInputValue(e.target.value)}
              onKeyDown={handleKeyDown}
            />
            <span className="filter-between-label">bis</span>
            <input
              type="text"
              className="filter-between-input"
              placeholder="Bis..."
              value={inputValue2}
              onChange={e => setInputValue2(e.target.value)}
              onKeyDown={handleKeyDown}
            />
          </div>
        ) : (
          <textarea
            className="filter-value-textarea"
            placeholder="Wert..."
            value={inputValue}
            onChange={e => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={10}
          />
        )
      )}

      <div className="filter-builder-add">
        <Button variant="ghost" icon onClick={handleAdd} disabled={!canAdd} title="Bedingung hinzufuegen" aria-label="Bedingung hinzufuegen">
          {iconPlus}
        </Button>
      </div>

      {/* Chips */}
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
