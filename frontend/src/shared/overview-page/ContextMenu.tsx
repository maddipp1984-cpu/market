import { useLayoutEffect, useEffect, useRef, useCallback } from 'react';
import './ContextMenu.css';

export interface ContextMenuItem {
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  danger?: boolean;
}

export interface ContextMenuSeparator {
  separator: true;
}

export type ContextMenuEntry = ContextMenuItem | ContextMenuSeparator;

interface ContextMenuProps {
  x: number;
  y: number;
  items: ContextMenuEntry[];
  onClose: () => void;
}

export function ContextMenu({ x, y, items, onClose }: ContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const itemsRef = useRef<(HTMLButtonElement | null)[]>([]);

  // Adjust position before paint to avoid flicker
  useLayoutEffect(() => {
    const el = menuRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.right > window.innerWidth) {
      el.style.left = `${x - rect.width}px`;
    }
    if (rect.bottom > window.innerHeight) {
      el.style.top = `${y - rect.height}px`;
    }
  }, [x, y]);

  // Focus first item on open
  useEffect(() => {
    itemsRef.current[0]?.focus();
  }, []);

  // Close on Escape or click outside
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    const handleClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('keydown', handleKey);
    document.addEventListener('mousedown', handleClick);
    return () => {
      document.removeEventListener('keydown', handleKey);
      document.removeEventListener('mousedown', handleClick);
    };
  }, [onClose]);

  // Arrow key navigation
  const handleMenuKeyDown = useCallback((e: React.KeyboardEvent) => {
    const focusable = itemsRef.current.filter(Boolean) as HTMLButtonElement[];
    const currentIdx = focusable.findIndex(el => el === document.activeElement);
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      const next = currentIdx < focusable.length - 1 ? currentIdx + 1 : 0;
      focusable[next]?.focus();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      const prev = currentIdx > 0 ? currentIdx - 1 : focusable.length - 1;
      focusable[prev]?.focus();
    }
  }, []);

  let itemIndex = 0;

  return (
    <div ref={menuRef} className="context-menu" role="menu" style={{ left: x, top: y }} onKeyDown={handleMenuKeyDown}>
      {items.map((item, i) => {
        if ('separator' in item && item.separator) {
          return <div key={`sep-${i}`} className="context-menu-separator" role="separator" />;
        }
        const menuItem = item as ContextMenuItem;
        const idx = itemIndex++;
        return (
          <button
            key={menuItem.label}
            ref={el => { itemsRef.current[idx] = el; }}
            className={`context-menu-item${menuItem.danger ? ' danger' : ''}`}
            disabled={menuItem.disabled}
            role="menuitem"
            onClick={() => { menuItem.onClick(); onClose(); }}
          >
            {menuItem.icon && <span className="context-menu-icon">{menuItem.icon}</span>}
            <span>{menuItem.label}</span>
          </button>
        );
      })}
    </div>
  );
}
