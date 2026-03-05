export type Dimension = 'QUARTER_HOUR' | 'HOUR' | 'DAY' | 'MONTH' | 'YEAR';

export interface TimeSeriesHeaderResponse {
  tsId: number;
  tsKey: string;
  dimension: Dimension;
  unit: string;
  currency: string | null;
  objectId: number | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TimeSeriesValuesResponse {
  start: string;
  end: string;
  dimension: Dimension;
  count: number;
  values: number[];
}

export interface TimeSeriesRow {
  index: number;
  timestampMs: number;
  value: number;
}

export interface WriteValuesRequest {
  date: string;
  values: number[];
}

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData, TValue> {
    flex?: boolean;
  }
}
