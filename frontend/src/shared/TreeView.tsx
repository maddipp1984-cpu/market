import { type ReactNode, useCallback, useMemo } from 'react';
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
  renderNode?: (node: TreeNode, item: ItemInstance<TreeNode>) => ReactNode;
}

function flattenNodes(nodes: TreeNode[], map: Map<string, TreeNode>, childrenMap: Map<string, string[]>) {
  for (const node of nodes) {
    map.set(node.id, node);
    if (node.children) {
      childrenMap.set(node.id, node.children.map(c => c.id));
      flattenNodes(node.children, map, childrenMap);
    }
  }
}

export function TreeView({ data, variant = 'light', defaultExpanded, paddingBase = '0px', onSelect, renderNode }: TreeViewProps) {
  const { nodeMap, childrenMap, rootChildIds } = useMemo(() => {
    const nodeMap = new Map<string, TreeNode>();
    const childrenMap = new Map<string, string[]>();
    flattenNodes(data, nodeMap, childrenMap);
    const rootChildIds = data.map(n => n.id);
    // Add virtual root
    nodeMap.set('__root__', { id: '__root__', label: '', children: data });
    childrenMap.set('__root__', rootChildIds);
    return { nodeMap, childrenMap, rootChildIds };
  }, [data]);

  const tree = useTree<TreeNode>({
    rootItemId: '__root__',
    dataLoader: {
      getItem: (itemId) => nodeMap.get(itemId)!,
      getChildren: (itemId) => childrenMap.get(itemId) ?? [],
    },
    getItemName: (item) => item.getItemData().label,
    isItemFolder: (item) => (childrenMap.get(item.getId())?.length ?? 0) > 0,
    initialState: {
      expandedItems: defaultExpanded ?? rootChildIds,
    },
    features: [syncDataLoaderFeature, selectionFeature, hotkeysCoreFeature],
  });

  const handleItemClick = useCallback((item: ItemInstance<TreeNode>) => {
    if (item.isFolder()) {
      if (item.isExpanded()) {
        item.collapse();
      } else {
        item.expand();
      }
    }
    onSelect?.(item.getItemData());
  }, [onSelect]);

  return (
    <div className={`tree-view tree-view--${variant}`} {...tree.getContainerProps('Tree')}>
      {tree.getItems().map((item) => {
        const node = item.getItemData();
        const level = item.getItemMeta().level;
        return (
          <div
            key={item.getId()}
            className={`tree-node ${item.isSelected() ? 'tree-node--selected' : ''}`}
            style={{ paddingLeft: `calc(${paddingBase} + ${level} * var(--tree-indent))` }}
            data-level={level}
            {...item.getProps()}
            onClick={() => handleItemClick(item)}
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
