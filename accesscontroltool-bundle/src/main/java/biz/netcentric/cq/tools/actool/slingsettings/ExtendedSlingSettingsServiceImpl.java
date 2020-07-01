/*
 * (C) Copyright 2020 Netcentric, A Cognizant Digital Business.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.slingsettings;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.sling.settings.SlingSettingsService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.helper.runtime.RuntimeHelper;

@Component(service = {ExtendedSlingSettingsService.class})
@Designate(ocd=ExtendedSlingSettingsServiceImpl.Config.class)
public class ExtendedSlingSettingsServiceImpl implements ExtendedSlingSettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(ExtendedSlingSettingsServiceImpl.class);

    private static final String ADDITIONAL_RUNMODE_CLOUD = "cloud";
    private static final  String RUN_MODE_SPEC_OR_SEPARATOR = ",";
    private static final String RUN_MODE_SPEC_AND_SEPARATOR = ".";
    private static final String RUN_MODE_SPEC_NOT_PREFIX = "-";

    @ObjectClassDefinition(name = "AC Tool Extended Runmodes", 
            description="Add additional runmodes (useful for the cloud where dev/stage/prod are only available at image build time)")
    protected static @interface Config {
        
        @AttributeDefinition(name="Additional Runmodes", description="Additional runmodes to be considered for AC Tool configs")
        String[] additionalRunmodes() default {};
    }
    
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingSettingsService slingSettingsService;

    private Set<String> extendedRunmodes;
    
    public ExtendedSlingSettingsServiceImpl() {
        
    }

    // constructor for testing purposes
    public ExtendedSlingSettingsServiceImpl(Set<String> extendedRunmodes) {
        this.extendedRunmodes = extendedRunmodes;
    }

    @Activate
    public void activate(Config config) {
        Set<String> defaultRunmodes = slingSettingsService.getRunModes();
        extendedRunmodes = new HashSet<>();
        extendedRunmodes.addAll(defaultRunmodes);
        boolean isCloudReady = RuntimeHelper.isCloudReadyInstance();
        if(isCloudReady) {
            extendedRunmodes.add(ADDITIONAL_RUNMODE_CLOUD);
        }

        if (config.additionalRunmodes() != null) {
            List<String> additionalRunmodes = Arrays.asList(config.additionalRunmodes());
            extendedRunmodes.addAll(additionalRunmodes);
        }

        LOG.info("Default runmodes: {} Extended Runmodes: {}  isCloudReady: {}", defaultRunmodes, extendedRunmodes, isCloudReady);
    }

    @Override
    public Set<String> getRunModes() {
        return extendedRunmodes;
    }

    @Override
    public String getSlingId() {
        return slingSettingsService.getSlingId();
    }

    @Override
    public boolean isMatchingRunModeSpec(String spec) {
        return getBestRunModeMatchCountFromSpec(spec, extendedRunmodes) > 0;
    }

    // copied from https://github.com/apache/sling-org-apache-sling-settings/blob/b8fa5314f9b13e9fcf1b8f8f56ab0812fce2f5e5/src/main/java/org/apache/sling/settings/impl/SlingSettingsServiceImpl.java#L332
    static int getBestRunModeMatchCountFromSpec(String spec, Collection<String> activeRunModes) {
        int numMatchingRunModes = 0;
        // 1. support OR
        for (String discjunctivePart : spec.split(Pattern.quote(RUN_MODE_SPEC_OR_SEPARATOR))) {
            int newNumMatchingRunModes = getBestRunModeMatchCountFromConjunctions(discjunctivePart, activeRunModes);
            if (newNumMatchingRunModes > numMatchingRunModes) {
                numMatchingRunModes = newNumMatchingRunModes;
            }
        }
        return numMatchingRunModes;
    }

    static int getBestRunModeMatchCountFromConjunctions(String conjunctions, Collection<String> activeRunModes) {
        int numMatchingRunModes = 0;
        // 2. support AND
        for (String conjunctivePart : conjunctions.split(Pattern.quote(RUN_MODE_SPEC_AND_SEPARATOR))) {
            // 3. support NOT operator
            if (conjunctivePart.startsWith(RUN_MODE_SPEC_NOT_PREFIX)) {
                if (activeRunModes.contains(conjunctivePart.substring(RUN_MODE_SPEC_NOT_PREFIX.length()))) {
                    return 0;
                }
            } else {
                if (!activeRunModes.contains(conjunctivePart)) {
                    return 0;
                }
            }
            numMatchingRunModes++;
        }
        return numMatchingRunModes;
    }

}
