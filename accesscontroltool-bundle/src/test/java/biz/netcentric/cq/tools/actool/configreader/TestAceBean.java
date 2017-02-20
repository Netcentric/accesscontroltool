package biz.netcentric.cq.tools.actool.configreader;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public class TestAceBean extends AceBean {

    private String assertedExceptionString;

    public void setAssertedExceptionString(final String assertedException) {
        assertedExceptionString = assertedException;
    }

    public String getAssertedExceptionString() {
        return assertedExceptionString;
    }
}
