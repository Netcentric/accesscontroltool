package biz.netcentric.cq.tools.actool.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.configmodel.Restriction;

/** Container class holding list containing (single and multivalue) restrictions
 *
 * @author jochenkoschorkej */
public class RestrictionsHolder {
    public static final Logger LOG = LoggerFactory.getLogger(ContentHelper.class);

    private final static RestrictionsHolder EMPTY = new RestrictionsHolder(Collections.unmodifiableList(new ArrayList<Restriction>()), null,
            null);
    private List<Restriction> restrictions;

    private final ValueFactory valueFactory;
    private final JackrabbitAccessControlList acl;

    private Map<String, Value> singleValuedRestrictions = new HashMap<String, Value>();
    private Map<String, Value[]> multiValuedRestrictions = new HashMap<String, Value[]>();

    public RestrictionsHolder(List<Restriction> restrictions, ValueFactory valueFactory, JackrabbitAccessControlList acl) {
        this.restrictions = restrictions;
        this.valueFactory = valueFactory;
        this.acl = acl;
        buildRestrictionsMaps(valueFactory, acl);
    }

    private void buildRestrictionsMaps(ValueFactory valueFactory, JackrabbitAccessControlList acl) {

        for (Restriction restriction : restrictions) {
            try {
                if (restriction.isMultivalued()) {
                    List<String> strValues = restriction.getValues();
                    final Value[] values = new Value[strValues.size()];
                    for (int i = 0; i < strValues.size(); i++) {
                        final Value value = valueFactory.createValue(strValues.get(i),
                                acl.getRestrictionType(restriction.getName()));
                        values[i] = value;
                    }
                    multiValuedRestrictions.put(restriction.getName(), values);
                } else {
                    final Value value = valueFactory.createValue(restriction.getValue(),
                            acl.getRestrictionType(restriction.getName()));
                    singleValuedRestrictions.put(restriction.getName(), value);
                }
            } catch (Exception e) {
                LOG.warn("Could not create value for restriction " + restriction.getName() + ", e=" + e, e);
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
    public static RestrictionsHolder empty() {
        return EMPTY;
    }

    public Map<String, Value> getSingleValuedRestrictionsMap()
            throws ValueFormatException, RepositoryException {
        return singleValuedRestrictions;
    }

    public Map<String, Value[]> getMultiValuedRestrictionsMap()
            throws ValueFormatException, RepositoryException {
        return multiValuedRestrictions;
    }


}
