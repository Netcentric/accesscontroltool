/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.helper;

import javax.jcr.LoginException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionUtil {
    public static final Logger LOG = LoggerFactory.getLogger(SessionUtil.class);

    public static Session cloneSession(Session session) throws LoginException, RepositoryException {
        Session clonedSession = session.impersonate(new SimpleCredentials(session.getUserID(), "".toCharArray()));
        return clonedSession;
    }

}
