package de.projekt.businesspartner.model;

public enum ContactFunction {
    ABRECHNUNG("Abrechnung"),
    BK_VERANTWORTLICHER("BK-Verantwortlicher");

    private final String label;

    ContactFunction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
