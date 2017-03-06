package biz.netcentric.cq.tools.actool.configreader;

import java.util.Map;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

/** Subclass of YamlConfigReader only used for unit tests. Creates a TestAceBean in order to set the assertedExceptionString set in test
 * yaml files for later evaluation in unit tests.
 *
 * @author jochenkoschorke */
public class TestYamlConfigReader extends YamlConfigReader {

    protected final String ASSERTED_EXCEPTION = "assertedException";

    @Override
    protected AceBean setupAceBean(final String principal,
            final Map<String, ?> currentAceDefinition) {
        TestAceBean testAclBean = (TestAceBean) super.setupAceBean(principal, currentAceDefinition);
        testAclBean.setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
        return testAclBean;
    }

}
