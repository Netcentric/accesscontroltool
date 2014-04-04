package biz.netcentric.cq.tools.actool.dumpservice;



import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;
import biz.netcentric.cq.tools.actool.helper.Constants;

public class AcDumpElementYamlVisitor implements AcDumpElementVisitor{
	
	private final static int PRINCIPAL_BASED_SORTING = 1;
	private final static int PATH_BASED_SORTING = 2;
	private StringBuilder sb;
	private int mapOrder;
	
	public AcDumpElementYamlVisitor(final int mapOrder, StringBuilder sb) {
		
		this.mapOrder = mapOrder;
		this.sb = sb;
		// add creation date and URL of current author instance as first line 
//		sb.append("# Dump created: " + new Date() + " on: " + serverUrl);
//		sb.append("\n\n");
	}
	
	@Override
	public void visit(final AuthorizableConfigBean authorizableConfigBean) {
		sb.append(Constants.DUMP_INDENTATION_KEY + "- " + authorizableConfigBean.getPrincipalID() + ":").append("\n");
		sb.append("\n");
		sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- name: ").append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "memberOf: " + authorizableConfigBean.getMemberOfString()).append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "path: " + authorizableConfigBean.getPath()).append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "isGroup: " + "'" + authorizableConfigBean.isGroup() + "'").append("\n");
		sb.append("\n");
	}

	@Override
	public void visit(final AceBean aceBean) {
		sb.append("\n");
		if(mapOrder == PATH_BASED_SORTING){
			sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- principal: " + aceBean.getPrincipalName()).append("\n");
		}else if(mapOrder == PRINCIPAL_BASED_SORTING){
			sb.append(Constants.DUMP_INDENTATION_FIRST_PROPERTY + "- path: " + aceBean.getJcrPath()).append("\n");
		}
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "permission: " + aceBean.getPermission()).append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "actions: " + aceBean.getActionsString()).append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "privileges: " + aceBean.getPrivilegesString()).append("\n");
		sb.append(Constants.DUMP_INDENTATION_PROPERTY + "repGlob: ");
		if(!aceBean.getRepGlob().isEmpty()){
			sb.append("'" + aceBean.getRepGlob() + "'");
		}
		sb.append("\n");

	}

	@Override
	public void visit(String mapKey) {
		sb.append("\n");
		sb.append(Constants.DUMP_INDENTATION_KEY + "- " + mapKey + ":");
		sb.append("\n");
	}

	@Override
	public void visit(GroupSectionKey groupSectionKey) {
		sb.append("\n");
		sb.append("- " + groupSectionKey.getKey() + ":");
		sb.append("\n");
		sb.append("\n");
		
	}
	
	@Override
	public void visit(AceSectionKey aceSectionKey) {
		sb.append("\n");
		sb.append("- " + aceSectionKey.getKey() + ":");
		sb.append("\n");
		sb.append("\n");
	}
	
	@Override
	public void visit(UserSectionKey userSectionKey) {
		sb.append("\n");
		sb.append("- " + userSectionKey.getKey() + ":");
		sb.append("\n");
		sb.append("\n");
		
	}

	@Override
	public void visit(LegacyAceSectionKey legacyAceSectionKey) {
		sb.append("\n");
		sb.append("- " + legacyAceSectionKey.getKey() + ":");
		sb.append("\n");
		sb.append("\n");
	}

	@Override
	public void visit(DumpComment dumpComment) {
		sb.append(dumpComment.getComment());
		sb.append("\n");
		sb.append("\n");
		
	}
}
