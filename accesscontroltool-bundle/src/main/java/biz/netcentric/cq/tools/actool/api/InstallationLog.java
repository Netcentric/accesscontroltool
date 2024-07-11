package biz.netcentric.cq.tools.actool.api;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

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
