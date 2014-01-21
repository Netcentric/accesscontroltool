package biz.netcentric.cq.tools.actool.installationhistory;

import javax.jcr.Session;


public interface AcHistoryService {
	
	public void persistHistory(AcInstallationHistoryPojo history);

	public String getLastInstallationLog();

	public String getLogHtml(Session session, String path);
}
