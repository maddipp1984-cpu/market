package de.projekt.timeseries.rest.dto;

import java.util.List;

public class SidebarNodeDto {
    private String id;
    private String label;
    private String tabType;
    private List<SidebarNodeDto> children;

    public SidebarNodeDto() {}

    public SidebarNodeDto(String id, String label, String tabType, List<SidebarNodeDto> children) {
        this.id = id;
        this.label = label;
        this.tabType = tabType;
        this.children = children;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getTabType() { return tabType; }
    public void setTabType(String tabType) { this.tabType = tabType; }

    public List<SidebarNodeDto> getChildren() { return children; }
    public void setChildren(List<SidebarNodeDto> children) { this.children = children; }
}
