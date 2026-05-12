import './LoadingIndicator.css';

interface LoadingIndicatorProps {
  message?: string;
}

export function LoadingIndicator({ message = 'Lade...' }: LoadingIndicatorProps) {
  return <div className="loading-indicator">{message}</div>;
}
