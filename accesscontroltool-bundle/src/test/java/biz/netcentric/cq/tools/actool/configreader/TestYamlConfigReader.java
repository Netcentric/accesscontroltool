package biz.netcentric.cq.tools.actool.configreader;

import java.util.Map;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;

/** Subclass of YamlConfigReader only used for unit tests. Overrides bean setup-methods from YamlConfigReader to set up TestAceBean and
 * TestAuthorizableConfigBean in order to set the assertedExceptionString set in test yaml files for later evaluation in unit tests. Also
 * overrides getNewAceBean() and getNewAuthorizableConfigBean() to return the correct testing type in order to make the downcast in
 * overridden setup-methods possible.
 *
 * @author jochenkoschorke */
public class TestYamlConfigReader extends YamlConfigReader {

    protected final String ASSERTED_EXCEPTION = "assertedException";

    @Override
    protected void setupAceBean(final String principal,
            final Map<String, ?> currentAceDefinition, final AceBean tmpAclBean) {
        super.setupAceBean(principal, currentAceDefinition, tmpAclBean);
        ((TestAceBean) tmpAclBean).setAssertedExceptionString(getMapValueAsString(
                currentAceDefinition, ASSERTED_EXCEPTION));
    }

    @Override
    protected void setupAuthorizableBean(
            final AuthorizableConfigBean authorizableConfigBean,
            final Map<String, String> currentPrincipalDataMap,
            final String authorizableId,
            boolean isGroupSection) {
        super.setupAuthorizableBean(authorizableConfigBean, currentPrincipalDataMap, authorizableId, isGroupSection);
        ((TestAuthorizableConfigBean) authorizableConfigBean).setAssertedExceptionString(getMapValueAsString(
                currentPrincipalDataMap, ASSERTED_EXCEPTION));
    }

    @Override
    protected AceBean getNewAceBean() {
        return new TestAceBean();
    }

    @Override
    protected AuthorizableConfigBean getNewAuthorizableConfigBean() {
        return new TestAuthorizableConfigBean();
    }

}
