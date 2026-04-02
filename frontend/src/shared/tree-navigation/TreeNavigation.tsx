import { useState, useCallback, useMemo } from 'react';
import { TreeView, type TreeNode } from '../TreeView';
import type { TreeNavigationProps, TreeNodeDef } from './types';
import './TreeNavigation.css';

const MIN_WIDTH = 150;
const MAX_WIDTH = 400;

function findFirstFrameNode(nodes: TreeNodeDef[]): string | null {
  for (const node of nodes) {
    if (node.hasFrame !== false) return node.id;
    if (node.children) {
      const found = findFirstFrameNode(node.children);
      if (found) return found;
    }
  }
  return null;
}

function collectAllIds(nodes: TreeNodeDef[]): string[] {
  const ids: string[] = [];
  for (const node of nodes) {
    ids.push(node.id);
    if (node.children) ids.push(...collectAllIds(node.children));
  }
  return ids;
}

function collectFrameIds(nodes: TreeNodeDef[]): string[] {
  const ids: string[] = [];
  for (const node of nodes) {
    if (node.hasFrame !== false) ids.push(node.id);
    if (node.children) ids.push(...collectFrameIds(node.children));
  }
  return ids;
}

function toTreeNodes(defs: TreeNodeDef[]): TreeNode[] {
  return defs.map(def => ({
    id: def.id,
    label: def.label,
    children: def.children ? toTreeNodes(def.children) : undefined,
  }));
}

export function TreeNavigation<T>({
  nodes,
  frames,
  data,
  onChange,
  disabled = false,
  validationErrors,
  renderNode,
  defaultWidth = 220,
}: TreeNavigationProps<T>) {
  const [activeNodeId, setActiveNodeId] = useState<string>(
    () => findFirstFrameNode(nodes) ?? ''
  );
  const [panelWidth, setPanelWidth] = useState(defaultWidth);
  const [dragging, setDragging] = useState(false);
  const noFrameIds = useMemo(() => {
    const set = new Set<string>();
    function walk(defs: TreeNodeDef[]) {
      for (const def of defs) {
        if (def.hasFrame === false) set.add(def.id);
        if (def.children) walk(def.children);
      }
    }
    walk(nodes);
    return set;
  }, [nodes]);

  const treeData = useMemo(() => toTreeNodes(nodes), [nodes]);
  const defaultExpanded = useMemo(() => collectAllIds(nodes), [nodes]);
  const frameIds = useMemo(() => collectFrameIds(nodes), [nodes]);

  const nodeDefMap = useMemo(() => {
    const map = new Map<string, TreeNodeDef>();
    function walk(defs: TreeNodeDef[]) {
      for (const def of defs) {
        map.set(def.id, def);
        if (def.children) walk(def.children);
      }
    }
    walk(nodes);
    return map;
  }, [nodes]);

  const handleSelect = useCallback((node: TreeNode) => {
    if (!noFrameIds.has(node.id)) {
      setActiveNodeId(node.id);
    }
  }, [noFrameIds]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setDragging(true);
    const startX = e.clientX;
    const startWidth = panelWidth;

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const delta = moveEvent.clientX - startX;
      const newWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta));
      setPanelWidth(newWidth);
    };

    const handleMouseUp = () => {
      setDragging(false);
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [panelWidth]);

  const treeRenderNode = useCallback((node: TreeNode, _item: import('@headless-tree/core').ItemInstance<TreeNode>) => {
    const def = nodeDefMap.get(node.id);
    const hasErrors = !!(validationErrors && validationErrors[node.id]?.length);

    if (renderNode && def) {
      return renderNode(def, hasErrors);
    }

    return (
      <>
        <span className="tree-label">{node.label}</span>
        {hasErrors && <span className="tree-node-error-dot" />}
      </>
    );
  }, [nodeDefMap, validationErrors, renderNode]);

  return (
    <div className="tree-navigation">
      <div className="tree-navigation-panel" style={{ width: panelWidth }}>
        <TreeView
          data={treeData}
          variant="light"
          defaultExpanded={defaultExpanded}
          selectOnClick
          selectedId={activeNodeId}
          onSelect={handleSelect}
          renderNode={treeRenderNode}
        />
      </div>

      <div
        className={`tree-navigation-resize-handle ${dragging ? 'tree-navigation-resize-handle--active' : ''}`}
        onMouseDown={handleMouseDown}
      >
        <div className="tree-navigation-resize-handle-indicator" />
      </div>

      <div className="tree-navigation-frame">
        {frameIds.map(id => {
          const FrameComponent = frames[id];
          if (!FrameComponent) return null;
          return (
            <div key={id} className={id !== activeNodeId ? 'tree-navigation-frame-hidden' : undefined}>
              <FrameComponent data={data} onChange={onChange} disabled={disabled} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
