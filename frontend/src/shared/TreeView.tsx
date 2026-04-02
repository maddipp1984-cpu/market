import { type ReactNode, useCallback, useMemo, useRef, useEffect } from 'react';
import { useTree } from '@headless-tree/react';
import { syncDataLoaderFeature, selectionFeature, hotkeysCoreFeature, type ItemInstance } from '@headless-tree/core';
import './TreeView.css';

export interface TreeNode {
  id: string;
  label: string;
  icon?: ReactNode;
  children?: TreeNode[];
}

interface TreeViewProps {
  data: TreeNode[];
  variant?: 'dark' | 'light';
  defaultExpanded?: string[];
  paddingBase?: string;
  onSelect?: (node: TreeNode) => void;
  selectOnClick?: boolean;
  selectedId?: string | null;
  renderNode?: (node: TreeNode, item: ItemInstance<TreeNode>) => ReactNode;
}

function flattenNodes(nodes: TreeNode[], map: Map<string, TreeNode>, childrenMap: Map<string, string[]>) {
  for (const node of nodes) {
    map.set(node.id, node);
    if (node.children && node.children.length > 0) {
      childrenMap.set(node.id, node.children.map(c => c.id));
      flattenNodes(node.children, map, childrenMap);
    }
  }
}

export function TreeView({ data, variant = 'light', defaultExpanded, paddingBase = '0px', onSelect, selectOnClick, selectedId, renderNode }: TreeViewProps) {
  const nodeMapRef = useRef(new Map<string, TreeNode>());
  const childrenMapRef = useRef(new Map<string, string[]>());

  const { rootChildIds } = useMemo(() => {
    const nodeMap = new Map<string, TreeNode>();
    const childrenMap = new Map<string, string[]>();
    flattenNodes(data, nodeMap, childrenMap);
    const rootChildIds = data.map(n => n.id);
    nodeMap.set('__root__', { id: '__root__', label: '', children: data });
    childrenMap.set('__root__', rootChildIds);
    nodeMapRef.current = nodeMap;
    childrenMapRef.current = childrenMap;
    return { rootChildIds };
  }, [data]);

  const tree = useTree<TreeNode>({
    rootItemId: '__root__',
    dataLoader: {
      getItem: (itemId) => {
        const node = nodeMapRef.current.get(itemId);
        if (!node) {
          console.warn(`[TreeView] Unknown item requested: "${itemId}"`);
          return { id: itemId, label: '' };
        }
        return node;
      },
      getChildren: (itemId) => childrenMapRef.current.get(itemId) ?? [],
    },
    getItemName: (item) => item.getItemData().label,
    isItemFolder: (item) => (childrenMapRef.current.get(item.getId())?.length ?? 0) > 0,
    initialState: {
      expandedItems: (defaultExpanded ?? rootChildIds).filter(id => nodeMapRef.current.has(id)),
    },
    features: [syncDataLoaderFeature, selectionFeature, hotkeysCoreFeature],
  });

  // Rebuild tree when data changes (instead of remounting)
  const isFirstRender = useRef(true);
  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false;
      return;
    }
    tree.rebuildTree();
  }, [data]);

  const handleItemClick = useCallback((item: ItemInstance<TreeNode>, e: React.MouseEvent) => {
    // Call headless-tree's own handler first (accessibility, focus)
    item.getProps().onClick?.(e as never);

    if (item.isFolder()) {
      if (item.isExpanded()) {
        item.collapse();
      } else {
        item.expand();
      }
      if (selectOnClick) {
        onSelect?.(item.getItemData());
      }
    } else if (selectOnClick) {
      onSelect?.(item.getItemData());
    }
  }, [selectOnClick, onSelect]);

  const handleItemDblClick = useCallback((item: ItemInstance<TreeNode>) => {
    if (!item.isFolder()) {
      onSelect?.(item.getItemData());
    }
  }, [onSelect]);

  return (
    <div className={`tree-view tree-view--${variant}`} {...tree.getContainerProps('Tree')}>
      {tree.getItems().map((item) => {
        const node = item.getItemData();
        const level = item.getItemMeta().level;
        return (
          <div
            key={item.getId()}
            className={`tree-node ${selectedId != null ? (item.getId() === selectedId ? 'tree-node--highlighted' : '') : (item.isSelected() ? 'tree-node--selected' : '')}`}
            style={{ paddingLeft: `calc(${paddingBase} + ${level} * var(--tree-indent))` }}
            data-level={level}
            {...item.getProps()}
            onClick={(e) => handleItemClick(item, e)}
            onDoubleClick={() => handleItemDblClick(item)}
          >
            {item.isFolder() ? (
              <span className="tree-chevron">
                {item.isExpanded() ? '\u25BC' : '\u25B6'}
              </span>
            ) : (
              <span className="tree-chevron tree-chevron--spacer" />
            )}
            {renderNode ? renderNode(node, item) : (
              <>
                {node.icon && <span className="tree-icon">{node.icon}</span>}
                <span className="tree-label">{node.label}</span>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
