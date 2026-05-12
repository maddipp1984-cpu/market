import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useDetailPage } from './useDetailPage';
import { TabProvider } from '../../shell/TabContext';
import { MessageBarProvider } from '../../shell/MessageBarContext';

interface TestDto {
  id: number | null;
  name: string;
}

function Tester({ onResult }: { onResult: (data: ReturnType<typeof useDetailPage<TestDto>>) => void }) {
  const result = useDetailPage<TestDto>({
    tabId: 'test-tab',
    defaultData: { id: null, name: '' },
    pageKey: 'test',
  });
  onResult(result);
  return null;
}

describe('useDetailPage', () => {
  it('extracts mode from tab params', () => {
    let captured: ReturnType<typeof useDetailPage<TestDto>> | null = null;

    render(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    expect(captured).not.toBeNull();
    expect(captured!.mode).toBe('view');
  });

  it('initializes with default data and loading state', () => {
    let captured: ReturnType<typeof useDetailPage<TestDto>> | null = null;

    render(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    expect(captured!.data.name).toBe('');
    expect(captured!.dirty).toBe(false);
    // loading=true weil kein existierender Tab 'test-tab' und mode!='new'
    expect(captured!.loading).toBe(true);
  });

  it('updateField sets dirty flag', () => {
    let captured: ReturnType<typeof useDetailPage<TestDto>> | null = null;

    const { rerender } = render(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    captured!.updateField('name', 'Test');

    rerender(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    // After updateField, the next render will have dirty=true
    expect(captured!.dirty).toBe(true);
  });

  it('updateData sets dirty flag', () => {
    let captured: ReturnType<typeof useDetailPage<TestDto>> | null = null;

    const { rerender } = render(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    captured!.updateData({ id: null, name: 'Updated' });

    rerender(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    expect(captured!.dirty).toBe(true);
  });

  it('handleSaveSuccess clears dirty flag', () => {
    let captured: ReturnType<typeof useDetailPage<TestDto>> | null = null;

    const { rerender } = render(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    captured!.updateField('name', 'Test');
    rerender(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    captured!.handleSaveSuccess();
    rerender(
      <MessageBarProvider>
        <TabProvider>
          <Tester onResult={(r) => { captured = r; }} />
        </TabProvider>
      </MessageBarProvider>
    );

    expect(captured!.dirty).toBe(false);
  });
});
