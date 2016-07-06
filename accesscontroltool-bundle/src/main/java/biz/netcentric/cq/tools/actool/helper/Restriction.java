package biz.netcentric.cq.tools.actool.helper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Value;

/** Bean holding the value(s) of single- or multivalued restriction
 * 
 * @author jochenkoschorkej */
public class Restriction {

    private String name;
    private Set<Value> values;

    public Restriction(final String name, final Value[] values) {
        this.name = name;
        this.values = new HashSet<Value>(Arrays.asList(values));
    }

    public Restriction(final String name, final Value value) {
        this.name = name;
        values = new HashSet<Value>(Collections.singleton(value));
    }

    public String getName() {
        return name;
    }

    public Set<Value> getValues() {
        return values;
    }

    public Value getValue() {
        return values.iterator().next();
    }

    public boolean isMultivalued() {
        return (values != null) && (values.size() > 1);
    }

}
