package biz.netcentric.cq.tools.actool.helper.runtime;

import java.util.Collection;
import java.util.Objects;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import javax.jcr.Node;
import javax.jcr.Session;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides basic context information about the runtime. 
 *  Located in its own package so it can be inlined with "Conditional-Package" from startup bundle */
public class RuntimeHelper {
    public static final Logger LOG = LoggerFactory.getLogger(RuntimeHelper.class);

    private static final String INSTALLER_CORE_BUNDLE_SYMBOLIC_ID = "org.apache.sling.installer.core";

    /**
     * 
     * @param session
     * @return
     * @deprecated Use {@link #getServerType(Collection)} instead
     */
    public static boolean isCompositeNodeStore(Session session) {
        
        try {
            String pathToCheck = "/apps";
            Node appsNode = session.getNode(pathToCheck);
            
            boolean hasPermission = session.hasPermission("/", Session.ACTION_SET_PROPERTY);
            if(!hasPermission) {
                // this can be ok for multitenancy cases that run with user of package installation (via install hook)
                LOG.info("AC Tool is running with a session (userID: "+session.getUserID()+") that does not have permissions '"+ Session.ACTION_SET_PROPERTY+"' at "+pathToCheck);
            }

            // see https://issues.apache.org/jira/browse/OAK-6563
            boolean hasCapability = session.hasCapability("addNode", appsNode, new Object[] { "nt:folder" });
            
            boolean isCompositeNode = hasPermission && !hasCapability;
            return isCompositeNode;
        } catch(Exception e) {
            throw new IllegalStateException("Could not check if session is connected to a composite node store: "+e, e);
        }
    }

    public static int getCurrentStartLevel() {
        return getCurrentStartLevel(FrameworkUtil.getBundle(RuntimeHelper.class).getBundleContext());
    }

    public static int getCurrentStartLevel(BundleContext bundleContext) {
        return bundleContext.getBundle(Constants.SYSTEM_BUNDLE_ID).adapt(FrameworkStartLevel.class).getStartLevel();
    }
    
    /**
     *    
     * @return {@code true} if the installer core bundle is not present (the case for all AEMaaCS runtimes running in the cloud)
     * @deprecated Use {@link #getServerType(Collection)} instead
     */
    @Deprecated
    public static boolean isCloudReadyInstance() {
        boolean isCloudReadyInstance = true;
        Bundle[] bundles = FrameworkUtil.getBundle(RuntimeHelper.class).getBundleContext().getBundles();
        for (Bundle bundle : bundles) {
            if(INSTALLER_CORE_BUNDLE_SYMBOLIC_ID.equals(bundle.getSymbolicName())) {
                isCloudReadyInstance = false;
                break;
            }
        }
        return isCloudReadyInstance;
    }

    /** 
     * The server type encapsulates information about the runtime environment, 
     * especially regarding the type of AEM instance (classic vs cloud) and whether it is running in a composite node store or not.
     */
    public enum ServerType {
        /** AEM 6.5 (LTS) and older */
        AEM_CLASSIC(false, false),
        /** AEM as a Cloud Service SDK (probably running locally) */
        AEM_CLOUD_SDK(true, false),
        /** AEM as a Cloud Service during Cloud Manager image build (with composite node store seed mode) */
        AEM_CLOUD_IMAGE_BUILD(true, true),
        /** AEM as a Cloud Service running in Adobe Cloud (with composite node store) */
        AEM_CLOUD_RUN(true, true),
        /** Any other (non AEM) Sling based runtime */
        SLING(false, false);
        
        private final boolean isAEMaaCS;
        private final boolean isInCloud;

        ServerType(boolean isAEMaaCS, boolean isInCloud) {
            this.isAEMaaCS = isAEMaaCS;
            this.isInCloud = isInCloud;
        }

        /**
         * Indicates whether this server type is an AEM as a Cloud Service runtime (either SDK, image build or cloud run).
         */
        public boolean isAEMaaCS() {
            return isAEMaaCS;
        }

        /**
         * Indicates whether this server type is running in the cloud with composite node store (either image build or cloud run).
         */
        public boolean isInCloud() {
            return isInCloud;
        }
    }

    /**
     * Determines the server type based on the provided run modes.
     * @param runModes The collection of active run modes.
     * @return The determined {@link ServerType}.
     */
    public static ServerType getServerType(Collection<String> runModes) {
        Objects.requireNonNull(runModes, "Run modes collection cannot be null");
        // "crx3" is available on all AEMs
        // "crx3composite-seed" is only available on composite node store seed mode (which is currently only used for cloud image build)
        // "sdk" (next to "crx3, crx3tarmk, crx3tar) is only available on cloud sdk
        // "crx3composite" and "cloud-ready" in cloud run instances
        // everything else would be Sling
        if(runModes.contains("sdk")) {
            return ServerType.AEM_CLOUD_SDK;
        } else if(runModes.contains("crx3composite-seed")) {
            return ServerType.AEM_CLOUD_IMAGE_BUILD;
        } else if(runModes.contains("crx3composite")) {
            return ServerType.AEM_CLOUD_RUN;
        } else if(runModes.contains("crx3")) {
            return ServerType.AEM_CLASSIC;
        } else {
            return ServerType.SLING;
        }
    }
}
