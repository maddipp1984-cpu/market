import type { MultiSeriesRow, TimeSeriesHeaderResponse } from '../../api/types';

const DEFAULT_MAX_POINTS = 5000;

export interface DownsampledPoint {
  index: number;
  timestampMs: number;
  values: (number | null)[];
}

/**
 * Downsamples rows to maxPoints by taking every Nth point.
 * Converts NaN to null for chart libraries.
 */
export function downsampleForChart(
  rows: MultiSeriesRow[],
  headers: TimeSeriesHeaderResponse[],
  maxPoints = DEFAULT_MAX_POINTS,
): DownsampledPoint[] {
  const step = rows.length > maxPoints ? Math.ceil(rows.length / maxPoints) : 1;
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
