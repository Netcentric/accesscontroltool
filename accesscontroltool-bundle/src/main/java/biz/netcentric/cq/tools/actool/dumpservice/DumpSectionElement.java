package biz.netcentric.cq.tools.actool.dumpservice;

public class DumpSectionElement implements StructuralDumpElement {

    private String value;
    public static final String YAML_DUMP_SECTION_PREFIX = "- ";

    public DumpSectionElement(final String value) {
        this.value = value;
    }

    public String getString() {
        return this.value;
    }

    @Override
    public int getLevel() {
        return 0;
    }
}
