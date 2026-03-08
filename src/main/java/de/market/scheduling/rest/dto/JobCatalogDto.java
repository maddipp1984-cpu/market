package de.market.scheduling.rest.dto;

import de.market.scheduling.model.JobParameter;
import java.util.List;

public class JobCatalogDto {
    private String jobKey;
    private String name;
    private String description;
    private List<ParameterDto> parameters;

    public static class ParameterDto {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
        private List<String> enumValues;

        public static ParameterDto from(JobParameter p) {
            ParameterDto dto = new ParameterDto();
            dto.name = p.getName();
            dto.type = p.getType().name();
            dto.description = p.getDescription();
            dto.required = p.isRequired();
            dto.defaultValue = p.getDefaultValue();
            dto.enumValues = p.getEnumValues();
            return dto;
        }

        // Getter
        public String getName() { return name; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Object getDefaultValue() { return defaultValue; }
        public List<String> getEnumValues() { return enumValues; }
    }

    // Getter + Setter
    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<ParameterDto> getParameters() { return parameters; }
    public void setParameters(List<ParameterDto> parameters) { this.parameters = parameters; }
}
