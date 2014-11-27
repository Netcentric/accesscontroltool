package biz.netcentric.cq.tools.actool.dumpservice;

import java.util.Map;
import java.util.Set;

import biz.netcentric.cq.tools.actool.helper.AceBean;

/**
 * Helper class holding maps for storing ace dump data. One for storing the
 * "valid" ACEs (ACEs which belong to an authorizable that is installed under
 * /home) and one for storing "legacy" ACEs (ACEs which belong to a deleted
 * authorizable that is no more installed under /home)
 * 
 * @author jochenkoschorke
 *
 */

public class AceDumpData {

    Map<String, Set<AceBean>> aceDump;
    Map<String, Set<AceBean>> legacyAceDump;

    public Map<String, Set<AceBean>> getAceDump() {
        return aceDump;
    }

    public void setAceDump(Map<String, Set<AceBean>> aceDump) {
        this.aceDump = aceDump;
    }

    public Map<String, Set<AceBean>> getLegacyAceDump() {
        return legacyAceDump;
    }

    public void setLegacyAceDump(Map<String, Set<AceBean>> legacyAceDump) {
        this.legacyAceDump = legacyAceDump;
    }
}
