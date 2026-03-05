import './DataPage.css';

interface DataPageProps {
  title: string;
  subtitle?: string;
  headerContent?: React.ReactNode;
  filterBar?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
}

export function DataPage({ title, subtitle, headerContent, filterBar, actions, children }: DataPageProps) {
  return (
    <div className="data-page">
      <div className="data-page-header">
        <h1 className="data-page-title">{title}</h1>
        {subtitle && <span className="data-page-subtitle">{subtitle}</span>}
        {actions && <div className="data-page-actions">{actions}</div>}
      </div>
      {headerContent}
      {filterBar}
      <div className="data-page-content">
        {children}
      </div>
    </div>
  );
}
