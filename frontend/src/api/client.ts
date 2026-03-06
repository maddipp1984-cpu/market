import type { ObjectResponse, TimeSeriesHeaderResponse, TimeSeriesValuesResponse, WriteValuesRequest, FilterRequest, TableResponse, FilterPreset, CreateFilterPresetRequest } from './types';

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

export async function fetchFilterPresets(pageKey: string, signal?: AbortSignal): Promise<FilterPreset[]> {
  const res = await fetch(`/api/filter-presets?pageKey=${encodeURIComponent(pageKey)}`, {
    headers: { 'X-User-Id': 'default' },
    signal,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function createFilterPreset(req: CreateFilterPresetRequest): Promise<{ presetId: number }> {
  const res = await fetch('/api/filter-presets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': 'default' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function updateFilterPreset(presetId: number, req: CreateFilterPresetRequest): Promise<void> {
  const res = await fetch(`/api/filter-presets/${presetId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': 'default' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function deleteFilterPreset(presetId: number): Promise<void> {
  const res = await fetch(`/api/filter-presets/${presetId}`, {
    method: 'DELETE',
    headers: { 'X-User-Id': 'default' },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function setPresetDefault(presetId: number): Promise<void> {
  const res = await fetch(`/api/filter-presets/${presetId}/default`, {
    method: 'PUT',
    headers: { 'X-User-Id': 'default' },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function clearPresetDefault(presetId: number): Promise<void> {
  const res = await fetch(`/api/filter-presets/${presetId}/default`, {
    method: 'DELETE',
    headers: { 'X-User-Id': 'default' },
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function fetchDefaultPreset(pageKey: string, signal?: AbortSignal): Promise<FilterPreset | null> {
  const res = await fetch(`/api/filter-presets/default?pageKey=${encodeURIComponent(pageKey)}`, {
    headers: { 'X-User-Id': 'default' },
    signal,
  });
  if (res.status === 404) return null;
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
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
