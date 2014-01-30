package biz.netcentric.cq.tools.actool.helper;

public class AceBeanColorWrapper {

	private String color;
	private AceBean aceBean;
	
	public AceBeanColorWrapper(String color, AceBean aceBean) {
		super();
		this.color = color;
		this.aceBean = aceBean;
	}
	public String getColor() {
		return color;
	}
	public void setColor(String color) {
		this.color = color;
	}
	public AceBean getAceBean() {
		return aceBean;
	}
	public void setAceBean(AceBean aceBean) {
		this.aceBean = aceBean;
	}
}
