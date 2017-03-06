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
        AceBean testAclBean = new TestAceBean();
        testAclBean.setPrincipal(principal);
        testAclBean.setJcrPath(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_PATH));
        testAclBean.setActionsStringFromConfig(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_ACTIONS));
        testAclBean.setPrivilegesString(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PRIVILEGES));
        testAclBean.setPermission(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PERMISSION));

        testAclBean.setRestrictions(currentAceDefinition.get(ACE_CONFIG_PROPERTY_RESTRICTIONS),
                (String) currentAceDefinition.get(ACE_CONFIG_PROPERTY_GLOB));
        ((TestAceBean) testAclBean).setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
        testAclBean.setActions(parseActionsString(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_ACTIONS)));

        testAclBean.setKeepOrder(Boolean.valueOf(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_KEEP_ORDER)));

        String initialContent = getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_INITIAL_CONTENT);
        testAclBean.setInitialContent(initialContent);
        return testAclBean;
    }

}
