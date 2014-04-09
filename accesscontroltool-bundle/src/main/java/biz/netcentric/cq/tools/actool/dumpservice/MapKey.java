package biz.netcentric.cq.tools.actool.dumpservice;

public class MapKey implements StructuralDumpElement{

	private String key;
	public static final String YAML_MAP_KEY_PREFIX = "- ";
	
	public MapKey(final String key) {
		this.key = key;
	}
	
	@Override
	public String getString() {
		return this.key;
	}

	@Override
	public int getLevel() {
		return 2;
	}

}
