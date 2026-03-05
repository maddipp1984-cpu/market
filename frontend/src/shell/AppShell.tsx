import { Sidebar } from './Sidebar';
import { TabBar } from './TabBar';
import { MessageBar } from './MessageBar';
import { useTabContext } from './TabContext';
import { getTabType } from './tabTypes';
import './AppShell.css';

export function AppShell() {
  const { tabs, activeTabId } = useTabContext();

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="app-shell-main">
        <TabBar />
        <div className="app-shell-content">
          {tabs.map(tab => {
            const tabType = getTabType(tab.type);
            if (!tabType) return null;
            const Component = tabType.component;
            return (
              <div
                key={tab.id}
                className="tab-panel"
                style={{ display: activeTabId === tab.id ? 'flex' : 'none' }}
              >
                <Component tabId={tab.id} />
              </div>
            );
          })}
        </div>
        <MessageBar />
      </div>
    </div>
  );
}
