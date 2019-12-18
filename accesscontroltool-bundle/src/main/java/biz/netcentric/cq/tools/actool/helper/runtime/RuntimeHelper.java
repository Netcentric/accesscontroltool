package biz.netcentric.cq.tools.actool.helper.runtime;

import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.settings.SlingSettingsService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.startlevel.FrameworkStartLevel;

/** Provides basic context information about the runtime. In own package so it can be inlined with "Conditional-Package" from startup bundle */
public class RuntimeHelper {
    
    private static final String RUNMODE_CLOUD_READY = "cloud-ready";

    public static boolean isCompositeNodeStore(Session session) {
        
        try {
            Node appsNode = session.getNode("/apps");
            // see https://issues.apache.org/jira/browse/OAK-6563
            boolean isCompositeNode = !session.hasCapability("addNode", appsNode, new Object[] { "nt:folder" });
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
    
    public static boolean isCloudReadyInstance(SlingSettingsService settingsService) {
        Set<String> runModes = settingsService.getRunModes();
        boolean isCloudReady = runModes.contains(RUNMODE_CLOUD_READY);
        return isCloudReady;
    }
    
    public static Node copyNode(Node src, Node dstParent) throws RepositoryException {
        String name = src.getName();
        Node dst = dstParent.addNode(name, src.getPrimaryNodeType().getName());
        for (PropertyIterator iter = src.getProperties(); iter.hasNext();) {
            copyProperty(iter.nextProperty(), dst);
        }
        for (NodeIterator iter = src.getNodes(); iter.hasNext();) {
            Node n = iter.nextNode();
            if (!n.getDefinition().isProtected()) {
                copyNode(n, dst);
            }
        }
        return dst;
    }

    public static Property copyProperty(Property src, Node dstParent) throws RepositoryException {
        if (!src.getDefinition().isProtected()) {
            String name = src.getName();
            if (src.getDefinition().isMultiple()) {
                return dstParent.setProperty(name, src.getValues());
            } else {
                return dstParent.setProperty(name, src.getValue());
            }
        }
        return null;
    }

}
