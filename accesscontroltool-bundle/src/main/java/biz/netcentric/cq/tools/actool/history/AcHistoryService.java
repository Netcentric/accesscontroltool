package biz.netcentric.cq.tools.actool.history;

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

import java.util.List;

import javax.jcr.RepositoryException;

import org.osgi.annotation.versioning.ProviderType;

import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;

@ProviderType
public interface AcHistoryService {

    public void persistHistory(PersistableInstallationLogger history);

    public void persistAcePurgeHistory(PersistableInstallationLogger history);

    /** Returns history items of previous runs
     * 
     * @return Set of AcToolExecutions 
     * @throws RepositoryException */
    public List<AcToolExecution> getAcToolExecutions() throws RepositoryException;

    public String getLastInstallationHistory();

    /**
     * Exposes the log contents of a specific run of the AC Tool. The given index is volatile (due to history order changes), 
     * so consider using {@link #getLogFromHistory(String, boolean, boolean)} instead.
     * @param n the index of the child node below the history root node which should be returned
     * @param inHtmlFormat
     * @param includeVerbose
     * @return the log's content
     * @see #getLogFromHistory(String, boolean, boolean)
     */
    public String getLogFromHistory(int n, boolean inHtmlFormat, boolean includeVerbose);

    public String getLogFromHistory(String id, boolean inHtmlFormat, boolean includeVerbose) throws RepositoryException;

    public boolean wasLastPersistHistoryCallSuccessful();

}
