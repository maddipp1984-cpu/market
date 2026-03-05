import { useTabContext } from './TabContext';
import { tabTypes, sectionLabels } from './tabTypes';
import './Sidebar.css';

const sections = ['daten', 'stammdaten', 'system'] as const;

const logoIcon = (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
  </svg>
);

export function Sidebar() {
  const { openTab } = useTabContext();

  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        {logoIcon}
        TIMESERIES
      </div>
      {sections.map(section => {
        const sectionTypes = tabTypes.filter(t => t.section === section);
        if (sectionTypes.length === 0) return null;
        return (
          <div key={section} className="sidebar-section">
            <div className="sidebar-section-label">{sectionLabels[section]}</div>
            {sectionTypes.map(tabType => (
              <button
                key={tabType.type}
                className="sidebar-link"
                onClick={() => openTab(tabType.type)}
              >
                {tabType.icon}
                {tabType.label}
              </button>
            ))}
          </div>
        );
      })}
    </nav>
  );
}
