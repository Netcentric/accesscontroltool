package biz.netcentric.cq.tools.actool.dumpservice;

import java.util.Map;
import java.util.Set;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configuration.CqActionsMapping;
import biz.netcentric.cq.tools.actool.helper.AceBean;

public class CompleteAcDump implements AcDumpElement{
	
	private AceDumpData aceDumpData;
	private Set<AuthorizableConfigBean> groupSet;
	private Set<AuthorizableConfigBean> userSet;
	private String serverUrl;
	private String dumpComment;
	
	private Dumpservice dumpservice;
	
	public CompleteAcDump(AceDumpData aceDumpData, final Set<AuthorizableConfigBean> groupSet, final Set<AuthorizableConfigBean> userSet, final int mapOrder, final String serverUrl, final String dumpComment, Dumpservice dumpservice){
		this.aceDumpData = aceDumpData;
		this.groupSet = groupSet;
		this.userSet = userSet;
		this.serverUrl = serverUrl;
		this.dumpComment = dumpComment;
		this.dumpservice = dumpservice;
	}
	
	@Override
	public void accept(AcDumpElementVisitor acDumpElementVisitor) {
		Map<String, Set<AceBean>> aceMap = this.aceDumpData.getAceDump();
		Map<String, Set<AceBean>> legacyAceMap = this.aceDumpData.getLegacyAceDump();
		
		// render group section label
		acDumpElementVisitor.visit(new DumpComment(this.dumpComment));
		
		// render group section label
		acDumpElementVisitor.visit(new GroupSectionKey());
		
		// render groupBeans
		renderAuthorizableBeans(acDumpElementVisitor, groupSet);

		if(dumpservice.isIncludeUsers()){
			// render user section label
			acDumpElementVisitor.visit(new UserSectionKey());
			// render userBeans
			renderAuthorizableBeans(acDumpElementVisitor, userSet);
		}
		
		// render ace section label
		acDumpElementVisitor.visit(new AceSectionKey());

		// render aceBeans
		renderAceBeans(acDumpElementVisitor, aceMap);
		
		if(dumpservice.isShowLegacyAces()){
			// render legacy ACEs section label
			acDumpElementVisitor.visit(new LegacyAceSectionKey());
			renderAceBeans(acDumpElementVisitor, legacyAceMap);
		}
	}

	private void renderAuthorizableBeans(AcDumpElementVisitor acDumpElementVisitor, final Set<AuthorizableConfigBean> authorizableBeans) {
		for(AuthorizableConfigBean authorizableConfigBean : authorizableBeans){
			authorizableConfigBean.accept(acDumpElementVisitor);
		}
	}

	private void renderAceBeans(AcDumpElementVisitor acDumpElementVisitor,
			Map<String, Set<AceBean>> aceMap) {
		for(Map.Entry<String, Set<AceBean>> entry : aceMap.entrySet()){
			Set<AceBean> aceBeanSet = entry.getValue();

			String mapKey = entry.getKey();
			acDumpElementVisitor.visit(mapKey);
			for(AceBean aceBean : aceBeanSet){
				aceBean = CqActionsMapping.getAlignedPermissionBean(aceBean);
				aceBean.accept(acDumpElementVisitor);
			}
		}
	}
}
