/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.history.impl;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;

import biz.netcentric.cq.tools.actool.api.HistoryEntry;
import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.api.InstallationResult;
import biz.netcentric.cq.tools.actool.comparators.HistoryEntryComparator;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

public class PersistableInstallationLogger implements InstallationLogger, InstallationLog, InstallationResult {

    protected static final String MSG_IDENTIFIER_ERROR = "ERROR: ";
    protected static final String MSG_IDENTIFIER_WARNING = "WARNING: ";

    private Set<HistoryEntry> warnings = new HashSet<HistoryEntry>();
    private Set<HistoryEntry> messages = new HashSet<HistoryEntry>();
    private Set<HistoryEntry> errors = new HashSet<HistoryEntry>();

    private Set<HistoryEntry> verboseMessages = new HashSet<HistoryEntry>();

    private boolean success = true;
    private final Date installationDate;
    private long executionTime;
    private long msgIndex = 0;

    private String mergedAndProcessedConfig;

    private Map<String, String> configFileContentsByName;

    // only for install hook case
    private String crxPackageName;

    private int countAclsNoChange = 0;
    private int countAclsChanged = 0;
    private int countAclsPathDoesNotExist = 0;

    private int countActionCacheHit = 0;
    private int countActionCacheMiss = 0;

    private int countAuthorizablesCreated = 0;
    private int countAuthorizablesMoved = 0;

    private int missingParentPathsForInitialContent = 0;

    private DateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    public PersistableInstallationLogger() {
        installationDate = new Date();
    }

