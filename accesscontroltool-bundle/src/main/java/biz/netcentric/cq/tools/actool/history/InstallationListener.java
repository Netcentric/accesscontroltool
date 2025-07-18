
package biz.netcentric.cq.tools.actool.history;

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

import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
/** Listener interface for installation events.
 * 
 * @deprecated Unused. Will be removed in a future release.
 */
@Deprecated()
public interface InstallationListener {
	
	void onWarning(String message);

	void onInfo(String message);
	
}
