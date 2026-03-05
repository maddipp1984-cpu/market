import type { Dimension, MultiSeriesRow } from '../../api/types';

export const dimensionLabels: Record<Dimension, string> = {
  QUARTER_HOUR: '15 Minuten',
  HOUR: '1 Stunde',
  DAY: 'Tag',
  MONTH: 'Monat',
  YEAR: 'Jahr',
};

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

export type AggregationMode = 'sum' | 'avg';

export function toDateStringBerlin(ms: number): string {
  const key = getBucketKey(ms, 'DAY');
  return key; // YYYY-MM-DD in Europe/Berlin
}

export function aggregateMultiRows(
  rows: MultiSeriesRow[],
  edits: Map<string, number>,
  seriesCount: number,
  targetDimension: Dimension,
  modes: AggregationMode[],
): MultiSeriesRow[] {
  interface MultiBucket {
    timestampMs: number;
    sums: number[];
    counts: number[];
  }

  const buckets = new Map<string, MultiBucket>();

  for (const row of rows) {
    const key = getBucketKey(row.timestampMs, targetDimension);
    let bucket = buckets.get(key);
    if (!bucket) {
      bucket = {
        timestampMs: row.timestampMs,
        sums: new Array(seriesCount).fill(0),
        counts: new Array(seriesCount).fill(0),
      };
      buckets.set(key, bucket);
    }
    for (let seriesIdx = 0; seriesIdx < seriesCount; seriesIdx++) {
      const value = edits.get(`${seriesIdx}:${row.index}`) ?? row.values[seriesIdx];
      if (value == null || isNaN(value)) continue;
      bucket.sums[seriesIdx] += value;
      bucket.counts[seriesIdx]++;
    }
  }

  const result: MultiSeriesRow[] = [];
  let index = 0;
  for (const [, bucket] of buckets) {
    const values = bucket.sums.map((sum, i) => {
      if (bucket.counts[i] === 0) return NaN;
      return modes[i] === 'avg' ? sum / bucket.counts[i] : sum;
    });
    result.push({ index: ++index, timestampMs: bucket.timestampMs, values });
  }
  return result;
}
