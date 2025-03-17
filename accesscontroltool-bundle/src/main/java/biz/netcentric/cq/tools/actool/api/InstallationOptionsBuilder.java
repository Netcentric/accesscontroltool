package biz.netcentric.cq.tools.actool.api;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builder for {@link InstallationOptions}.
 * 
 * @since 3.6.0 */
public final class InstallationOptionsBuilder {

    private Optional<String> configurationRootPath;
    private List<String> restrictedToPaths;
    private boolean skipIfConfigUnchanged;
    private boolean updateExistingExternalGroups;

    public InstallationOptionsBuilder() {
        this.configurationRootPath = Optional.empty();
        this.restrictedToPaths = new LinkedList<>();
        this.skipIfConfigUnchanged = false;
        this.updateExistingExternalGroups = false;
    }

    public InstallationOptionsBuilder(InstallationOptions options) {
        this.configurationRootPath = options.getConfigurationRootPath();
        this.restrictedToPaths = new LinkedList<>();
        this.restrictedToPaths.addAll(options.getRestrictedToPaths());
        this.skipIfConfigUnchanged = options.shouldSkipIfConfigUnchanged();
        this.updateExistingExternalGroups = options.shouldUpdateExistingExternalGroups();
    }

    public InstallationOptionsBuilder withConfigurationRootPath(String configurationRootPath) {
        this.configurationRootPath = Optional.of(configurationRootPath);
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
        private final Optional<String> configurationRootPath;
        private final List<String> restrictedToPaths;
        private final boolean skipIfConfigUnchanged;
        private final boolean updateExistingExternalGroups;

        public InstallationOptionsImpl(InstallationOptionsBuilder builder) {
            this.configurationRootPath = builder.configurationRootPath;
            this.restrictedToPaths = builder.restrictedToPaths;
            this.skipIfConfigUnchanged = builder.skipIfConfigUnchanged;
            this.updateExistingExternalGroups = builder.updateExistingExternalGroups;
        }

        @Override
        public Optional<String> getConfigurationRootPath() {
            return configurationRootPath;
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
    }
}
