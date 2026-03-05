import { TabProvider } from './shell/TabContext';
import { MessageBarProvider } from './shell/MessageBarContext';
import { AppShell } from './shell/AppShell';

export default function App() {
  return (
    <MessageBarProvider>
      <TabProvider>
        <AppShell />
      </TabProvider>
    </MessageBarProvider>
  );
}
