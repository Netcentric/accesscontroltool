package biz.netcentric.cq.tools.actool.validators;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.AcConfiguration;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizablesConfig;
import biz.netcentric.cq.tools.actool.configmodel.GlobalConfiguration;
import biz.netcentric.cq.tools.actool.history.impl.PersistableInstallationLogger;
import biz.netcentric.cq.tools.actool.validators.exceptions.InvalidExternalGroupUsageValidationException;

@Component(service = ExternalGroupsInIsMemberOfValidator.class)
public class ExternalGroupsInIsMemberOfValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalGroupsInIsMemberOfValidator.class);

    public void validateIsMemberOfConfig(AcConfiguration acConfiguration, PersistableInstallationLogger installLog,
            GlobalConfiguration globalConfiguration) throws InvalidExternalGroupUsageValidationException {
        List<String> externalGroupsValidationResults = checkIsMemberOfConfigsOfAllAuthorizables(acConfiguration);
        if (!externalGroupsValidationResults.isEmpty()) {

            externalGroupsValidationResults.stream().forEach(m -> installLog.addWarning(LOG, m));

            String validationMsg = "Found " + externalGroupsValidationResults.size() + " authorizable(s) that use external groups in isMemberOf. ";

            if (Boolean.TRUE.equals(globalConfiguration.getAllowExternalGroupsInIsMemberOf())) {
                installLog.addWarning(LOG, validationMsg);
                installLog.addWarning(LOG, "Found global config 'allowExternalGroupsInIsMemberOf: true': PLEASE REFACTOR your groups structure to not use external groups in isMemberOf.");
            } else {
                installLog.addError(LOG, validationMsg + " If absolutely needed, use 'allowExternalGroupsInIsMemberOf: true' in global configuration, but prefer to refactor your groups structure to not use isMemberOf together with external groups.", null);
                throw new InvalidExternalGroupUsageValidationException(validationMsg);
            }
        }
    }

    private List<String> checkIsMemberOfConfigsOfAllAuthorizables(AcConfiguration acConfiguration) {

        List<String> validationErrors = new LinkedList<>();

        AuthorizablesConfig authorizablesConfig = acConfiguration.getAuthorizablesConfig();
        for (AuthorizableConfigBean authorizableConfigBean : authorizablesConfig) {
            if(authorizableConfigBean.getIsMemberOf() == null) {
                continue;
            }
            
            List<String> groupIdsWithExternalIdSet = Arrays.stream(authorizableConfigBean.getIsMemberOf())
                    .map(aId -> acConfiguration.getAuthorizablesConfig().getAuthorizableConfig(aId))
                    .filter(Objects::nonNull)
                    .filter(authBean -> StringUtils.isNotBlank(authBean.getExternalId()))
                    .map(AuthorizableConfigBean::getAuthorizableId)
                    .collect(Collectors.toList());

            if (!groupIdsWithExternalIdSet.isEmpty()) {
                validationErrors.add("The authorizable " + authorizableConfigBean.getAuthorizableId()
                        + " cannot use external group(s) in isMemberOf list: " + StringUtils.join(groupIdsWithExternalIdSet, ","));
            }
        }

        return validationErrors;
    }
}
