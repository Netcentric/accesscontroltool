package biz.netcentric.cq.tools.actool.configreader;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.service.cm.ConfigurationPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.env.EnvScalarConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import biz.netcentric.cq.tools.actool.history.InstallationLogger;

/** 
 * Resolves placeholders in scalars.
 * Similar to {@link EnvScalarConstructor}, but uses the interpolation provided by a {@link ConfigurationPlugin}.
 * 
 * Usually this is called with 
 * <a href="https://github.com/apache/felix-dev/tree/master/configadmin-plugins/interpolation">Felix Configadmin Interpolation Plugin</a>.
 * 
 * @see <a href="https://bitbucket.org/snakeyaml/snakeyaml/wiki/Variable%20substitution">Variable substitution</a>
 *
 */
public class YamlConfigurationAdminPluginScalarConstructor extends SafeConstructor {

    // this tag is not used explicitly in YAML but bound implicitly via Yaml.addImplicitResolver(...)
    public static final Tag TAG = new Tag("!CONFIGADMIN");
    private static final Logger LOG = LoggerFactory.getLogger(YamlConfigurationAdminPluginScalarConstructor.class);
    static final String KEY = "myKey";

    private final ConfigurationPlugin interpolationPlugin;
    private final InstallationLogger installLog;

    public YamlConfigurationAdminPluginScalarConstructor(InstallationLogger installLog, ConfigurationPlugin interpolationPlugin) {
        super(new LoaderOptions());
        this.yamlConstructors.put(TAG, new ConstructYamlConfigurationAdminPlugin());
        this.interpolationPlugin = interpolationPlugin;
        this.installLog = installLog;
    }

    private class ConstructYamlConfigurationAdminPlugin extends AbstractConstruct {
        public Object construct(Node node) {
            String value = constructScalar((ScalarNode) node);
            return resolvePlaceholder(value);
        }
    }

    private String resolvePlaceholder(String scalar) {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put(KEY, scalar);
        try {
            installLog.addMessage(LOG, "Using ConfigAdminPlugin to resolve '" + scalar + "'");
            // resolve arbitrarily many placeholders in the scalar
            interpolationPlugin.modifyConfiguration(null, dictionary);
            return (String)dictionary.get(KEY);
        } catch (Exception e) {
            throw new YAMLException("Could not resolve scalar: " + scalar + ": " + e.getMessage(), e);
        }
    }
}
