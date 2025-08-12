package biz.netcentric.cq.tools.actool.api;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Builder for {@link InstallationOptions}.
 * 
 * @since 3.6.0 */
public final class InstallationOptionsBuilder {

    private String configurationRootPath;
    private List<String> restrictedToPaths;
    private boolean skipIfConfigUnchanged;
    private boolean updateExistingExternalGroups;

    /**
     * Creates a new builder with the given properties previously returned by {@link InstallationOptions#getPersistableProperties()}.
     * @param properties the properties returned by {@link InstallationOptions#getPersistableProperties()}
     */
    public InstallationOptionsBuilder(Map<String, Object> properties) {
        this.configurationRootPath = (String)properties.get("configurationRootPath");
        String[] restrictedToPathsArray = (String[])properties.get("restrictedToPaths");
        if (restrictedToPathsArray != null) {
            this.restrictedToPaths = new LinkedList<>(Arrays.asList(restrictedToPathsArray));
        } else {
            this.restrictedToPaths = new LinkedList<>();
        }
        this.skipIfConfigUnchanged = (Boolean)properties.getOrDefault("skipIfConfigUnchanged", Boolean.FALSE);
        this.updateExistingExternalGroups = (Boolean)properties.getOrDefault("updateExistingExternalGroups", Boolean.FALSE);
    }
    
    public InstallationOptionsBuilder() {
        this.configurationRootPath = null;
        this.restrictedToPaths = new LinkedList<>();
        this.skipIfConfigUnchanged = false;
        this.updateExistingExternalGroups = false;
    }

    public InstallationOptionsBuilder(InstallationOptions options) {
        this.configurationRootPath = options.getConfigurationRootPath().orElse(null);
        this.restrictedToPaths = new LinkedList<>();
        this.restrictedToPaths.addAll(options.getRestrictedToPaths());
        this.skipIfConfigUnchanged = options.shouldSkipIfConfigUnchanged();
        this.updateExistingExternalGroups = options.shouldUpdateExistingExternalGroups();
    }

    public InstallationOptionsBuilder withConfigurationRootPath(String configurationRootPath) {
        this.configurationRootPath = configurationRootPath;
        return this;
    }

    public InstallationOptionsBuilder withRestrictedToPaths(String... restrictedToPaths) {
        this.restrictedToPaths.addAll(Arrays.asList(restrictedToPaths));
        return this;
    }

    public InstallationOptionsBuilder withRestrictedToPaths(Collection<String> restrictedToPaths) {
        this.restrictedToPaths.addAll(restrictedToPaths);
        return this;
    }

    public InstallationOptionsBuilder skipIfConfigUnchanged() {
        this.skipIfConfigUnchanged = true;
        return this;
    }

    public InstallationOptionsBuilder updateExistingExternalGroups() {
        this.updateExistingExternalGroups = true;
        return this;
    }

    public InstallationOptions build() {
        return new InstallationOptionsImpl(this);
    }

    private static final class InstallationOptionsImpl implements InstallationOptions {
        private final String configurationRootPath;
        private final List<String> restrictedToPaths;
        private final boolean skipIfConfigUnchanged;
        private final boolean updateExistingExternalGroups;

       InstallationOptionsImpl(InstallationOptionsBuilder builder) {
            this.configurationRootPath = builder.configurationRootPath;
            this.restrictedToPaths = builder.restrictedToPaths;
            this.skipIfConfigUnchanged = builder.skipIfConfigUnchanged;
            this.updateExistingExternalGroups = builder.updateExistingExternalGroups;
        }

        @Override
        public Optional<String> getConfigurationRootPath() {
            return Optional.ofNullable(configurationRootPath);
        }

        @Override
        public List<String> getRestrictedToPaths() {
            return restrictedToPaths;
        }

        @Override
        public boolean shouldSkipIfConfigUnchanged() {
            return skipIfConfigUnchanged;
        }

        @Override
        public boolean shouldUpdateExistingExternalGroups() {
            return updateExistingExternalGroups;
        }

        @Override
        public int hashCode() {
            return Objects.hash(configurationRootPath, restrictedToPaths, skipIfConfigUnchanged, updateExistingExternalGroups);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            InstallationOptionsImpl other = (InstallationOptionsImpl) obj;
            return Objects.equals(configurationRootPath, other.configurationRootPath)
                    && Objects.equals(restrictedToPaths, other.restrictedToPaths) && skipIfConfigUnchanged == other.skipIfConfigUnchanged
                    && updateExistingExternalGroups == other.updateExistingExternalGroups;
        }

        @Override
        public String toString() {
            return "InstallationOptionsImpl [configurationRootPath=" + configurationRootPath + ", restrictedToPaths=" + restrictedToPaths
                    + ", skipIfConfigUnchanged=" + skipIfConfigUnchanged + ", updateExistingExternalGroups=" + updateExistingExternalGroups
                    + "]";
        }

        @Override
        public Map<String, Object> getPersistableProperties() {
            Map<String, Object> properties = new java.util.HashMap<>();
            properties.put("configurationRootPath", configurationRootPath);
            properties.put("restrictedToPaths", restrictedToPaths.toArray(new String[0]));
            properties.put("skipIfConfigUnchanged", skipIfConfigUnchanged);
            properties.put("updateExistingExternalGroups", updateExistingExternalGroups);
            return properties;
        }
    }
}
