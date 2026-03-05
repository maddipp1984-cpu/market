import { useState } from 'react';
import { useMessageBar, type Message } from './MessageBarContext';
import './MessageBar.css';

function formatTime(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

const typeLabels: Record<Message['type'], string> = {
  success: 'OK',
  info: 'Info',
  error: 'Fehler',
};

export function MessageBar() {
  const { messages } = useMessageBar();
  const [expanded, setExpanded] = useState(false);

  if (messages.length === 0) return null;

  const last = messages[messages.length - 1];

  return (
    <div className="message-bar">
      <div className={`message-bar-current message-bar--${last.type}`}>
        <button
          className="message-bar-toggle"
          onClick={() => setExpanded(prev => !prev)}
          disabled={messages.length <= 1}
          title={expanded ? 'Historie ausblenden' : 'Historie anzeigen'}
        >
          {expanded ? '\u2212' : '+'}
        </button>
        <span className={`message-bar-badge message-bar-badge--${last.type}`}>
          {typeLabels[last.type]}
        </span>
        <span className="message-bar-time">{formatTime(last.timestamp)}</span>
        <span className="message-bar-separator" />
        <span className="message-bar-text">{last.text}</span>
      </div>
      {expanded && messages.length > 1 && (
        <div className="message-bar-history">
          {[...messages].reverse().slice(1).map(msg => (
            <div key={msg.id} className={`message-bar-history-item message-bar--${msg.type}`}>
              <span className={`message-bar-badge message-bar-badge--${msg.type}`}>
                {typeLabels[msg.type]}
              </span>
              <span className="message-bar-time">{formatTime(msg.timestamp)}</span>
              <span className="message-bar-separator" />
              <span className="message-bar-text">{msg.text}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
