/*
 * (C) Copyright 2016 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.aceinstaller;

import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** Installs AceBeans to content paths. */
public interface AceBeanInstaller {

    /** Method which installs all ACE contained in the configurations. if an ACL is already existing in CRX the ACEs from the config get
     * merged into the ACL (the ones from config overwrite the ones in CRX) ACEs belonging to groups which are not contained in any
     * configuration don't get altered
     *
     * @param pathBasedAceMapFromConfig map containing the ACE data from the merged configurations path based
     * @param session the jcr session
     * @param installationLog the installation log
     * @param authorizablesToRemoveAcesFor */
    void installPathBasedACEs(final Map<String, Set<AceBean>> pathBasedAceMapFromConfig, final AcConfiguration acConfiguration, final Session session,
            final InstallationLogger installationLog, Set<String> authorizablesToRemoveAcesFor) throws Exception;

}