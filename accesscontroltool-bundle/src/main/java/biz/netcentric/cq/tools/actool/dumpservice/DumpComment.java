package biz.netcentric.cq.tools.actool.dumpservice;


public class DumpComment implements CommentingDumpElement{
	String comment;

	public DumpComment(final String comment) {
		this.comment = comment;
	}

	public String getString() {
		return comment;
	}
}
