import './PageLayout.css';

interface PageLayoutProps {
  title: string;
  maxWidth?: string;
  children: React.ReactNode;
}

export function PageLayout({ title, maxWidth, children }: PageLayoutProps) {
  return (
    <div className="page-layout" style={maxWidth ? { maxWidth } : undefined}>
      <h1 className="page-title">{title}</h1>
      {children}
    </div>
  );
}
