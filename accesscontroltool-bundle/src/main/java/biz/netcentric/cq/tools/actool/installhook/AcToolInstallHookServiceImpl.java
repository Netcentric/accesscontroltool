package biz.netcentric.cq.tools.actool.installhook;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.jcr.Session;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.settings.SlingSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import biz.netcentric.cq.tools.actool.aceservice.AceService;
import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableInstallationHistory;
import biz.netcentric.cq.tools.actool.installationhistory.AcInstallationHistoryPojo;

import com.day.jcr.vault.fs.io.Archive;
import com.day.jcr.vault.fs.io.Archive.Entry;

@Component
@Service(value = AcToolInstallHookService.class)
public class AcToolInstallHookServiceImpl implements AcToolInstallHookService {

	private static final Logger LOG = LoggerFactory
			.getLogger(AcToolInstallHookServiceImpl.class);

	@Reference
	private AceService aceService;

	@Reference
	private SlingSettingsService slingSettingsService;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * biz.netcentric.cq.tools.actool.installhook.AcToolInstallHoookService#
	 * installYamlFilesFromPackage(com.day.jcr.vault.fs.io.Archive,
	 * javax.jcr.Session)
	 */
	@Override
	public void installYamlFilesFromPackage(Archive archive, Session session)
			throws Exception {
		AcInstallationHistoryPojo history = new AcInstallationHistoryPojo();
		Set<AuthorizableInstallationHistory> authorizableInstallationHistorySet = new LinkedHashSet<AuthorizableInstallationHistory>();

		try {
			Entry rootEntry = archive.getJcrRoot();
			if (rootEntry == null) {
				throw new IllegalStateException(
						"Invalid package: It does not contain a JCR root element");
			}
			Map<String, String> configs = getConfigurations(archive,
					archive.getJcrRoot(), session);
			aceService.installNewConfigurations(session, history, configs,
					authorizableInstallationHistorySet);
		} catch (Exception e) {
			history.addError(e.toString());
			throw e;
		} finally {
			// TODO: acHistoryService.persistHistory(history,
			// this.configurationPath);
		}
	}

	private Map<String, String> getConfigurations(Archive archive,
			Entry parent, Session session) throws IOException {
		Map<String, String> configs = new HashMap<String, String>();
		// Read the configuration files from the archive
		for (Entry entry : parent.getChildren()) {
			if (entry.isDirectory()) {
				configs.putAll(getConfigurations(archive, entry, session));
			} else {
				Set<String> currentRunModes = slingSettingsService.getRunModes();
				if (isRelevantConfiguration(entry.getName(), parent.getName(), currentRunModes)) {
					LOG.info("Reading YAML file {}", entry.getName());
					InputStream input = archive.getInputSource(entry)
							.getByteStream();
					if (input == null) {
						throw new IllegalStateException(
								"Could not get input stream from entry "
										+ entry.getName());
					}
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(input, StandardCharsets.UTF_8))) {
						StringBuilder sb = new StringBuilder();
						String line;
						while ((line = reader.readLine()) != null) {
							sb.append(line);
							sb.append(System.lineSeparator());
						}

						// We cannot use the entry's name, since it might not be
						// unique, and we don't have its full path
						// so we add its hash code as a key for the map of
						// configs.
						configs.put(parent.getName() + "/" + entry.getName()
								+ " (" + entry.hashCode() + ")", sb.toString());
					}
				}
			}
		}
		return configs;
	}

	 static boolean isRelevantConfiguration(final String entryName, final String parentName, final Set<String> currentRunModes) {
		if (entryName.endsWith(".yaml")) {
			// extract runmode from parent name (if parent has "." in it)
			Set<String> requiredRunModes = extractRunModesFromName(parentName);
			if (requiredRunModes.isEmpty()) {
				LOG.debug(
						"Install file '{}', because parent name '{}' does not have a run mode specified.",
						entryName, parentName);
				return true;
			}

			// check if parent name has the right name
			for (String requiredRunMode : requiredRunModes) {
				if (!currentRunModes.contains(requiredRunMode)) {
					LOG.debug(
							"Do not install file '{}', because required run mode '{}' is not set.",
							entryName, requiredRunMode);
					return false;
				}
			}
			return true;
		}
		return false;
	}

	static Set<String> extractRunModesFromName(final String name) {
		Set<String> requiredRunModes = new HashSet<String>();

		// extract runmodes from name (separated by ".")
		int positionDot = name.indexOf(".");

		while (positionDot != -1) {
			// find next dot
			int positionPreviousDot = positionDot;
			if (positionPreviousDot + 1 >= name.length()) {
				// ignore dots at the end!
				return requiredRunModes;
			}
			positionDot = name.indexOf(".", positionPreviousDot + 1);
			final String runMode;
			if (positionDot == -1) {
				runMode = name.substring(positionPreviousDot + 1);
			} else {
				runMode = name.substring(positionPreviousDot + 1, positionDot);
			}
			if (!runMode.isEmpty()) {
				requiredRunModes.add(runMode);
			}
		}
		return requiredRunModes;
	}
}
