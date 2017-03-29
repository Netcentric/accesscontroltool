/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;
import biz.netcentric.cq.tools.actool.session.SessionManager;
import com.day.cq.commons.jcr.JcrConstants;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Component
public class YamlMacroChildNodeObjectsProviderImpl implements YamlMacroChildNodeObjectsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YamlMacroChildNodeObjectsProviderImpl.class);

    @Reference
    private SessionManager sessionManager;

    @Override
    public List<Object> getValuesForPath(String pathOfChildrenOfClause, AcInstallationHistoryPojo history) {

        LOG.debug("FOR Loop: Getting children for " + pathOfChildrenOfClause);

        List<Object> results = new ArrayList<Object>();

        Session session = null;
        try {
            session = sessionManager.getSession();

            Node node = session.getNode(pathOfChildrenOfClause);

            NodeIterator childrenIt = node.getNodes();
            while (childrenIt.hasNext()) {
                Node childNode = (Node) childrenIt.next();

                if (childNode.getName().startsWith("jcr:")
                        || childNode.getName().startsWith("rep:")
                        || childNode.getName().startsWith("oak:")) {
                    continue;
                }

                Map<String, Object> childNodeObjectForEl = new HashMap<String, Object>();

                childNodeObjectForEl.put("name", childNode.getName());
                childNodeObjectForEl.put("path", childNode.getPath());
                childNodeObjectForEl.put("primaryType", childNode.getPrimaryNodeType().toString());

                try {
                    Node jcrContentNode = childNode.getNode(JcrConstants.JCR_CONTENT);

                    PropertyIterator propertiesIt = jcrContentNode.getProperties();
                    Map<String, Object> jcrContentSubNode = new HashMap<String, Object>();
                    while (propertiesIt.hasNext()) {
                        Property prop = (Property) propertiesIt.next();
                        if (prop.isMultiple()) {
                            jcrContentSubNode.put(prop.getName(), valuesToStringArr(prop.getValues()));
                        } else {
                            String strVal = prop.getValue().getString();
                            jcrContentSubNode.put(prop.getName(), strVal);

                            // add the title also to root map to simplify access
                            if (JcrConstants.JCR_TITLE.equals(prop.getName())) {
                                childNodeObjectForEl.put("title", strVal);
                            }
                        }

                    }
                    childNodeObjectForEl.put(JcrConstants.JCR_CONTENT, jcrContentSubNode);

                } catch (PathNotFoundException epnf) {
                    LOG.debug("Node " + node.getPath() + " does not have a jcr content node (legitimate for folders)");
                }

                results.add(childNodeObjectForEl);
            }

        } catch (PathNotFoundException e) {
            history.addWarning(
                    "Path " + pathOfChildrenOfClause + " as configured for source for FOR loop does not exist! (statement skipped)");

        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not get children of path " + pathOfChildrenOfClause + ": " + e, e);
        } finally {
            sessionManager.close(session);
        }

        String msg = "Loop for children of " + pathOfChildrenOfClause + " evaluates to " + results.size() + " children";
        LOG.debug(msg);
        history.addMessage(msg);

        return results;
    }

    private String[] valuesToStringArr(Value[] values) throws ValueFormatException, RepositoryException {
        String[] strVals = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            strVals[i] = values[i].getString();
        }
        return strVals;
    }

}
