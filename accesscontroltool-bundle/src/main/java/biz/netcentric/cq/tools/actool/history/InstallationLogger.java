package biz.netcentric.cq.tools.actool.history;

import org.slf4j.Logger;

import biz.netcentric.cq.tools.actool.api.InstallationLog;

// TODO: remove extends Installation log to completely separate writing from reading
public interface InstallationLogger extends InstallationLog {

    void addVerboseMessage(Logger log, String message);

    void addError(final String error);

    void addError(Logger log, String error);

    void addError(Logger log, String error, Throwable e);

    void addMessage(Logger log, String message);

    void addWarning(Logger log, String warning);

    // all the following methods just modify some internal statistics which anyhow ends up as messages in the log
    @Deprecated
    void incMissingParentPathsForInitialContent();
    
    @Deprecated
    int getMissingParentPathsForInitialContent();

    @Deprecated
    void incCountActionCacheHit();

    @Deprecated
    void incCountActionCacheMiss();

    @Deprecated
    void incCountAclsPathDoesNotExist();

    @Deprecated
    void incCountAclsChanged();

    @Deprecated
    void incCountAclsNoChange();


}
