package biz.netcentric.cq.tools.actool.installhook;

import javax.jcr.Session;

import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

import com.day.jcr.vault.fs.io.Archive;

public interface AcToolInstallHookService {

	public AcInstallationHistoryPojo installYamlFilesFromPackage(Archive archive, Session session)
			throws Exception;

}