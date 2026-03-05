import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTabContext } from './TabContext';
import { defaultSidebarTree, type SidebarNode } from './sidebarTree';
import { getTabType } from './tabTypes';
import { fetchSidebarConfig, type SidebarNodeConfig } from '../api/client';
import { TreeView, type TreeNode } from '../shared/TreeView';
import type { ItemInstance } from '@headless-tree/core';
import './Sidebar.css';

const logoIcon = (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
  </svg>
);

/** Convert backend config nodes to SidebarNode[], skipping unknown tabTypes */
function convertConfigNodes(nodes: SidebarNodeConfig[]): SidebarNode[] {
  const result: SidebarNode[] = [];
  for (const node of nodes) {
    if (node.children) {
      // Folder
      const children = convertConfigNodes(node.children);
      result.push({ id: node.id, label: node.label ?? node.id, children });
    } else if (node.tabType) {
      // Leaf item
      const tabType = getTabType(node.tabType);
      if (!tabType) {
        console.warn(`[Sidebar] Unknown tabType "${node.tabType}" for node "${node.id}" — skipping`);
        continue;
      }
      result.push({ id: node.id, label: tabType.label, tabType: node.tabType });
    }
  }
  return result;
}

/** Enriches sidebar nodes with icons from tabTypes registry */
function enrichWithIcons(nodes: SidebarNode[]): TreeNode[] {
  return nodes.map(node => {
    const tabType = node.tabType ? getTabType(node.tabType) : undefined;
    const enriched: TreeNode = {
      id: node.id,
      label: node.label,
      icon: node.icon ?? tabType?.icon,
    };
    if (node.children) {
      enriched.children = enrichWithIcons(node.children);
    }
    return enriched;
  });
}

/** Build a lookup map: nodeId -> SidebarNode (for tabType resolution) */
function buildNodeMap(nodes: SidebarNode[], map: Map<string, SidebarNode> = new Map()): Map<string, SidebarNode> {
  for (const node of nodes) {
    map.set(node.id, node);
    if (node.children) buildNodeMap(node.children, map);
  }
  return map;
}

export function Sidebar() {
  const { openTab } = useTabContext();
  const [sidebarTree, setSidebarTree] = useState<SidebarNode[]>(defaultSidebarTree);

  useEffect(() => {
    const controller = new AbortController();
    fetchSidebarConfig(controller.signal)
      .then(config => setSidebarTree(convertConfigNodes(config)))
      .catch(err => {
        if (err.name !== 'AbortError') {
          console.warn('[Sidebar] Failed to load config, using default tree:', err.message);
        }
      });
    return () => controller.abort();
  }, []);

  const treeData = useMemo(() => enrichWithIcons(sidebarTree), [sidebarTree]);
  const nodeMap = useMemo(() => buildNodeMap(sidebarTree), [sidebarTree]);

  const handleSelect = useCallback((treeNode: TreeNode) => {
    const sidebarNode = nodeMap.get(treeNode.id);
    if (sidebarNode?.tabType && !sidebarNode.children) {
      openTab(sidebarNode.tabType);
    }
  }, [nodeMap, openTab]);

  const renderNode = useCallback((node: TreeNode, item: ItemInstance<TreeNode>) => {
    const isSection = item.getItemMeta().level === 0;

    if (isSection) {
      return <span className="sidebar-section-label">{node.label}</span>;
    }

    return (
      <>
        {node.icon && <span className="tree-icon">{node.icon}</span>}
        <span className="tree-label">{node.label}</span>
      </>
    );
  }, []);

  // Root sections are expanded by default
  const defaultExpanded = useMemo(() => sidebarTree.map(s => s.id), [sidebarTree]);

  return (
    <nav className="sidebar">
      <div className="sidebar-logo">
        {logoIcon}
        TIMESERIES
      </div>
      <TreeView
        data={treeData}
        variant="dark"
        defaultExpanded={defaultExpanded}
        paddingBase="var(--space-lg)"
        onSelect={handleSelect}
        renderNode={renderNode}
      />
    </nav>
  );
}
