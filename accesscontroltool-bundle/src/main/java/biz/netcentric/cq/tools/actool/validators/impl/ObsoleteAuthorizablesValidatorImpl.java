package biz.netcentric.cq.tools.actool.validators.impl;

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

import java.util.HashSet;
import java.util.Set;

import org.osgi.service.component.annotations.Component;

import biz.netcentric.cq.tools.actool.validators.ObsoleteAuthorizablesValidator;

@Component
public class ObsoleteAuthorizablesValidatorImpl implements ObsoleteAuthorizablesValidator {

    public void validate(Set<String> obsoleteAuthorizables, Set<String> authorizableIdsFromAllConfigs, String sourceFile) {

        HashSet<String> obsoleteAuthorizablesAlsoInRegularConfig = new HashSet<String>(authorizableIdsFromAllConfigs);
        obsoleteAuthorizablesAlsoInRegularConfig.retainAll(obsoleteAuthorizables);

        if (!obsoleteAuthorizablesAlsoInRegularConfig.isEmpty()) {
            throw new IllegalArgumentException(
                    "Some obsolete authorizables in " + sourceFile + " are also used in regular configuration: "
                            + obsoleteAuthorizablesAlsoInRegularConfig + "");
        }

    }


}
