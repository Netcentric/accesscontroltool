package biz.netcentric.cq.tools.actool.history;

import java.util.Date;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents an execution of the AC Tool.
 */
@ProviderType
public interface AcToolExecution {
    
    public String getLogsPath();

    public Date getInstallationDate();

    public boolean isSuccess();

    public String getTrigger();
    
    public String getConfigurationRootPath();
    
    public int getAuthorizableChanges();

    public int getAclChanges();
}
