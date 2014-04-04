package biz.netcentric.cq.tools.actool.dumpservice;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;

public interface AcDumpElementVisitor {
	void visit(DumpComment dumpComment);
	void visit(AuthorizableConfigBean authorizableConfigBean);
	void visit(AceBean aceBean);
	void visit(String mapKey);
	void visit(GroupSectionKey GroupSectionKey);
	void visit(UserSectionKey userSectionKey);
	void visit(AceSectionKey aceSectionKey);
	void visit(LegacyAceSectionKey legacyAceSectionKey);
}
