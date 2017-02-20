package biz.netcentric.cq.tools.actool.configreader;

import java.util.Map;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public class TestYamlConfigReader extends YamlConfigReader {

    @Override
    protected AceBean setupAceBean(final String principal,
            final Map<String, ?> currentAceDefinition, AceBean tmpAclBean) {
        tmpAclBean = new TestAceBean();
        tmpAclBean.setPrincipal(principal);
        tmpAclBean.setJcrPath(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_PATH));
        tmpAclBean.setActionsStringFromConfig(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_ACTIONS));
        tmpAclBean.setPrivilegesString(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PRIVILEGES));
        tmpAclBean.setPermission(getMapValueAsString(
                currentAceDefinition, ACE_CONFIG_PROPERTY_PERMISSION));

        tmpAclBean.setRestrictions(currentAceDefinition.get(ACE_CONFIG_PROPERTY_RESTRICTIONS),
                (String) currentAceDefinition.get(ACE_CONFIG_PROPERTY_GLOB));
        ((TestAceBean) tmpAclBean).setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
        tmpAclBean.setActions(parseActionsString(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_ACTIONS)));

        tmpAclBean.setKeepOrder(Boolean.valueOf(getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_PROPERTY_KEEP_ORDER)));

        String initialContent = getMapValueAsString(currentAceDefinition,
                ACE_CONFIG_INITIAL_CONTENT);
        tmpAclBean.setInitialContent(initialContent);
        return tmpAclBean;
    }

}
