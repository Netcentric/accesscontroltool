/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.api;

import java.util.Set;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Access to log messages being emitted
 *
 */
@ProviderType
public interface InstallationLog {

    // This is only set for the installhook mechanism
    String getCrxPackageName();

    String getMessageHistory();

    String getVerboseMessageHistory();

    Set<HistoryEntry> getErrors();

    Set<HistoryEntry> getMessages();

}