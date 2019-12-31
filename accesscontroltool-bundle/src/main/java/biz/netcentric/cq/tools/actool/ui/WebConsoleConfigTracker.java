package biz.netcentric.cq.tools.actool.ui;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.oak.commons.PropertiesUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { WebConsoleConfigTracker.class, ConfigurationListener.class })
public class WebConsoleConfigTracker implements ConfigurationListener {

    private static final Logger LOG = LoggerFactory.getLogger(WebConsoleConfigTracker.class);

    private static final String CONSOLE_PID = "org.apache.felix.webconsole.internal.servlet.OsgiManager";
    private static final String CONSOLE_ROOT_PROP = "manager.root";
    private static final String CONSOLE_ROOT_DEFAULT = "/system/console"; 
    
    private static final String CONSOLE_SEC_PROVIDER_PID = "org.apache.sling.extensions.webconsolesecurityprovider.internal.SlingWebConsoleSecurityProvider";
    private static final String CONSOLE_SEC_PROVIDER_USERS_PROP = "users";
    private static final String CONSOLE_SEC_PROVIDER_GROUPS_PROP = "groups";

    private static final String[] RELEVANT_PIDS = new String[] {CONSOLE_PID, CONSOLE_SEC_PROVIDER_PID};

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ConfigurationAdmin configAdmin;

    private String webConsoleRoot;

    private String[] allowedUsers = new String[] {};
    private String[] allowedGroups = new String[] {};

    @Activate
    private void updateConfig() {
        Configuration webconsoleConfig = getConfigForPid(CONSOLE_PID);
        if(webconsoleConfig != null) {
            webConsoleRoot = (String)  webconsoleConfig.getProperties().get(CONSOLE_ROOT_PROP);
        }
        webConsoleRoot = StringUtils.defaultIfBlank(webConsoleRoot, CONSOLE_ROOT_DEFAULT);

        Configuration webconsoleSecProviderConfig = getConfigForPid(CONSOLE_SEC_PROVIDER_PID);
        if(webconsoleSecProviderConfig != null) {
            allowedUsers = PropertiesUtil.toStringArray(webconsoleSecProviderConfig.getProperties().get(CONSOLE_SEC_PROVIDER_USERS_PROP));
            allowedGroups = PropertiesUtil.toStringArray(webconsoleSecProviderConfig.getProperties().get(CONSOLE_SEC_PROVIDER_GROUPS_PROP));
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("webConsoleRoot: {} allowedUsers: {} allowedGroups: {}", 
                    webConsoleRoot, 
                    ArrayUtils.toString(allowedUsers), 
                    ArrayUtils.toString(allowedGroups));
        }
    }

    @Override
    public void configurationEvent(ConfigurationEvent event) {
        String pid = event.getPid();
        if(ArrayUtils.contains(RELEVANT_PIDS, pid)) {
            updateConfig();
        }
    }

    public String getWebConsoleRoot() {
        return webConsoleRoot;
    }

    public String[] getAllowedUsers() {
        return allowedUsers;
    }

    public String[] getAllowedGroups() {
        return allowedGroups;
    }

    private Configuration getConfigForPid(String pid) {
        
        try {
            Configuration[] configs = configAdmin.listConfigurations("(service.pid="+pid+")");
            if(configs!=null && configs.length>0) {
                return configs[0];
            } else {
                return null;
            }            
        } catch (Exception e) {
            throw new IllegalStateException("Could retrieve configs for PID"+pid+": "+e, e);
        }
    }

    
}
