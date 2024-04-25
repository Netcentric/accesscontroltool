package biz.netcentric.cq.tools.actool.externalusermanagement;

import java.io.IOException;
import java.util.Collection;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

/**
 * Implementations of this service synchronize (i.e. create/update/delete) groups in an external directory (outside AEM).
 */
public interface ExternalGroupManagement {
    void updateGroups(Collection<AuthorizableConfigBean> groupConfigs) throws IOException;

    /**
     * 
     * @return a label for the external group management tool (e.g. IMS)
     */
    String getLabel();
}
