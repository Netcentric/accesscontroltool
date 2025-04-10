
package biz.netcentric.cq.tools.actool.history;

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
