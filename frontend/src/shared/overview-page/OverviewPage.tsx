import type { ReactNode } from 'react';
import { Button } from '../Button';
import { StatusMessage } from '../StatusMessage';
import './OverviewPage.css';

const iconRefresh = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="23 4 23 10 17 10" /><polyline points="1 20 1 14 7 14" />
    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
  </svg>
);

const iconPlus = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);

interface OverviewPageProps {
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
  onNew?: () => void;
  newLabel?: string;
  actions?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}

export function OverviewPage({
  loading,
  error,
  onRefresh,
  onNew,
  newLabel = 'Neu',
  actions: extraActions,
  children,
  footer,
}: OverviewPageProps) {
  return (
    <div className="overview-page">
      <div className="overview-page-toolbar">
        <Button variant="ghost" icon onClick={onRefresh} disabled={loading} title="Aktualisieren" aria-label="Aktualisieren">
          {iconRefresh}
        </Button>
        {onNew && (
          <Button variant="primary" icon onClick={onNew} title={newLabel} aria-label={newLabel}>
            {iconPlus}
          </Button>
        )}
        {extraActions}
      </div>
      <div className="overview-page-content">
        {loading && <StatusMessage type="info">Lade...</StatusMessage>}
        {error && <StatusMessage type="error">{error}</StatusMessage>}
        {!loading && !error && children}
      </div>
      {footer && <div className="overview-page-footer">{footer}</div>}
    </div>
  );
}
