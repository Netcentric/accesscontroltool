/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.api;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

/**
 * Access to log messages being emitted
 *
 */
public interface InstallationLog extends AcInstallationHistoryPojo {

    // This is only set for the installhook mechanism
    String getCrxPackageName();

    String getMessageHistory();

    String getVerboseMessageHistory();
    
    // TODO: move to separate interface InstallationResult which extends this interface
    int getCountAclsUnchanged();

    int getCountAclsChanged();

    int getCountAclsPathDoesNotExist();

    int getCountActionCacheMiss();

    int getCountActionCacheHit();

    int getMissingParentPathsForInitialContent();

}