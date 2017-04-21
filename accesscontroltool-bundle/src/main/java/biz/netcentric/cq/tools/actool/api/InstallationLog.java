package biz.netcentric.cq.tools.actool.api;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

public interface InstallationLog extends AcInstallationHistoryPojo {

    String getCrxPackageName();

    String getMessageHistory();

    String getVerboseMessageHistory();

    int getCountAclsUnchanged();

    int getCountAclsChanged();

    int getCountAclsPathDoesNotExist();

    int getCountActionCacheMiss();

    int getCountActionCacheHit();

}