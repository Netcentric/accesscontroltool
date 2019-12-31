package biz.netcentric.cq.tools.actool.ui;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import biz.netcentric.cq.tools.actool.api.AcInstallationService;
import biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl;

public class RequestParameters {

    static RequestParameters fromRequest(HttpServletRequest req, AcInstallationService acInstallationService) {
        String configRootPath = getParam(req, AcToolUiService.PARAM_CONFIGURATION_ROOT_PATH,
                ((AcInstallationServiceImpl) acInstallationService).getConfiguredAcConfigurationRootPath());
        String basePathsParam = req.getParameter(AcToolUiService.PARAM_BASE_PATHS);
        RequestParameters result = new RequestParameters(
                configRootPath,
                StringUtils.isNotBlank(basePathsParam) ? Arrays.asList(basePathsParam.split(" *, *")) : null,
                Integer.parseInt(getParam(req, AcToolUiService.PARAM_SHOW_LOG_NO, "0")),
                Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_SHOW_LOG_VERBOSE)),
                Boolean.valueOf(req.getParameter(AcToolUiService.PARAM_APPLY_ONLY_IF_CHANGED)));
        return result;
    }

    final String configurationRootPath;
    final List<String> basePaths;
    final int showLogNo;
    final boolean showLogVerbose;
    final boolean applyOnlyIfChanged;

    public RequestParameters(String configurationRootPath, List<String> basePaths, int showLogNo, boolean showLogVerbose,
            boolean applyOnlyIfChanged) {
        super();
        this.configurationRootPath = configurationRootPath;
        this.basePaths = basePaths;
        this.showLogNo = showLogNo;
        this.showLogVerbose = showLogVerbose;
        this.applyOnlyIfChanged = applyOnlyIfChanged;
    }

    public String[] getBasePathsArr() {
        if (basePaths == null) {
            return null;
        } else {
            return basePaths.toArray(new String[basePaths.size()]);
        }
    }

    static String getParam(final HttpServletRequest req, final String name, final String defaultValue) {
        String result = req.getParameter(name);
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

}