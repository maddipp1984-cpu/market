import type { Dimension } from '../api/types';

/**
 * Berechnet den Timestamp für einen Wert an Position index.
 * Nachbau von TimeSeriesSlice.getTimestamp() (Java-Backend).
 *
 * Backend liefert LocalDateTime ohne Offset (z.B. "2026-01-01T00:00:00").
 * Wir parsen das explizit als lokale Zeit (Europe/Berlin).
 */
export function calculateTimestampMs(start: string, dimension: Dimension, index: number): number {
  const base = parseLocalDateTime(start);
  switch (dimension) {
    case 'QUARTER_HOUR':
      return base + index * 15 * 60_000;
    case 'HOUR':
      return base + index * 60 * 60_000;
    case 'DAY': {
      const d = new Date(base);
      d.setDate(d.getDate() + index);
      return d.getTime();
    }
    case 'MONTH': {
      const d = new Date(base);
      d.setMonth(d.getMonth() + index);
      return d.getTime();
    }
    case 'YEAR': {
      const d = new Date(base);
      d.setFullYear(d.getFullYear() + index);
      return d.getTime();
    }
  }
}

/**
 * Parst einen LocalDateTime-String als lokale Zeit.
 * "2026-01-01T00:00:00" → Date in lokaler Zeitzone (nicht UTC).
 */
function parseLocalDateTime(s: string): number {
  const [datePart, timePart] = s.split('T');
  const [y, m, d] = datePart.split('-').map(Number);
  const [h, min, sec] = (timePart ?? '00:00:00').split(':').map(Number);
  return new Date(y, m - 1, d, h || 0, min || 0, sec || 0).getTime();
}

const formatters = new Map<string, Intl.DateTimeFormat>();

function getFormatter(dimension: Dimension): Intl.DateTimeFormat {
  let fmt = formatters.get(dimension);
  if (fmt) return fmt;

  switch (dimension) {
    case 'QUARTER_HOUR':
    case 'HOUR':
      fmt = new Intl.DateTimeFormat('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit',
      });
      break;
    case 'DAY':
      fmt = new Intl.DateTimeFormat('de-DE', {
        day: '2-digit', month: '2-digit', year: 'numeric',
      });
      break;
    case 'MONTH':
      fmt = new Intl.DateTimeFormat('de-DE', {
        month: '2-digit', year: 'numeric',
      });
      break;
    case 'YEAR':
      fmt = new Intl.DateTimeFormat('de-DE', { year: 'numeric' });
      break;
  }
  formatters.set(dimension, fmt);
  return fmt;
}

export function formatTimestamp(timestampMs: number, dimension: Dimension): string {
  const s = getFormatter(dimension).format(timestampMs);
  return s.replace(',', '');
}
