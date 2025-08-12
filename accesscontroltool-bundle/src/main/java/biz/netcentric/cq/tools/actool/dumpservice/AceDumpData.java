package biz.netcentric.cq.tools.actool.dumpservice;

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

import java.util.Map;
import java.util.Set;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

/**
 * Helper class holding maps for storing ace dump data. One for storing the
 * "valid" ACEs (ACEs which belong to an authorizable that is installed under
 * /home) and one for storing "legacy" ACEs (ACEs which belong to a deleted
 * authorizable that is no more installed under /home)
 * 
 * @author jochenkoschorke
 *
 */

public class AceDumpData {

    Map<String, Set<AceBean>> aceDump;
    Map<String, Set<AceBean>> legacyAceDump;

    public Map<String, Set<AceBean>> getAceDump() {
        return aceDump;
    }

    public void setAceDump(Map<String, Set<AceBean>> aceDump) {
        this.aceDump = aceDump;
    }



}
