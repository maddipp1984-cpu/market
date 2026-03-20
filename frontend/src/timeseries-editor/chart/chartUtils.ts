import type { MultiSeriesRow, TimeSeriesHeaderResponse } from '../../api/types';

export const CHART_POINT_OPTIONS = [
  { value: 5000, label: '5.000' },
  { value: 10000, label: '10.000' },
  { value: 25000, label: '25.000' },
  { value: 50000, label: '50.000' },
  { value: 0, label: 'Alle' },
];

export const DEFAULT_MAX_POINTS = 5000;

export interface DownsampledPoint {
  index: number;
  timestampMs: number;
  values: (number | null)[];
}

/**
 * Downsamples rows to maxPoints by taking every Nth point.
 * Converts NaN to null for chart libraries.
 * maxPoints=0 means no limit (all points).
 */
export function downsampleForChart(
  rows: MultiSeriesRow[],
  headers: TimeSeriesHeaderResponse[],
  maxPoints = DEFAULT_MAX_POINTS,
): DownsampledPoint[] {
  const limit = maxPoints > 0 ? maxPoints : rows.length;
  const step = rows.length > limit ? Math.ceil(rows.length / limit) : 1;
  const result: DownsampledPoint[] = [];
  for (let i = 0; i < rows.length; i += step) {
    const r = rows[i];
    result.push({
      index: i,
      timestampMs: r.timestampMs,
      values: headers.map((_, s) => {
        const v = r.values[s];
        return isNaN(v) ? null : v;
      }),
    });
  }
  return result;
}
