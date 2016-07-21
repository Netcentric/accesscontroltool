package biz.netcentric.cq.tools.actool.configmodel;

import java.util.Arrays;
import java.util.List;

/** Configuration bean holding the value(s) of single- or multivalued restriction
 * 
 * @author jochenkoschorkej */
public class Restriction {

    private String name;
    private List<String> values; // for single val restrictions, this field is filled with one value

    public Restriction(final String name, final String[] values) {
        this.name = name;
        this.values = Arrays.asList(values);
    }

    public Restriction(final String name, final String value) {
        this.name = name;
        values = Arrays.asList(value);
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }

    public String getValue() {
        return values.iterator().next();
    }

    public boolean isMultivalued() {
        return (values != null) && (values.size() > 1);
    }

    @Override
    public String toString() {
        return "[Restriction name=" + name + ", values=" + values + "]";
    }

}
