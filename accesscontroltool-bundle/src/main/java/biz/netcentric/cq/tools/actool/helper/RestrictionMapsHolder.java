package biz.netcentric.cq.tools.actool.helper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Value;
/**
 * Container class holding 2 maps for storing single and multivalue restrictions. singleValuedRestrictionsMap holds restriction names as keys and values of type javax.jcr.Value
 * multiValuedRestrictionsMap holds restriction names as keys and values of type javax.jcr.Value[]
 * @author jochenkoschorkej
 *
 */
public class RestrictionMapsHolder {
    private final static Map<String, Value> emptySingleValuedRestrictionsMap = Collections.unmodifiableMap(new HashMap<String, Value>());
    private final static Map<String, Value[]> emptyMultiValuedRestrictionsMap = Collections.unmodifiableMap(new HashMap<String, Value[]>());
    private final static RestrictionMapsHolder emptyHolder = new RestrictionMapsHolder(emptySingleValuedRestrictionsMap, emptyMultiValuedRestrictionsMap);

    private Map<String, Value> singleValuedRestrictionsMap = new HashMap<>();
    private Map<String, Value[]> multiValuedRestrictionsMap = new HashMap<>();

    public RestrictionMapsHolder(final Map<String, Value> singleValuedRestrictionsMap, final Map<String, Value[]> multiValuedRestrictionsMap) {
        super();
        this.singleValuedRestrictionsMap = singleValuedRestrictionsMap;
        this.multiValuedRestrictionsMap = multiValuedRestrictionsMap;
    }

    public Map<String, Value> getSingleValuedRestrictionsMap() {
        return singleValuedRestrictionsMap;
    }

    public Map<String, Value[]> getMultiValuedRestrictionsMap() {
        return multiValuedRestrictionsMap;
    }

    public boolean isEmpty(){
        return singleValuedRestrictionsMap.isEmpty() && multiValuedRestrictionsMap.isEmpty();
    }

    /**
     * returns a RestrictionMapsHolder object holding 2 unmodifiable empty restriction maps
     * @return
     */
    public static RestrictionMapsHolder emptyHolder(){
        return emptyHolder;
    }
}
