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

// Currency
export interface CurrencyDto {
  id: number | null;
  isoCode: string;
  description: string;
}

// Business Partner
export interface ContactPersonDto {
  id: number | null;
  firstName: string;
  lastName: string;
  email: string | null;
  phone: string | null;
  street: string | null;
  zipCode: string | null;
  city: string | null;
  functions: string[];
}

export interface BusinessPartnerDto {
  id: number | null;
  shortName: string;
  name: string;
  notes: string | null;
  contacts: ContactPersonDto[];
}

// Scheduling
export interface BatchJobDto {
  id: number | null;
  jobKey: string;
  name: string;
  description: string | null;
  jobClass: string;
  scheduleType: 'NONE' | 'CRON' | 'INTERVAL';
  cronExpression: string | null;
  intervalSeconds: number | null;
  enabled: boolean;
}

export interface JobExecutionDto {
  id: number;
  startTime: string;
  endTime: string | null;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  errorMessage: string | null;
  recordsAffected: number | null;
  logFile: string | null;
  triggeredBy: string;
}

declare module '@tanstack/react-table' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface ColumnMeta<TData, TValue> {
    flex?: boolean;
  }
}
