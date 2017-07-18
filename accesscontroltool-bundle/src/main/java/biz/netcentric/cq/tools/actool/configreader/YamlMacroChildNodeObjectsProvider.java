/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.List;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.history.AcInstallationLog;

/** Provides the objects for .
 * 
 * @author ghenzler */
public interface YamlMacroChildNodeObjectsProvider {

    List<Object> getValuesForPath(String pathOfChildrenOfClause, AcInstallationLog history, Session session);

}