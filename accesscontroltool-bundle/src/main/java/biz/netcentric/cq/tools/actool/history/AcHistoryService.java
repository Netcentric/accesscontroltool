package biz.netcentric.cq.tools.actool.history;

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
     * Returns the log's content of a specific run of the AC Tool. The given index is volatile (due to history order changes), 
     * so consider using {@link #getLogFromHistory(String, boolean, boolean)} instead.
     * @param n the index of the child node below the history root node which should be returned
     * @param inHtmlFormat
     * @param includeVerbose
     * @return the log's content
     * @see #getLogFromHistory(String, boolean, boolean)
     */
    public String getLogFromHistory(int n, boolean inHtmlFormat, boolean includeVerbose);

    /**
     * Shortcut for {@link #getLogFromHistory(String, boolean, boolean, int)} with last argument being -1 (no line wrapping).
     * @param id
     * @param inHtmlFormat
     * @param includeVerbose
     * @return
     * @throws RepositoryException
     */
    public String getLogFromHistory(String id, boolean inHtmlFormat, boolean includeVerbose) throws RepositoryException;

    /**
     * Returns the log's content of a specific run of the AC Tool.
     * @param id the id of the child node below the history root node which should be returned
     * @param inHtmlFormat
     * @param includeVerbose
     * @param maxLineWidth
     * @return
     * @throws RepositoryException
     */
    public String getLogFromHistory(String id, boolean inHtmlFormat, boolean includeVerbose, int maxLineWidth) throws RepositoryException;

    public boolean wasLastPersistHistoryCallSuccessful();

}
