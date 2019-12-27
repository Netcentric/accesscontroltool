package biz.netcentric.cq.tools.actool.history.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import biz.netcentric.cq.tools.actool.history.AcToolExecution;

public class AcToolExecutionImpl implements AcToolExecution, Comparable<AcToolExecution> {
    static final String TRIGGER_SEPARATOR_IN_NODE_NAME = "_via_";

    private final String path;
    private final Date installationDate;
    private final boolean isSuccess;
    private final String trigger;
    
    public AcToolExecutionImpl(String path, Date installationDate, boolean isSuccess) {
        super();
        this.path = path;
        this.installationDate = installationDate;
        this.isSuccess = isSuccess;
        this.trigger = StringUtils.substringAfter(path, TRIGGER_SEPARATOR_IN_NODE_NAME);
    }

    @Override
    public String toString() {
        String successStatusString = isSuccess ? "ok" : "failed";
        return path + " (" + installationDate.toString() + ")(" + successStatusString  + ")";
    }

    public String getLogsPath() {
        return path;
    }

    public Date getInstallationDate() {
        return installationDate;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getTrigger() {
        return trigger;
    }

    @Override
    public int compareTo(AcToolExecution otherExecution) {
        return -getInstallationDate().compareTo(otherExecution.getInstallationDate());
    }

}
