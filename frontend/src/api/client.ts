import type { ObjectResponse, TimeSeriesHeaderResponse, TimeSeriesValuesResponse, WriteValuesRequest, FilterRequest, TableResponse } from './types';

export interface TimingInfo {
  serverMs: number | null;
  totalMs: number;
}

export interface SidebarNodeConfig {
  id: string;
  label: string | null;
  tabType: string | null;
  children: SidebarNodeConfig[] | null;
}

function extractTiming(res: Response, startTime: number): TimingInfo {
  const serverHeader = res.headers.get('X-Response-Time');
  return {
    serverMs: serverHeader ? parseInt(serverHeader, 10) : null,
    totalMs: Math.round(performance.now() - startTime),
  };
}

export async function fetchSidebarConfig(signal?: AbortSignal): Promise<SidebarNodeConfig[]> {
  const res = await fetch('/api/config/sidebar', { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchHeader(tsId: number, signal?: AbortSignal): Promise<{ data: TimeSeriesHeaderResponse; timing: TimingInfo }> {
  const t0 = performance.now();
  const res = await fetch(`/api/timeseries/${tsId}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function fetchValues(
  tsId: number,
  start: string,
  end: string,
  signal?: AbortSignal
): Promise<{ data: TimeSeriesValuesResponse; timing: TimingInfo }> {
  const t0 = performance.now();
  const params = new URLSearchParams({ start, end });
  const res = await fetch(`/api/timeseries/${tsId}/values?${params}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function fetchObjects(signal?: AbortSignal): Promise<{ data: ObjectResponse[]; timing: TimingInfo }> {
  const t0 = performance.now();
  const res = await fetch('/api/objects', { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function fetchTable(
  url: string,
  filter?: FilterRequest,
  signal?: AbortSignal,
): Promise<{ data: TableResponse; timing: TimingInfo }> {
  const t0 = performance.now();
  const hasFilter = filter && filter.conditions && filter.conditions.length > 0;
  const res = hasFilter
    ? await fetch(`${url}/query`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(filter),
        signal,
      })
    : await fetch(url, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function writeDay(tsId: number, req: WriteValuesRequest): Promise<void> {
  const res = await fetch(`/api/timeseries/${tsId}/values`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}
