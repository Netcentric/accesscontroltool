/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

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
