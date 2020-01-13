package biz.netcentric.cq.tools.actool.slingsettings;

import java.util.Set;

/** AC Tool SlingSettingsService in a way that also returns dev/stage/prod runmodes at runtime in the cloud. */
public interface ExtendedSlingSettingsService {

    /**
     * Return the set of activate run modes.
     * This set might be empty.
     * @return A non modifiable set of run modes.
     */
    Set<String> getRunModes();
    
    /**
     * The identifier of the running Sling instance.
     * @return The unique Sling identifier.
     */
    String getSlingId();
    
}
