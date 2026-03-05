import { createContext, useContext, useState, useCallback, useRef, type ReactNode } from 'react';

export interface Message {
  id: number;
  text: string;
  type: 'success' | 'info' | 'error';
  timestamp: number;
}

interface MessageBarContextValue {
  messages: Message[];
  showMessage: (text: string, type?: 'success' | 'info' | 'error') => void;
  clearMessages: () => void;
}

const MessageBarContext = createContext<MessageBarContextValue | null>(null);

export function MessageBarProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const counterRef = useRef(0);

  const showMessage = useCallback((text: string, type: 'success' | 'info' | 'error' = 'info') => {
    const id = ++counterRef.current;
    setMessages(prev => [...prev, { id, text, type, timestamp: Date.now() }]);
  }, []);

  const clearMessages = useCallback(() => setMessages([]), []);

  return (
    <MessageBarContext.Provider value={{ messages, showMessage, clearMessages }}>
      {children}
    </MessageBarContext.Provider>
  );
}

export function useMessageBar() {
  const ctx = useContext(MessageBarContext);
  if (!ctx) throw new Error('useMessageBar must be used within MessageBarProvider');
  return ctx;
}
