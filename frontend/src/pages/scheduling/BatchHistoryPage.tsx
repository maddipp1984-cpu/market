import { useState, useCallback } from 'react';
import { OverviewPage } from '../../shared/overview-page/OverviewPage';
import { fetchExecutionLog } from '../../api/client';
import { useMessageBar } from '../../shell/MessageBarContext';

const columnOverrides = { id: { hidden: true }, scheduleId: { hidden: true } };

export function BatchHistoryPage({ tabId }: { tabId: string }) {
  const { showMessage } = useMessageBar();
  const [logContent, setLogContent] = useState<string | null>(null);
  const [logExecId, setLogExecId] = useState<number | null>(null);

  const handleRowDoubleClick = useCallback(async (row: Record<string, unknown>) => {
    const execId = row.id as number;
    try {
      const content = await fetchExecutionLog(execId);
      setLogContent(content);
      setLogExecId(execId);
    } catch {
      showMessage('Log konnte nicht geladen werden', 'error');
    }
  }, [showMessage]);

  return (
    <>
      <OverviewPage
        pageKey="batch-history"
        apiUrl="/api/batch-history"
        tabId={tabId}
        columnOverrides={columnOverrides}
        emptyMessage="Noch keine Ausfuehrungen"
        onRowDoubleClick={handleRowDoubleClick}
      />

      {/* Log Modal */}
      {logContent !== null && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'var(--color-overlay, rgba(0,0,0,0.5))', display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000,
        }}
          onClick={() => { setLogContent(null); setLogExecId(null); }}
        >
          <div
            style={{
              background: 'var(--color-surface, white)', borderRadius: 'var(--radius-md)',
              padding: 'var(--space-md)', maxWidth: '800px', width: '90%', maxHeight: '80vh',
              display: 'flex', flexDirection: 'column',
            }}
            onClick={e => e.stopPropagation()}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-sm)' }}>
              <h3 style={{ margin: 0 }}>Log (Ausfuehrung #{logExecId})</h3>
              <button
                onClick={() => { setLogContent(null); setLogExecId(null); }}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: 'var(--color-text-secondary)' }}
              >
                X
              </button>
            </div>
            <pre style={{
              flex: 1, overflow: 'auto', background: 'var(--color-bg, #f8f9fa)',
              padding: 'var(--space-sm)', borderRadius: 'var(--radius-sm)',
              fontSize: 'var(--font-size-sm)', fontFamily: 'monospace',
              whiteSpace: 'pre-wrap', wordBreak: 'break-all',
              margin: 0,
            }}>
              {logContent}
            </pre>
          </div>
        </div>
      )}
    </>
  );
}
