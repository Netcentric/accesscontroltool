package biz.netcentric.cq.tools.actool.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;
import biz.netcentric.cq.tools.actool.history.impl.HistoryUtils;

/**
 * Record hashes of config file sets in repository to allow to decide if another AC Tool execution is required
 */
@Component(service=AcConfigChangeTracker.class)
public class AcConfigChangeTracker {
    private static final Logger LOG = LoggerFactory.getLogger(AcConfigChangeTracker.class);

    public boolean configIsUnchangedComparedToLastExecution(Map<String, String> configFiles, String[] restrictedToPaths, Session session) {
        

        String executionKey = createExecutionKey(configFiles, restrictedToPaths, session);

        try {
            String hashOfConfigFilesThisExecution = createHashOverConfigFiles(configFiles);

            if(session.itemExists(HistoryUtils.ACHISTORY_PATH)) {
                Node acHistoryNode = session.getNode(HistoryUtils.ACHISTORY_PATH);

                String hashValueLastExecution = acHistoryNode.hasProperty(executionKey) ? acHistoryNode.getProperty(executionKey).getString() : null;
                
                if(StringUtils.equals(hashOfConfigFilesThisExecution, hashValueLastExecution)) {
                    LOG.info("Execution key {} with hash {} is equal", executionKey, hashOfConfigFilesThisExecution);
                    return true;
                }
                
                acHistoryNode.setProperty(executionKey, hashOfConfigFilesThisExecution);
                session.save(); // save directly to avoid parallel executions of the exact same config in AEMaaCS 
            } else {
                LOG.debug("Node {} does not exist yet", HistoryUtils.ACHISTORY_PATH);
            }

        } catch (Exception e) {
            LOG.info("Could not retrieve/save execution hash of config files: e="+e, e);
        }
        return false;
    }

    private String createExecutionKey(Map<String, String> configFiles, String[] restrictedToPaths, Session session) {
        boolean isCompositeNodeStore= RuntimeHelper.isCompositeNodeStore(session);
        String restrictedToPathsKey = restrictedToPaths==null || restrictedToPaths.length==0 ? "ALL_PATHS" : StringUtils.join(restrictedToPaths, "+").replace("$", "").replace("^", "");
        String effectiveRootPathOfConfigs = getEffectiveConfigRootPath(configFiles);
        String executionKey = "hash("+StringUtils.removeEnd(effectiveRootPathOfConfigs, "/").replace('/', '\\') + "," + restrictedToPathsKey.replace('/', '\\').replace(':', '_')+","+(isCompositeNodeStore?"compNodeStore":"stdRepo")+")";
        return executionKey;
    }

    static String getEffectiveConfigRootPath(Map<String, String> configFiles) {
        return StringUtils.getCommonPrefix(configFiles.keySet().toArray(new String[configFiles.size()]));
    }

    private String createHashOverConfigFiles(Map<String, String> configFiles) throws Exception {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String,String> test : configFiles.entrySet()) {
            buf.append(test.getKey()+"\n"+test.getValue());
        }

        return md5(buf.toString());
    }
    
    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hashInBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


}
