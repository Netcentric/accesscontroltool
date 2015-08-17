package biz.netcentric.cq.tools.actool.configReader;

import java.util.Map;

import javax.jcr.Session;

import com.day.jcr.vault.fs.io.Archive;

public interface ConfigFilesRetriever {

    Map<String, String> getConfigFileContentByFilenameMap(Session session, String jcrRootPath) throws Exception;

    Map<String, String> getConfigFileContentByFilenameMap(Archive archive) throws Exception;

}
