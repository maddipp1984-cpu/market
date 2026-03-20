import type { MultiSeriesRow, TimeSeriesHeaderResponse, Dimension } from '../../api/types';

export interface ChartProps {
  rows: MultiSeriesRow[];
  headers: TimeSeriesHeaderResponse[];
  dimension: Dimension;
  maxPoints?: number;  // 0 = alle Punkte
}

export const SERIES_COLORS = [
  '#2563eb', // blue
  '#dc2626', // red
  '#16a34a', // green
  '#d97706', // amber
  '#7c3aed', // violet
  '#0891b2', // cyan
  '#db2777', // pink
  '#65a30d', // lime
  '#ea580c', // orange
  '#6366f1', // indigo
];