    public Date getInstallationDate() {
        return installationDate;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(final long time) {
        executionTime = time;
    }

    public Set<HistoryEntry> getWarnings() {
        return warnings;
    }


    public String getMergedAndProcessedConfig() {
        return mergedAndProcessedConfig;
    }

    public void setMergedAndProcessedConfig(String mergedAndProcessedConfig) {
        this.mergedAndProcessedConfig = mergedAndProcessedConfig;
    }

    public Map<String, String> getConfigFileContentsByName() {
        return configFileContentsByName;
    }

    public void setConfigFileContentsByName(Map<String, String> configFileContentsByName) {
        this.configFileContentsByName = configFileContentsByName;
    }

    /* (non-Javadoc)
     * @see biz.netcentric.cq.tools.actool.history.AcInstallationLo#getCrxPackageName()
     */
    @Override
    public String getCrxPackageName() {
        return crxPackageName;
    }

    public void setCrxPackageName(String crxPackageName) {
        this.crxPackageName = crxPackageName;
    }

    @Override
    public void addWarning(Logger log, String warning) {
        log.warn(warning);
        addWarning(warning);
    }

    protected void addWarning(String warning) {
        warnings.add(new HistoryEntry(msgIndex, new Timestamp(
                new Date().getTime()), MSG_IDENTIFIER_WARNING + warning));
        msgIndex++;
    }

    @Override
    public void addMessage(Logger log, String message) {
        log.info(message);
        addMessage(message);
    }

    protected void addMessage(String message) {
        messages.add(new HistoryEntry(msgIndex, new Timestamp(new Date()
                .getTime()), " " + message));
        msgIndex++;
    }

    @Override
    public void addError(Logger log, String error, Throwable e) {
        log.error(error, e);
        addError(error, e);
    }

    public void addError(final String error, Throwable e) {
        String fullErrorValue = error + " / e=" + e;
        errors.add(new HistoryEntry(msgIndex, new Timestamp(
                new Date().getTime()), MSG_IDENTIFIER_ERROR + fullErrorValue));
        success = false;
        msgIndex++;
    }

    @Override
    public void addVerboseMessage(Logger log, String message) {
        log.debug(message);
        addVerboseMessage(message);
    }

    protected void addVerboseMessage(String message) {
        verboseMessages.add(new HistoryEntry(msgIndex, new Timestamp(
                new Date().getTime()), " " + message));
        msgIndex++;
    }

    @Override
    public Set<HistoryEntry> getMessages() {
        return messages;
    }

    @Override
    public Set<HistoryEntry> getErrors() {
        return errors;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n" + getMessageHistory() + "\n");

        sb.append("\n" + "Execution time: " + msHumanReadable(executionTime) + "\n");

        sb.append("\n" + "Success: " + success);

        return sb.toString();
    }

    /* (non-Javadoc)
     * @see biz.netcentric.cq.tools.actool.history.AcInstallationLo#getMessageHistory()
     */
    @Override
    @SuppressWarnings("unchecked")
    public String getMessageHistory() {
        return getMessageString(getMessageSet(warnings, messages, errors));
    }

    /* (non-Javadoc)
     * @see biz.netcentric.cq.tools.actool.history.AcInstallationLo#getVerboseMessageHistory()
     */
    @Override
    @SuppressWarnings("unchecked")
    public String getVerboseMessageHistory() {
        return getMessageString(getMessageSet(warnings, messages,
                verboseMessages, errors));
    }

    private Set<HistoryEntry> getMessageSet(Set<HistoryEntry>... sets) {
        Set<HistoryEntry> resultSet = new TreeSet<>(
                new HistoryEntryComparator());

        for (Set<HistoryEntry> set : sets) {
            for (HistoryEntry entry : set) {
                resultSet.add(entry);
            }
        }
        return resultSet;
    }

    private String getMessageString(Set<HistoryEntry> messageHistorySet) {
        StringBuilder sb = new StringBuilder();
        if (!messageHistorySet.isEmpty()) {
            for (HistoryEntry entry : messageHistorySet) {
                sb.append("\n" + timestampFormat.format(entry.getTimestamp()) + ": "
                        + entry.getMessage());
            }
        }
        return sb.toString();
    }

    /** Utility method to return any magnitude of milliseconds in a human readable format using the appropriate time unit (ms, sec, min)
     * depending on the magnitude of the input.
     * 
     * @param millis milliseconds
     * @return a string with a number and a unit */
    public static String msHumanReadable(final long millis) {

        double number = millis;
        final String[] units = new String[] { "ms", "sec", "min", "h", "days" };
        final double[] divisors = new double[] { 1000, 60, 60, 24 };

        int magnitude = 0;
        do {
            double currentDivisor = divisors[Math.min(magnitude, divisors.length - 1)];
            if (number < currentDivisor) {
                break;
            }
            number /= currentDivisor;
            magnitude++;
        } while (magnitude < units.length - 1);
        NumberFormat format = NumberFormat.getNumberInstance(Locale.UK);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(1);
        String result = format.format(number) + units[magnitude];
        return result;
    }

    @Override
    public void incCountAclsNoChange() {
        countAclsNoChange++;
    }

    @Override
    public int getCountAclsUnchanged() {
        return countAclsNoChange;
    }

    @Override
    public void incCountAclsChanged() {
        countAclsChanged++;
    }

    @Override
    public int getCountAclsChanged() {
        return countAclsChanged;
    }

    @Override
    public void incCountAclsPathDoesNotExist() {
        countAclsPathDoesNotExist++;
    }

    @Override
    public int getCountAclsPathDoesNotExist() {
        return countAclsPathDoesNotExist;
    }

    @Override
    public void incCountActionCacheMiss() {
        countActionCacheMiss++;
    }

    @Override
    public int getCountActionCacheMiss() {
        return countActionCacheMiss;
    }

    @Override
    public void incCountActionCacheHit() {
        countActionCacheHit++;
    }

    @Override
    public int getCountActionCacheHit() {
        return countActionCacheHit;
    }

    @Override
    public void incMissingParentPathsForInitialContent() {
        missingParentPathsForInitialContent++;
    }

    /*
     * (non-Javadoc)
     * 
     * @see biz.netcentric.cq.tools.actool.history.AcInstallationLo#getMissingParentPathsForInitialConten()
     */
    @Override
    public int getMissingParentPathsForInitialContent() {
        return missingParentPathsForInitialContent;
    }

    @Override
    public void incCountAuthorizablesCreated() {
        countAuthorizablesCreated++;
    }

    @Override
    public void incCountAuthorizablesMoved() {
        countAuthorizablesMoved++;
    }

    public int getCountAuthorizablesCreated() {
        return countAuthorizablesCreated;
    }

    public int getCountAuthorizablesMoved() {
        return countAuthorizablesMoved;
    }

}
