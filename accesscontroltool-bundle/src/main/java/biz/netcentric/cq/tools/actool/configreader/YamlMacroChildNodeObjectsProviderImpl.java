/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;

import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.commons.jcr.JcrConstants;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

@Component
public class YamlMacroChildNodeObjectsProviderImpl implements YamlMacroChildNodeObjectsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YamlMacroChildNodeObjectsProviderImpl.class);

    @Reference
    private SlingRepository repository;

    @Override
    public List<Object> getValuesForPath(String pathOfChildrenOfClause, InstallationLogger history, Session session, boolean includeContent) {

        LOG.debug("FOR Loop: Getting children for {} with content {}", pathOfChildrenOfClause, includeContent);

        List<Object> results = new ArrayList<Object>();

        try {

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

                if (childNode.hasNode(JcrConstants.JCR_CONTENT)) {
                    Node jcrContentNode = childNode.getNode(JcrConstants.JCR_CONTENT);

                    if (jcrContentNode.hasProperty(JcrConstants.JCR_TITLE)) {
                        childNodeObjectForEl.put("title", jcrContentNode.getProperty(JcrConstants.JCR_TITLE).getString());
                    }

                    Map<String, Object> jcrContentSubNode = getValuesForNode(jcrContentNode, includeContent);
                    childNodeObjectForEl.put(JcrConstants.JCR_CONTENT, jcrContentSubNode);
                }

                results.add(childNodeObjectForEl);
            }

        } catch (PathNotFoundException e) {
            history.addWarning(LOG,
                    "Path " + pathOfChildrenOfClause + " as configured for source for FOR loop does not exist! (statement skipped)");

        } catch (RepositoryException e) {
            throw new IllegalStateException("Could not get children of path " + pathOfChildrenOfClause + ": " + e, e);
        }

        history.addVerboseMessage(LOG, "Loop for children of " + pathOfChildrenOfClause + " evaluates to " + results.size() + " children");

        return results;
    }

    private Map<String, Object> getValuesForNode(Node node, boolean includeChildren) throws RepositoryException {
        PropertyIterator propertiesIt = node.getProperties();
        Map<String, Object> values = new HashMap<String, Object>();
        while (propertiesIt.hasNext()) {
            Property prop = (Property) propertiesIt.next();
            if (prop.isMultiple()) {
                values.put(prop.getName(), valuesToStringArr(prop.getValues()));
            } else {
                Value value = prop.getValue();
                if (isIrrelevantType(value)) {
                    continue;
                }
                String strVal = value.getString();
                values.put(prop.getName(), strVal);
            }
        }

        if (includeChildren) {
            NodeIterator iterator = node.getNodes();
            while (iterator.hasNext()) {
                Node child = iterator.nextNode();
                values.put(child.getName(), getValuesForNode(child, includeChildren));
            }
        }

        return values;
    }

    private boolean isIrrelevantType(Value value) {
        return value.getType() == PropertyType.BINARY
                || value.getType() == PropertyType.REFERENCE
                || value.getType() == PropertyType.WEAKREFERENCE;
    }

    private String[] valuesToStringArr(Value[] values) throws ValueFormatException, RepositoryException {
        List<String> strVals = new ArrayList<String>();
        for (int i = 0; i < values.length; i++) {
            Value value = values[i];
            if (isIrrelevantType(value)) {
                continue;
            }
            strVals.add(value.getString());
        }
        return strVals.toArray(new String[strVals.size()]);
    }

}
