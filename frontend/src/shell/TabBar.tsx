import { useTabContext } from './TabContext';
import './TabBar.css';

export function TabBar() {
  const { tabs, activeTabId, setActiveTab, closeTab } = useTabContext();

  return (
    <div className="tab-bar">
      {tabs.map(tab => (
        <button
          key={tab.id}
          className={`tab-bar-tab${activeTabId === tab.id ? ' active' : ''}`}
          onClick={() => setActiveTab(tab.id)}
        >
          <span className="tab-bar-icon">{tab.icon}</span>
          <span className="tab-bar-label">{tab.label}</span>
          {tabs.length > 1 && (
            <span
              className="tab-bar-close"
              onClick={(e) => { e.stopPropagation(); closeTab(tab.id); }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
