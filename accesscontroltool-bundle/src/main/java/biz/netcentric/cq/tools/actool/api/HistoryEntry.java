/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.api;

import java.sql.Timestamp;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents one log line of an execution of the AC Tool.
 */
@ProviderType
public final class HistoryEntry {

    private Timestamp timestamp;
    private String message;
    private long index;

    public HistoryEntry(long index, Timestamp timestamp, String message) {
        super();
        this.index = index;
        this.timestamp = timestamp;
        this.message = message;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
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
		return "HistoryEntry [timestamp=" + timestamp + ", message=" + message
				+ ", index=" + index + "]";
	}


}
