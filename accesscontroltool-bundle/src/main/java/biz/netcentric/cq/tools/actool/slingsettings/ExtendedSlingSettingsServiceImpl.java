package biz.netcentric.cq.tools.actool.slingsettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @ObjectClassDefinition(name = "AC Tool Extended Runmodes", 
            description="Add additional runmodes (useful for the cloud where dev/stage/prod are only available at image build time)")
    protected static @interface Config {
        
        @AttributeDefinition(name="Additional Runmodes", description="Additional runmodes to be considered for AC Tool configs")
        String[] additionalRunmodes() default {};
    }
    
    
    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingSettingsService slingSettingsService;

    private Set<String> extendedRunmodes;
    
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

}
