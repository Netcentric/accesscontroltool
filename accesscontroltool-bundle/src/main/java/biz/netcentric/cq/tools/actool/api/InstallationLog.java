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

public interface InstallationLog extends AcInstallationHistoryPojo {

    String getCrxPackageName();

    String getMessageHistory();

    String getVerboseMessageHistory();

    // all the following statistics end up in messages, so there should be really no need to expose that
    @Deprecated
    int getCountAclsUnchanged();

    @Deprecated
    int getCountAclsChanged();

    @Deprecated
    int getCountAclsPathDoesNotExist();

    @Deprecated
    int getCountActionCacheMiss();

    @Deprecated
    int getCountActionCacheHit();

    @Deprecated
    int getMissingParentPathsForInitialContent();

}