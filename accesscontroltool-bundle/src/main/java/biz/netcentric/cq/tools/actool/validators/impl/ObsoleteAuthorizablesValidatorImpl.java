package biz.netcentric.cq.tools.actool.validators.impl;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
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
