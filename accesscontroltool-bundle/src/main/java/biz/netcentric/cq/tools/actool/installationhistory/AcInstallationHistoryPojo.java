/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.installationhistory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.comparators.HistoryEntryComparator;

public class AcInstallationHistoryPojo {

    private static final Logger LOG = LoggerFactory
            .getLogger(AcInstallationHistoryPojo.class);

    private static final String MSG_IDENTIFIER_EXCEPTION = "EXCEPTION:";
    private static final String MSG_IDENTIFIER_WARNING = "WARNING:";

    private Set<HistoryEntry> warnings = new HashSet<HistoryEntry>();
    private Set<HistoryEntry> messages = new HashSet<HistoryEntry>();
    private Set<HistoryEntry> exceptions = new HashSet<HistoryEntry>();

    private Set<HistoryEntry> verboseMessages = new HashSet<HistoryEntry>();

    private boolean success = true;
    private Date installationDate;
    private long executionTime;
    private long msgIndex = 0;
    Rendition rendition;

    private String mergedAndProcessedConfig;

    private DateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    public enum Rendition {
        HTML, TXT;
    }

    public AcInstallationHistoryPojo() {
        rendition = Rendition.TXT;
        setInstallationDate(new Date());
    }

    public AcInstallationHistoryPojo(Rendition rendition) {
        this.rendition = rendition;
        setInstallationDate(new Date());
    }

    public Date getInstallationDate() {
        return installationDate;
    }

    public void setInstallationDate(final Date installationDate) {
        this.installationDate = installationDate;
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

    public void addWarning(String warning) {
        if (rendition.equals(Rendition.HTML)) {
            warnings.add(new HistoryEntry(msgIndex, new Timestamp(
                    new Date().getTime()), "<font color='orange'><b>"
                    + MSG_IDENTIFIER_WARNING + " " + warning + "</b></font>"));
        } else if (rendition.equals(Rendition.TXT)) {
            warnings.add(new HistoryEntry(msgIndex, new Timestamp(
                    new Date().getTime()), MSG_IDENTIFIER_WARNING + " "
                    + warning));
        }
        msgIndex++;
    }

    public void addMessage(String message) {
        messages.add(new HistoryEntry(msgIndex, new Timestamp(new Date()
                .getTime()), " " + message));
        msgIndex++;
    }

    public void addError(final String exception) {
        if (rendition.equals(Rendition.HTML)) {
            exceptions.add(new HistoryEntry(msgIndex, new Timestamp(
                    new Date().getTime()), "<font color='red'><b>"
                    + MSG_IDENTIFIER_EXCEPTION + "</b>" + " " + exception
                    + "</b></font>"));
        } else if (rendition.equals(Rendition.TXT)) {
            exceptions.add(new HistoryEntry(msgIndex, new Timestamp(
                    new Date().getTime()), MSG_IDENTIFIER_EXCEPTION + " "
                    + exception));

        }
        success = false;
        msgIndex++;
    }

    public void addVerboseMessage(String message) {
        verboseMessages.add(new HistoryEntry(msgIndex, new Timestamp(
                new Date().getTime()), " " + message));
        msgIndex++;
    }

    public Set<HistoryEntry> getMessages() {
        return messages;
    }

    public Set<HistoryEntry> getException() {
        return exceptions;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n" + "Installation triggered: "
                + installationDate.toString() + "\n");

        sb.append("\n" + getMessageHistory() + "\n");

        sb.append("\n" + "Execution time: " + executionTime + " ms\n");

        if (rendition.equals(Rendition.HTML)) {
            if (success) {
                sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_OPEN);
            } else {
                sb.append(HtmlConstants.FONT_COLOR_NO_SUCCESS_HTML_OPEN);
            }
        }
        sb.append("\n" + "Success: " + success);

        if (rendition.equals(Rendition.HTML)) {
            sb.append(HtmlConstants.FONT_COLOR_SUCCESS_HTML_CLOSE);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public String getMessageHistory() {
        return getMessageString(getMessageSet(warnings, messages,
                exceptions));
    }

    @SuppressWarnings("unchecked")
    public String getVerboseMessageHistory() {
        return getMessageString(getMessageSet(warnings, messages,
                verboseMessages, exceptions));
    }

    private Set<HistoryEntry> getMessageSet(Set<HistoryEntry>... sets) {
        Set<HistoryEntry> resultSet = new TreeSet<HistoryEntry>(
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

}
