package biz.netcentric.cq.tools.actool.dumpservice;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.helper.AceBean;

public interface AcDumpElementVisitor {

    void visit(AuthorizableConfigBean authorizableConfigBean);

    void visit(AceBean aceBean);

    void visit(CommentingDumpElement commentingDumpElement);

    void visit(StructuralDumpElement structuralDumpElement);
}
