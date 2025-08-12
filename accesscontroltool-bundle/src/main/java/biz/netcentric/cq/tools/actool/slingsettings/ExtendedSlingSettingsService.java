/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 */
package biz.netcentric.cq.tools.actool.slingsettings;

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

import java.util.Set;

/** AC Tool SlingSettingsService in a way that also returns dev/stage/prod runmodes at runtime in the cloud. */
public interface ExtendedSlingSettingsService {

    /**
     * Return the set of activate run modes.
     * This set might be empty.
     * @return A non modifiable set of run modes.
     */
    Set<String> getRunModes();
    
    /**
     * The identifier of the running Sling instance.
     * @return The unique Sling identifier.
     */
    String getSlingId();

    boolean isMatchingRunModeSpec(String spec);
    
}
