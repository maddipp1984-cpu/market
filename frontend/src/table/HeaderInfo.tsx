import type { Dimension, TimeSeriesHeaderResponse } from '../api/types';

interface HeaderInfoProps {
  header: TimeSeriesHeaderResponse;
  rowCount: number;
}

const dimensionLabels: Record<Dimension, string> = {
  QUARTER_HOUR: '15 Minuten',
  HOUR: '1 Stunde',
  DAY: 'Tag',
  MONTH: 'Monat',
  YEAR: 'Jahr',
};

export function HeaderInfo({ header, rowCount }: HeaderInfoProps) {
  return (
    <div className="header-info">
      <div className="header-grid">
        <span className="label">ID:</span>
        <span>{header.tsId}</span>
        <span className="label">Key:</span>
        <span>{header.tsKey}</span>
        <span className="label">Dimension:</span>
        <span>{dimensionLabels[header.dimension]}</span>
        <span className="label">Einheit:</span>
        <span>{header.unit}{header.currency ? ` (${header.currency})` : ''}</span>
        {header.description && (
          <>
            <span className="label">Beschreibung:</span>
            <span>{header.description}</span>
          </>
        )}
        <span className="label">Werte:</span>
        <span>{rowCount.toLocaleString('de-DE')}</span>
      </div>
    </div>
  );
}
