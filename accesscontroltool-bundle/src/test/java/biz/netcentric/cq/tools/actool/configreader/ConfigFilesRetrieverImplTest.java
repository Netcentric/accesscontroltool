/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import biz.netcentric.cq.tools.actool.slingsettings.ExtendedSlingSettingsServiceImpl;

public class ConfigFilesRetrieverImplTest {

    private ExtendedSlingSettingsServiceImpl slingSettings;

    @Test
    public void testExtractRunModeSpecFromName() {
        assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName(""), Matchers.equalTo(""));
        assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("namewithoutrunmodes"), Matchers.equalTo(""));
        assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("name.runmode1"), Matchers.equalTo("runmode1"));
        assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("name.runmode1.runmode2"), Matchers.equalTo("runmode1.runmode2"));
        // this specifies an empty run mode
        assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("namewithoutrunmodes."), Matchers.equalTo(""));
    }

    @Test
    public void testIsRelevantConfiguration() throws Exception {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        slingSettings = new ExtendedSlingSettingsServiceImpl(currentRunmodes);
        Collection<String> configFilePatterns = Collections.emptyList();
        assertFalse(
                (ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry(""), "fragments", slingSettings, configFilePatterns)));
        assertFalse(
                (ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test"), "fragments", slingSettings, configFilePatterns)));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments", slingSettings,
                configFilePatterns)));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.publish", slingSettings,
                configFilePatterns)));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.author", slingSettings,
                configFilePatterns)));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.samplecontent",
                slingSettings, configFilePatterns)));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yam"), "fragments.samplecontent",
                slingSettings, configFilePatterns)));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.samplecontent.publish",
                slingSettings, configFilePatterns)));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.foo.publish",
                slingSettings, configFilePatterns)));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.samplecontent.local",
                slingSettings, configFilePatterns)));

    }

    @Test
    public void testIsRelevantConfigurationWithOrCombinations() throws Exception {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));
        slingSettings = new ExtendedSlingSettingsServiceImpl(currentRunmodes);
        Collection<String> configFilePatterns = Collections.emptyList();
        // testing 'or' combinations with
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.dev,local",
                slingSettings, configFilePatterns)));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.int,prod",
                slingSettings, configFilePatterns)));

        // combined 'and' and 'or'
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.author.dev,author.local",
                slingSettings, configFilePatterns)));
        assertFalse(
                (ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("test.yaml"), "fragments.publish.dev,publish.local",
                        slingSettings, configFilePatterns)));
    }

    @Test
    public void testIsRelevantConfigurationsFiltered() throws Exception {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("author"));
        slingSettings = new ExtendedSlingSettingsServiceImpl(currentRunmodes);
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("/conf", "file.yaml"), "config.author",
                slingSettings, ImmutableList.<String> of())));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("/conf", "file.yaml"), "config.author",
                slingSettings, ImmutableList.<String> of("/noMatch", "/conf/.*"))));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("/conf", "file.yaml"), "config.author",
                slingSettings, ImmutableList.<String> of("/conf/test.*.yaml"))));
        assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("/conf", "file.yaml"), "config.author",
                slingSettings, ImmutableList.<String> of("/conf/.*\\.yaml", "/noMatch"))));
        assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration(new StubEntry("/conf", "file.yaml"), "config.author",
                slingSettings, ImmutableList.<String> of("/nonconf.*"))));

    }

    static class StubEntry implements ConfigFilesRetrieverImpl.PackageEntryOrNode {

        private final String parentPath;
        private final String name;

        StubEntry(String name) {
            this.name = name;
            this.parentPath = "/";
        }

        StubEntry(String parentPath, String name) {
            this.parentPath = parentPath;
            this.name = name;
        }

        @Override
        public String getName() throws Exception {
            return name;
        }

        @Override
        public String getPath() throws Exception {
            return parentPath + "/" + name;
        }

        @Override
        public List<ConfigFilesRetrieverImpl.PackageEntryOrNode> getChildren() throws Exception {
            return Collections.emptyList();
        }

        @Override
        public boolean isDirectory() throws Exception {
            return false;
        }

        @Override
        public String getContentAsString() throws Exception {
            throw new UnsupportedOperationException("not implemented for testing");
        }
    }

}
