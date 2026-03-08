package de.market.scheduling.model;

import java.util.List;

public class JobParameter {
    private final String name;
    private final JobParameterType type;
    private final String description;
    private final boolean required;
    private final Object defaultValue;
    private final List<String> enumValues; // nur für ENUM-Typ

    private JobParameter(String name, JobParameterType type, String description,
                         boolean required, Object defaultValue, List<String> enumValues) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
        this.defaultValue = defaultValue;
        this.enumValues = enumValues;
    }

    public static JobParameter required(String name, JobParameterType type, String description) {
        return new JobParameter(name, type, description, true, null, null);
    }

    public static JobParameter optional(String name, JobParameterType type, String description, Object defaultValue) {
        return new JobParameter(name, type, description, false, defaultValue, null);
    }

    public static JobParameter enumParam(String name, String description, boolean required, List<String> values, String defaultValue) {
        return new JobParameter(name, JobParameterType.ENUM, description, required, defaultValue, values);
    }

    // Getter
    public String getName() { return name; }
    public JobParameterType getType() { return type; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public Object getDefaultValue() { return defaultValue; }
    public List<String> getEnumValues() { return enumValues; }
}
