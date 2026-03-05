import { TabProvider } from './shell/TabContext';
import { AppShell } from './shell/AppShell';

export default function App() {
  return (
    <TabProvider>
      <AppShell />
    </TabProvider>
  );
}
