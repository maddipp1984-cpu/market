import type { ReactNode } from 'react';

export interface TreeNodeDef {
  id: string;
  label: string;
  children?: TreeNodeDef[];
  hasFrame?: boolean;
}

export interface FrameProps<T> {
  data: T;
  onChange: (updated: T) => void;
  disabled: boolean;
}

export interface TreeNavigationProps<T> {
  nodes: TreeNodeDef[];
  frames: Record<string, React.ComponentType<FrameProps<T>>>;
  data: T;
  onChange: (updated: T) => void;
  disabled?: boolean;
  validationErrors?: Record<string, string[]>;
  renderNode?: (node: TreeNodeDef, hasErrors: boolean) => ReactNode;
  defaultWidth?: number;
}
