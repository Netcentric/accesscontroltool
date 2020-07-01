/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.slingsettings;

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
