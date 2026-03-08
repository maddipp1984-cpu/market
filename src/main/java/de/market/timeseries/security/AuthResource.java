package de.market.timeseries.security;

public class AuthResource {
    private String resourceKey;
    private String label;
    private boolean hasTypeScope;

    public AuthResource() {}

    public AuthResource(String resourceKey, String label, boolean hasTypeScope) {
        this.resourceKey = resourceKey;
        this.label = label;
        this.hasTypeScope = hasTypeScope;
    }

    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isHasTypeScope() { return hasTypeScope; }
    public void setHasTypeScope(boolean hasTypeScope) { this.hasTypeScope = hasTypeScope; }
}
