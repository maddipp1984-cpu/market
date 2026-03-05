import type { ReactNode, ComponentType } from 'react';
import { DashboardPage } from '../pages/DashboardPage';
import { TimeSeriesEditorPage } from '../timeseries-editor/TimeSeriesEditorPage';
import { ObjectsPage } from '../pages/ObjectsPage';
import { ObjekttypenPage } from '../pages/ObjekttypenPage';
import { EinheitenPage } from '../pages/EinheitenPage';
import { WaehrungenPage } from '../pages/WaehrungenPage';
import { SettingsPage } from '../pages/SettingsPage';

export interface TabType {
  type: string;
  label: string;
  icon: ReactNode;
  singleton?: boolean;
  component: ComponentType<{ tabId: string }>;
}

const iconDashboard = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
  </svg>
);

const iconTimeSeries = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);

const iconObjects = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
  </svg>
);

const iconSettings = (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

export const tabTypes: TabType[] = [
  { type: 'dashboard', label: 'Dashboard', icon: iconDashboard, singleton: true, component: DashboardPage },
  { type: 'zeitreihen', label: 'Zeitreihen', icon: iconTimeSeries, component: TimeSeriesEditorPage },
  { type: 'objekte', label: 'Objekte', icon: iconObjects, component: ObjectsPage },
  { type: 'objekttypen', label: 'Objekttypen', icon: iconObjects, component: ObjekttypenPage },
  { type: 'einheiten', label: 'Einheiten', icon: iconObjects, component: EinheitenPage },
  { type: 'waehrungen', label: 'Waehrungen', icon: iconObjects, component: WaehrungenPage },
  { type: 'einstellungen', label: 'Einstellungen', icon: iconSettings, component: SettingsPage },
];


export function getTabType(type: string): TabType | undefined {
  return tabTypes.find(t => t.type === type);
}
