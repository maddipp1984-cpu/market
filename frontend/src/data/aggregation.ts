import type { Dimension, TimeSeriesRow } from '../api/types';

const DIMENSION_RANK: Record<Dimension, number> = {
  QUARTER_HOUR: 0, HOUR: 1, DAY: 2, MONTH: 3, YEAR: 4,
};

export function getAvailableDimensions(original: Dimension): Dimension[] {
  const rank = DIMENSION_RANK[original];
  return (['QUARTER_HOUR', 'HOUR', 'DAY', 'MONTH', 'YEAR'] as Dimension[])
    .filter(d => DIMENSION_RANK[d] >= rank);
}

const partsFormatter = new Intl.DateTimeFormat('de-DE', {
  timeZone: 'Europe/Berlin',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

function getBucketKey(timestampMs: number, dimension: Dimension): string {
  const parts = partsFormatter.formatToParts(new Date(timestampMs));
  const get = (type: string) => parts.find(p => p.type === type)!.value;
  switch (dimension) {
    case 'YEAR': return get('year');
    case 'MONTH': return `${get('year')}-${get('month')}`;
    case 'DAY': return `${get('year')}-${get('month')}-${get('day')}`;
    case 'HOUR': return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}`;
    case 'QUARTER_HOUR': return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
  }
}

export function aggregateRows(
  rows: TimeSeriesRow[],
  edits: Map<number, number>,
  targetDimension: Dimension,
): TimeSeriesRow[] {
  const buckets = new Map<string, { timestampMs: number; sum: number }>();

  for (const row of rows) {
    const value = edits.get(row.index) ?? row.value;
    if (value == null || isNaN(value)) continue;
    const key = getBucketKey(row.timestampMs, targetDimension);
    const bucket = buckets.get(key);
    if (bucket) {
      bucket.sum += value;
    } else {
      buckets.set(key, { timestampMs: row.timestampMs, sum: value });
    }
  }

  const result: TimeSeriesRow[] = [];
  let index = 0;
  for (const [, bucket] of buckets) {
    result.push({ index: index++, timestampMs: bucket.timestampMs, value: bucket.sum });
  }
  return result;
}
