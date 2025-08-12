package biz.netcentric.cq.tools.actool.configreader;

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

import java.util.List;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** Provides the objects for .
 * 
 * @author ghenzler */
public interface YamlMacroChildNodeObjectsProvider {

    List<Object> getValuesForPath(String pathOfChildrenOfClause, InstallationLogger history, Session session, boolean includeContent);

}
