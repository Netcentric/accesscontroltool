package biz.netcentric.cq.tools.actool.configreader;

import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

/** Subclass of AuthorizableConfigBean only used for unit testing. Has extra field for storing assertedExceptionStrings from test yaml files
 * for later evaluation in unit tests
 *
 * @author jochenkoschorke */
public class TestAuthorizableConfigBean extends AuthorizableConfigBean {

    private String assertedExceptionString;

    /** sets a assertedExceptionString from a extra attribute in a test yaml files
     *
     * @param assertedException */
    public void setAssertedExceptionString(final String assertedException) {
        assertedExceptionString = assertedException;
    }

    /** returns the assertedExceptionString file stemming from the definition in yaml file this AuthorizableConfigBean is based on
     *
     * @return */
    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }

}
