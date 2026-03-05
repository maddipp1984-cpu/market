import type { Dimension, TimeSeriesHeaderResponse } from '../api/types';

interface HeaderInfoProps {
  header: TimeSeriesHeaderResponse;
}

const dimensionLabels: Record<Dimension, string> = {
  QUARTER_HOUR: '15 Minuten',
  HOUR: '1 Stunde',
  DAY: 'Tag',
  MONTH: 'Monat',
  YEAR: 'Jahr',
};

export function HeaderInfo({ header }: HeaderInfoProps) {
  return (
    <div className="header-info">
      <div className="header-chips">
        <span className="chip"><span className="label">ID:</span> {header.tsId}</span>
        <span className="chip"><span className="label">Key:</span> {header.tsKey}</span>
        <span className="chip"><span className="label">Dimension:</span> {dimensionLabels[header.dimension]}</span>
        <span className="chip"><span className="label">Einheit:</span> {header.unit}{header.currency ? ` (${header.currency})` : ''}</span>
        {header.description && (
          <span className="chip"><span className="label">Beschreibung:</span> {header.description}</span>
        )}
      </div>
    </div>
  );
}
