/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimestampPropertyComparator implements Comparator<Node> {

    private static final String PROPERTY_TIMESTAMP = "timestamp";

    final Logger LOG = LoggerFactory
            .getLogger(TimestampPropertyComparator.class);

    @Override
    public int compare(Node node1, Node node2) {
        long timestamp1 = 0;
        long timestamp2 = 0;

        try {
            timestamp1 = node1.getProperty(PROPERTY_TIMESTAMP).getLong();
            timestamp2 = node2.getProperty(PROPERTY_TIMESTAMP).getLong();
        } catch (ValueFormatException e) {
            LOG.error("Exception: ", e);
        } catch (PathNotFoundException e) {
            LOG.error("Exception: ", e);
        } catch (RepositoryException e) {
            LOG.error("Exception: ", e);
        }

        if (timestamp1 > timestamp2) {
            return -1;
        }
        if (timestamp1 < timestamp2) {
            return 1;
        }

        return 0;
    }

}
