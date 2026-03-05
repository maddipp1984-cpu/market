import './StatusMessage.css';

interface StatusMessageProps {
  type: 'error' | 'info';
  children: React.ReactNode;
}

export function StatusMessage({ type, children }: StatusMessageProps) {
  return <div className={`status-message status-${type}`}>{children}</div>;
}
