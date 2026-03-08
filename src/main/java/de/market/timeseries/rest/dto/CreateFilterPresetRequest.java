package de.market.timeseries.rest.dto;

public class CreateFilterPresetRequest {

    private String pageKey;
    private String name;
    private Object conditions;
    private String scope = "USER";
    private boolean isDefault;

    public String getPageKey() { return pageKey; }
    public void setPageKey(String pageKey) { this.pageKey = pageKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Object getConditions() { return conditions; }
    public void setConditions(Object conditions) { this.conditions = conditions; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
