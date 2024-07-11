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

import org.slf4j.Logger;

import biz.netcentric.cq.tools.actool.api.InstallationLog;
import biz.netcentric.cq.tools.actool.api.InstallationResult;
/**
 * This is the SPI for writing to the logs.
 */
// TODO: remove extends Installation log to completely separate writing from reading
public interface InstallationLogger extends InstallationLog, InstallationResult {

    void addVerboseMessage(Logger log, String message);

    void addError(String error, Throwable e);

    void addError(Logger log, String error, Throwable e);

    void addMessage(Logger log, String message);

    void addWarning(Logger log, String warning);

    // all the following methods just modify some internal statistics 
    // TODO: move to dedicate interface
    void incMissingParentPathsForInitialContent();
    
    int getMissingParentPathsForInitialContent();

    void incCountActionCacheHit();

    void incCountActionCacheMiss();

    void incCountAclsPathDoesNotExist();

    void incCountAclsChanged();

    void incCountAclsNoChange();

    void incCountAuthorizablesCreated();
    void incCountAuthorizablesMoved();

    int getCountAuthorizablesCreated();
    int getCountAuthorizablesMoved();
}
