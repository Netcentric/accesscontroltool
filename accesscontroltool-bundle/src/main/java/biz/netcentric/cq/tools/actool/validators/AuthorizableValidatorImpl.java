package biz.netcentric.cq.tools.actool.validators;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

public class AuthorizableValidatorImpl implements AuthorizableValidator {

    private static final Logger LOG = LoggerFactory
            .getLogger(AuthorizableValidatorImpl.class);
    private boolean enabled = true;
    AuthorizableConfigBean authorizableConfigBean;

    public AuthorizableValidatorImpl() {
    }

    public void validate(AuthorizableConfigBean authorizableConfigBean)
            throws AcConfigBeanValidationException {
        this.authorizableConfigBean = authorizableConfigBean;
        validate();
    }

    private boolean validate() throws AcConfigBeanValidationException {
        if (enabled) {
            return validateMemberOf(this.authorizableConfigBean)
                    && validateAuthorizableId(this.authorizableConfigBean);
        }
        return true;
    }

    public boolean validateMemberOf(
            AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException {
        String currentPrincipal = tmpPrincipalConfigBean.getPrincipalID();
        String currentEntryValue = tmpPrincipalConfigBean
                .getMemberOfStringFromConfig();
        if (StringUtils.isNotBlank(currentEntryValue)) {
            if (currentEntryValue != null) {

                String[] groups = currentEntryValue.split(",");

                for (int i = 0; i < groups.length; i++) {

                    // remove leading and trailing blanks from groupname
                    groups[i] = StringUtils.strip(groups[i]);

                    if (!Validators.isValidAuthorizableId(groups[i])) {
                        LOG.error(
                                "Validation error while reading group property of authorizable:{}, invalid authorizable name: {}",
                                currentPrincipal, groups[i]);
                        throw new InvalidGroupNameException(
                                "Validation error while reading group property of authorizable: "
                                        + currentPrincipal
                                        + ", invalid group name: " + groups[i]);
                    }
                }

                tmpPrincipalConfigBean.setMemberOf(groups);

            }
        }
        return true;
    }

    public boolean validateAuthorizableId(
            AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException {
        String currentPrincipal = tmpPrincipalConfigBean.getPrincipalID();

        if (Validators.isValidAuthorizableId((String) currentPrincipal)) {
            tmpPrincipalConfigBean.setPrincipalID((String) currentPrincipal);
        } else {
            String message = "Validation error while reading group data: invalid group name: "
                    + (String) currentPrincipal;
            LOG.error(message);
            throw new InvalidGroupNameException(message);

        }
        return true;
    }

    @Override
    public void setBean(AuthorizableConfigBean authorizableConfigBean) {
        this.authorizableConfigBean = authorizableConfigBean;
    }

    @Override
    public void disable() {
        this.enabled = false;

    }

}
