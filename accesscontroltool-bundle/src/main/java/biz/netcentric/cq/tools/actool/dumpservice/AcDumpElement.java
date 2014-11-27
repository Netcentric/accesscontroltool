package biz.netcentric.cq.tools.actool.dumpservice;

public interface AcDumpElement {
    void accept(AcDumpElementVisitor acDumpElementVisitor);
}
