package de.market.scheduling.jobs;

import de.market.scheduling.model.JobParameter;
import de.market.scheduling.model.JobResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class AbstractBatchJob {

    public abstract String getJobKey();

    public abstract String getName();

    public abstract String getDescription();

    public List<JobParameter> getParameters() {
        return Collections.emptyList();
    }

    public void validateParameters(Map<String, Object> parameters) {
        for (JobParameter param : getParameters()) {
            if (param.isRequired()) {
                Object val = parameters.get(param.getName());
                if (val == null || (val instanceof String && ((String) val).isBlank())) {
                    throw new IllegalArgumentException(
                        "Pflichtparameter '" + param.getName() + "' fehlt fuer Job '" + getJobKey() + "'");
                }
            }
        }
    }

    public abstract JobResult execute(Map<String, Object> parameters);
}
