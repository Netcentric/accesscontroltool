package biz.netcentric.cq.tools.actool.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Value;

/** Container class holding list containing (single and multivalue) restrictions
 *
 * @author jochenkoschorkej */
public class RestrictionModel {
    private final static RestrictionModel EMPTY = new RestrictionModel(Collections.unmodifiableList(new ArrayList<Restriction>()));
    private List<Restriction> restrictions;

    private Map<String, Value> singleValuedRestrictions = new HashMap<String, Value>();
    private Map<String, Value[]> multiValuedRestrictions = new HashMap<String, Value[]>();

    public RestrictionModel(List<Restriction> restrictions) {
        this.restrictions = restrictions;
        buildRestrictionMaps(restrictions);
    }

    private void buildRestrictionMaps(final List<Restriction> restrictions) {
        for (Restriction restriction : restrictions) {
            if (!restriction.isMultivalued()) {
                singleValuedRestrictions.put(restriction.getName(), restriction.getValue());
            } else {
                multiValuedRestrictions.put(restriction.getName(),
                        restriction.getValues().toArray(new Value[restriction.getValues().size()]));
            }
        }
    }

    public List<Restriction> getRestrictions() {
        return restrictions;
    }

    public boolean isEmpty() {
        return restrictions.isEmpty();
    }

    /** returns a RestrictionsModel object holding an empty restriction list
     *
     * @return empty RestrictionsModel */
    public static RestrictionModel empty() {
        return EMPTY;
    }

    public Map<String, Value> getSingleValuedRestrictionsMap() {
        return singleValuedRestrictions;
    }

    public Map<String, Value[]> getMultiValuedRestrictionsMap() {
        return multiValuedRestrictions;
    }
}
