package biz.netcentric.cq.tools.actool.api;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents an installation result
 *
 */
@ProviderType
public interface InstallationResult extends InstallationLog {

    int getCountAclsUnchanged();

    int getCountAclsChanged();

    int getCountAclsPathDoesNotExist();

    int getCountActionCacheMiss();

    int getCountActionCacheHit();

    int getMissingParentPathsForInitialContent();

    boolean isSuccess();
}
