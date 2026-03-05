import './FilterBar.css';

interface FilterBarProps {
  children: React.ReactNode;
  actions?: React.ReactNode;
}

export function FilterBar({ children, actions }: FilterBarProps) {
  return (
    <div className="filter-bar">
      {children}
      {actions && <div className="filter-bar-actions">{actions}</div>}
    </div>
  );
}
