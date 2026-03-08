package de.market.scheduling.rest.dto;

import java.util.Map;

public class BatchScheduleDto {
    private Integer id;
    private String jobKey;
    private String name;
    private String scheduleType;
    private String cronExpression;
    private Integer intervalSeconds;
    private boolean enabled;
    private Map<String, Object> parameters;

    // Getter + Setter
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getJobKey() { return jobKey; }
    public void setJobKey(String jobKey) { this.jobKey = jobKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Integer getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(Integer intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
