package biz.netcentric.cq.tools.actool.dumpservice;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AcHelper;
import biz.netcentric.cq.tools.actool.helper.AceBean;


public class AcDumpElementYamlVisitor implements AcDumpElementVisitor{
	
	private final static int PRINCIPAL_BASED_SORTING = 1;
	private final static int PATH_BASED_SORTING = 2;
	
	public static final int DUMP_INDENTATION_KEY = 4; 
	public static final int DUMP_INDENTATION_FIRST_PROPERTY = 7;
	public static final int DUMP_INDENTATION_PROPERTY = 9;
	
	public static final String YAML_STRUCTURAL_ELEMENT_PREFIX = "- ";
	private StringBuilder sb;
	private int mapOrder;
	
	public AcDumpElementYamlVisitor(final int mapOrder, final StringBuilder sb) {
		this.mapOrder = mapOrder;
		this.sb = sb;
	}
	
	@Override
	public void visit(final AuthorizableConfigBean authorizableConfigBean) {
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_KEY)).append("- " + authorizableConfigBean.getPrincipalID() + ":").append("\n");
		sb.append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY)).append("- name: ").append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("memberOf: " + authorizableConfigBean.getMemberOfString()).append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("path: " + authorizableConfigBean.getPath()).append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("isGroup: " + "'" + authorizableConfigBean.isGroup() + "'").append("\n");
		sb.append("\n");
	}

	@Override
	public void visit(final AceBean aceBean) {
		
		if(mapOrder == PATH_BASED_SORTING){
			sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY)).append("- principal: " + aceBean.getPrincipalName()).append("\n");
		}else if(mapOrder == PRINCIPAL_BASED_SORTING){
			sb.append(AcHelper.getBlankString(DUMP_INDENTATION_FIRST_PROPERTY)).append("- path: " + aceBean.getJcrPath()).append("\n");
		}
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("permission: " + aceBean.getPermission()).append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("actions: " + aceBean.getActionsString()).append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("privileges: " + aceBean.getPrivilegesString()).append("\n");
		sb.append(AcHelper.getBlankString(DUMP_INDENTATION_PROPERTY)).append("repGlob: ");
		if(!aceBean.getRepGlob().isEmpty()){
			sb.append("'" + aceBean.getRepGlob() + "'");
		}
		sb.append("\n");
		sb.append("\n");
	}
	
	@Override
	public void visit(final CommentingDumpElement commentingDumpElement) {
		sb.append(CommentingDumpElement.YAML_COMMENT_PREFIX + commentingDumpElement.getString());
		sb.append("\n");
		sb.append("\n");
	}

	@Override
	public void visit(StructuralDumpElement structuralDumpElement) {
		sb.append("\n");
		sb.append(AcHelper.getBlankString(structuralDumpElement.getLevel() * 2) + YAML_STRUCTURAL_ELEMENT_PREFIX  + structuralDumpElement.getString());
		sb.append("\n");
		sb.append("\n");
	}
}
