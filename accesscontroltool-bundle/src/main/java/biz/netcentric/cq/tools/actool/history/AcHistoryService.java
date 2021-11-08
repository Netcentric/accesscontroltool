/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.history;

import java.util.List;

import org.osgi.annotation.versioning.ProviderType;

import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;

@ProviderType
public interface AcHistoryService {

    public void persistHistory(PersistableInstallationLogger history);

    public void persistAcePurgeHistory(PersistableInstallationLogger history);

    /** Returns history items of previous runs
     * 
     * @return Set of AcToolExecutions */
    public List<AcToolExecution> getAcToolExecutions();

    public String getLastInstallationHistory();

    public String getLogFromHistory(int n, boolean inHtmlFormat, boolean includeVerbose);

    public boolean wasLastPersistHistoryCallSuccessful();

}
