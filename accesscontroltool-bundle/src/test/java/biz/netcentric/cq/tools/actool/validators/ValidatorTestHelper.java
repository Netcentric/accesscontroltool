
package biz.netcentric.cq.tools.actool.validators;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;

import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.Yaml;

import biz.netcentric.cq.tools.actool.configmodel.AceBean;
import biz.netcentric.cq.tools.actool.configmodel.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configreader.ConfigReader;
import biz.netcentric.cq.tools.actool.validators.exceptions.AcConfigBeanValidationException;
import biz.netcentric.cq.tools.actool.validators.impl.AuthorizableValidatorImpl;

/** Helper class containing static methods used in validator-related unit tests based on test yaml files
 *
 * @author jochenkoschorkej */
public class ValidatorTestHelper {

    private ValidatorTestHelper() {
    }

    static void createAuthorizableTestBeans(final List<LinkedHashMap> yamlList, ConfigReader yamlConfigReader,
            List<AuthorizableConfigBean> authorizableBeanList)
            throws AcConfigBeanValidationException {
        final AuthorizableValidator authorizableValidator = new AuthorizableValidatorImpl("/home/groups", "/home/users");
        authorizableValidator.disable();
        final Set<AuthorizableConfigBean> groupsSet = yamlConfigReader
                .getGroupConfigurationBeans(yamlList, authorizableValidator);
        final Set<AuthorizableConfigBean> usersSet = yamlConfigReader
                .getUserConfigurationBeans(yamlList, authorizableValidator);
        authorizableBeanList.addAll(groupsSet);
        authorizableBeanList.addAll(usersSet);

    }

    static String getTestConfigAsString(final String resourceName)
            throws IOException {
        final ClassLoader classloader = Thread.currentThread()
                .getContextClassLoader();
        final InputStream is = classloader.getResourceAsStream(resourceName);

        final StringWriter stringWriter = new StringWriter();
        IOUtils.copy(is, stringWriter, "UTF-8");
        return stringWriter.toString();
    }

    static void createAceTestBeans(final List<LinkedHashMap> yamlList, ConfigReader yamlConfigReader, Set<String> groupsFromConfig,
            List<AceBean> aceBeanList, Session session)
            throws RepositoryException, AcConfigBeanValidationException {

        final Set<AceBean> aceBeans = yamlConfigReader.getAceConfigurationBeans(yamlList, null, session, "junit");

        for (AceBean bean : aceBeans) {
            aceBeanList.add(bean);
        }
    }

    static List<LinkedHashMap> getYamlList(final String path) throws IOException {
        final String configString = ValidatorTestHelper.getTestConfigAsString(path);

        final Yaml yaml = new Yaml();
        final List<LinkedHashMap> yamlList = (List<LinkedHashMap>) yaml
                .load(configString);
        return yamlList;
    }

    static String getSimpleValidationException(final AceBean aceBean,
            final AceBeanValidator aceBeanValidator, final AccessControlManager accessControlManager) {
        try {
            aceBeanValidator.validate(aceBean, accessControlManager);
        } catch (final Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }

    static String getSimpleValidationException(
            final AuthorizableConfigBean authorizableConfigBean,
            final AuthorizableValidator authorizableValidator) {
        try {
            authorizableValidator.validate(authorizableConfigBean);
        } catch (final Exception e) {
            return e.getClass().getSimpleName();
        }
        return "";
    }
}
