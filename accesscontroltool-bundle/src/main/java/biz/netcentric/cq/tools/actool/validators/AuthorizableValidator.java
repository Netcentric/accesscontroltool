package biz.netcentric.cq.tools.actool.validators;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidGroupNameException;

public interface AuthorizableValidator {

    public void validate(AuthorizableConfigBean authorizableConfigBean)
            throws AcConfigBeanValidationException;

    public boolean validateMemberOf(
            AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException;

    public boolean validateAuthorizableId(
            AuthorizableConfigBean tmpPrincipalConfigBean)
            throws InvalidGroupNameException;

    public void setBean(AuthorizableConfigBean authorizableConfigBean);

    public void disable();

}
