import type { ObjectResponse, TimeSeriesHeaderResponse, TimeSeriesValuesResponse, WriteValuesRequest, FilterRequest, TableResponse, FilterPreset, CreateFilterPresetRequest, BusinessPartnerDto } from './types';
import type { EffectivePermission } from '../auth/AuthContext';
import keycloak from '../auth/keycloak';

async function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  await keycloak.updateToken(30);
  const headers = new Headers(init?.headers);
  if (keycloak.token) {
    headers.set('Authorization', `Bearer ${keycloak.token}`);
  }
  return fetch(input, { ...init, headers });
}

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
  const res = await authFetch('/api/config/sidebar', { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function fetchHeader(tsId: number, signal?: AbortSignal): Promise<{ data: TimeSeriesHeaderResponse; timing: TimingInfo }> {
  const t0 = performance.now();
  const res = await authFetch(`/api/timeseries/${tsId}`, { signal });
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
  const res = await authFetch(`/api/timeseries/${tsId}/values?${params}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function fetchObjects(signal?: AbortSignal): Promise<{ data: ObjectResponse[]; timing: TimingInfo }> {
  const t0 = performance.now();
  const res = await authFetch('/api/objects', { signal });
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
    ? await authFetch(`${url}/query`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(filter),
        signal,
      })
    : await authFetch(url, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  const data = await res.json();
  return { data, timing: extractTiming(res, t0) };
}

export async function fetchFilterPresets(pageKey: string, signal?: AbortSignal): Promise<FilterPreset[]> {
  const res = await authFetch(`/api/filter-presets?pageKey=${encodeURIComponent(pageKey)}`, {

    signal,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function createFilterPreset(req: CreateFilterPresetRequest): Promise<{ presetId: number }> {
  const res = await authFetch('/api/filter-presets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function updateFilterPreset(presetId: number, req: CreateFilterPresetRequest): Promise<void> {
  const res = await authFetch(`/api/filter-presets/${presetId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function deleteFilterPreset(presetId: number): Promise<void> {
  const res = await authFetch(`/api/filter-presets/${presetId}`, {
    method: 'DELETE',

  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function setPresetDefault(presetId: number, pageKey: string, scope: string): Promise<void> {
  const res = await authFetch(`/api/filter-presets/${presetId}/default?pageKey=${encodeURIComponent(pageKey)}&scope=${encodeURIComponent(scope)}`, {
    method: 'PUT',

  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function clearPresetDefault(presetId: number): Promise<void> {
  const res = await authFetch(`/api/filter-presets/${presetId}/default`, {
    method: 'DELETE',

  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

export async function fetchDefaultPreset(pageKey: string, signal?: AbortSignal): Promise<FilterPreset | null> {
  const res = await authFetch(`/api/filter-presets/default?pageKey=${encodeURIComponent(pageKey)}`, {

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
  const res = await authFetch(`/api/timeseries/${tsId}/values`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}

// ==================== Permissions ====================

export async function fetchMyPermissions(): Promise<{ userId: string; isAdmin: boolean; permissions: EffectivePermission[] }> {
  const res = await authFetch('/api/permissions/me');
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

// ==================== Admin API ====================

async function adminFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await authFetch(`/api/admin${path}`, init);
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json();
}

export interface AdminUser {
  userId: string;
  username: string;
  email: string | null;
  isAdmin: boolean;
  createdAt: string;
  groupCount: number;
}

export interface AdminGroup {
  groupId: number;
  name: string;
  description: string | null;
  memberCount: number;
}

export interface AdminGroupDetail extends AdminGroup {
  members: { userId: string; username: string; email: string | null }[];
  permissions: { permissionId: number; groupId: number; resourceKey: string; objectTypeId: number | null; canRead: boolean; canWrite: boolean; canDelete: boolean }[];
  fieldRestrictions: { restrictionId: number; groupId: number; resourceKey: string; fieldKey: string; objectTypeId: number | null }[];
}

export interface AdminResource {
  resourceKey: string;
  label: string;
  hasTypeScope: boolean;
}

// Users
export const adminGetUsers = () => adminFetch<AdminUser[]>('/users');
export const adminCreateUser = (username: string, email: string, password: string) =>
  adminFetch<{ userId: string }>('/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, email, password }) });
export const adminSetAdmin = (userId: string, isAdmin: boolean) =>
  adminFetch<void>(`/users/${userId}/admin`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ isAdmin }) });
export const adminSetEnabled = (userId: string, enabled: boolean) =>
  adminFetch<void>(`/users/${userId}/enabled`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled }) });
export const adminResetPassword = (userId: string, password: string) =>
  adminFetch<void>(`/users/${userId}/password`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password }) });
export const adminGetUserEffective = (userId: string) =>
  adminFetch<{ userId: string; isAdmin: boolean; permissions: EffectivePermission[] }>(`/users/${userId}/effective`);

// Groups
export const adminGetGroups = () => adminFetch<AdminGroup[]>('/groups');
export const adminGetGroup = (id: number) => adminFetch<AdminGroupDetail>(`/groups/${id}`);
export const adminCreateGroup = (name: string, description: string) =>
  adminFetch<{ groupId: number }>('/groups', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description }) });
export const adminUpdateGroup = (id: number, name: string, description: string) =>
  adminFetch<void>(`/groups/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, description }) });
export const adminDeleteGroup = (id: number) =>
  adminFetch<void>(`/groups/${id}`, { method: 'DELETE' });
export const adminAddMember = (groupId: number, userId: string) =>
  adminFetch<void>(`/groups/${groupId}/members`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId }) });
export const adminRemoveMember = (groupId: number, userId: string) =>
  adminFetch<void>(`/groups/${groupId}/members/${userId}`, { method: 'DELETE' });

// Permissions
export const adminSetPermissions = (groupId: number, permissions: { resourceKey: string; objectTypeId: number | null; canRead: boolean; canWrite: boolean; canDelete: boolean }[]) =>
  adminFetch<void>(`/groups/${groupId}/permissions`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(permissions) });
export const adminSetFieldRestrictions = (groupId: number, restrictions: { resourceKey: string; fieldKey: string; objectTypeId: number | null }[]) =>
  adminFetch<void>(`/groups/${groupId}/field-restrictions`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(restrictions) });

// Resources
export const adminGetResources = () => adminFetch<AdminResource[]>('/resources');

// ==================== Business Partners ====================

export async function fetchBusinessPartner(id: number, signal?: AbortSignal): Promise<BusinessPartnerDto> {
  const res = await authFetch(`/api/business-partners/${id}`, { signal });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function saveBusinessPartner(dto: BusinessPartnerDto): Promise<BusinessPartnerDto> {
  const isNew = dto.id === null;
  const url = isNew ? '/api/business-partners' : `/api/business-partners/${dto.id}`;
  const res = await authFetch(url, {
    method: isNew ? 'POST' : 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
  return res.json();
}

export async function deleteBusinessPartner(id: number): Promise<void> {
  const res = await authFetch(`/api/business-partners/${id}`, { method: 'DELETE' });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error || `HTTP ${res.status}`);
  }
}
