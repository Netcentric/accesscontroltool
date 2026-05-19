package biz.netcentric.cq.tools.actool.history.impl;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import biz.netcentric.cq.tools.actool.history.AcToolExecution;

public class AcToolExecutionImpl implements AcToolExecution, Comparable<AcToolExecution> {
    static final String TRIGGER_SEPARATOR_IN_NODE_NAME = "_via_";

    private final String id;
    private final String path;
    private final Date installationDate;
    private final boolean isSuccess;
    private final String trigger;
    private final String configurationRootPath;
    private final int authorizableChanges;
    private final int aclChanges;
    
    public AcToolExecutionImpl(String id, String path, Date installationDate, boolean isSuccess, String configurationRootPath, int authorizableChanges, int aclChanges) {
        super();
        this.id = id;
        this.path = path;
        this.installationDate = installationDate;
        this.isSuccess = isSuccess;
        this.trigger = StringUtils.substringAfter(path, TRIGGER_SEPARATOR_IN_NODE_NAME); // best backwards-compatible way to obtain the trigger
        this.configurationRootPath = configurationRootPath;
        this.authorizableChanges = authorizableChanges;
        this.aclChanges = aclChanges; 
    }

    @Override
    public String toString() {
        String successStatusString = isSuccess ? "ok" : "failed";
        return path + " (" + installationDate.toString() + ")(" + successStatusString  + ")";
    }

    @Override
    public String getId() {
        return id;
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

    public String getConfigurationRootPath() {
        return configurationRootPath;
    }

    public int getAuthorizableChanges() {
        return authorizableChanges;
    }

    public int getAclChanges() {
        return aclChanges;
    }

    @Override
    public int compareTo(AcToolExecution otherExecution) {
        int compareByDate = -getInstallationDate().compareTo(otherExecution.getInstallationDate());
        if (compareByDate != 0) {
            return compareByDate;
        }
        return getId().compareTo(otherExecution.getId());
    }

}
