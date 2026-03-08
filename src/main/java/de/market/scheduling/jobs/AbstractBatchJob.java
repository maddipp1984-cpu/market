package de.market.scheduling.jobs;

import de.market.scheduling.model.JobResult;

public abstract class AbstractBatchJob {

    public abstract String getJobKey();

    public abstract String getName();

    public abstract String getDescription();

    public abstract JobResult execute();
}
