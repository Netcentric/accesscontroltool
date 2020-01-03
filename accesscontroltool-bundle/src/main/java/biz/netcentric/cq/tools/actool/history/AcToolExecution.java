package biz.netcentric.cq.tools.actool.history;

import java.util.Date;

/**
 * Represents an execution of the AC Tool.
 */
public interface AcToolExecution {
    
    public String getLogsPath();

    public Date getInstallationDate();

    public boolean isSuccess();

    public String getTrigger();
    
    public String getConfigurationRootPath();
    
    public int getAuthorizableChanges();

    public int getAclChanges();
}
