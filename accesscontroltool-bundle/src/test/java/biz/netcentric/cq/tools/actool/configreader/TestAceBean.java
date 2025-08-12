
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

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

/** Subclass of AceBean only used for unit testing. Has extra field for storing assertedExceptionStrings from test yaml files for later
 * evaluation in unit tests
 *
 * @author jochenkoschorke */
public class TestAceBean extends AceBean {

    private String assertedExceptionString;

    /** sets a assertedExceptionString from a extra attribute in a test yaml files
     *
     * @param assertedException */
    public void setAssertedExceptionString(final String assertedException) {
        assertedExceptionString = assertedException;
    }

    /** returns the assertedExceptionString file stemming from the definition in yaml file this AceBean is based on
     *
     * @return */
    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }
}
