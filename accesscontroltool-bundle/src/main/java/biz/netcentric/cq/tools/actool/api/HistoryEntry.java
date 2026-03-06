package biz.netcentric.cq.tools.actool.api;

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

import java.sql.Timestamp;
import java.time.ZonedDateTime;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents one log line of an execution of the AC Tool.
 */
@ProviderType
public final class HistoryEntry {

    private ZonedDateTime date;
    private String message;
    private long index;

    /**
     * Creates a new history entry. Calls {@link #HistoryEntry(long, ZonedDateTime, String)} internally with a converted timestamp using the current time zone.
     * @param index
     * @param timestamp
     * @param message
     * @deprecated Use {@link #HistoryEntry(long, ZonedDateTime, String)} instead.
     */
    @Deprecated
    public HistoryEntry(long index, Timestamp timestamp, String message) {
        this(index, timestamp.toInstant().atZone(ZonedDateTime.now().getZone()), message);
    }

    public HistoryEntry(long index, ZonedDateTime date, String message) {
        super();
        this.index = index;
        this.date = date;
        this.message = message;
    }

    @Deprecated
    public Timestamp getTimestamp() {
        return Timestamp.from(date.toInstant());
    }

    @Deprecated
    public void setTimestamp(Timestamp timestamp) {
        setDate(timestamp.toInstant().atZone(ZonedDateTime.now().getZone()));
    }

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

	@Override
	public String toString() {
		return "HistoryEntry [date=" + date + ", message=" + message
				+ ", index=" + index + "]";
	}


}
