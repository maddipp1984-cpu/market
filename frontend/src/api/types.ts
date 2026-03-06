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

export interface MultiSeriesRow {
  index: number;
  timestampMs: number;
  values: number[];  // Wert pro Serie, Index = Position in headers[]
}

export interface ObjectResponse {
  objectId: number;
  type: string;
  objectKey: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WriteValuesRequest {
  date: string;
  values: number[];
}

export type ColumnType = 'TEXT' | 'NUMBER' | 'DATE';

export interface ColumnMeta {
  key: string;
  label: string;
  sqlColumn: string;
  type?: ColumnType;
}

export interface FilterCondition {
  sqlColumn: string;
  operator: string;
  value: string;
  value2?: string;
  conjunction?: string;
}

export interface FilterRequest {
  conditions?: FilterCondition[];
}

export interface TableResponse {
  columns: ColumnMeta[];
  data: Record<string, unknown>[];
}

export type PresetScope = 'GLOBAL' | 'USER';

export interface FilterPreset {
  presetId: number;
  pageKey: string;
  userId: string | null;
  name: string;
  conditions: FilterCondition[];
  scope: PresetScope;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFilterPresetRequest {
  pageKey: string;
  name: string;
  conditions: FilterCondition[];
  scope: PresetScope;
}

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData, TValue> {
    flex?: boolean;
  }
}
