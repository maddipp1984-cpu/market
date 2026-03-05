import type { TimeSeriesHeaderResponse, TimeSeriesValuesResponse, WriteValuesRequest } from './types';

export interface SidebarNodeConfig {
  id: string;
  label: string | null;
  tabType: string | null;
  children: SidebarNodeConfig[] | null;
}

export async function fetchSidebarConfig(signal?: AbortSignal): Promise<SidebarNodeConfig[]> {
  const res = await fetch('/api/config/sidebar', { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchHeader(tsId: number, signal?: AbortSignal): Promise<TimeSeriesHeaderResponse> {
  const res = await fetch(`/api/timeseries/${tsId}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchValues(
  tsId: number,
  start: string,
  end: string,
  signal?: AbortSignal
): Promise<TimeSeriesValuesResponse> {
  const params = new URLSearchParams({ start, end });
  const res = await fetch(`/api/timeseries/${tsId}/values?${params}`, { signal });
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
