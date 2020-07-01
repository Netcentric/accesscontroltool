/*
 * (C) Copyright 2017 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.configreader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import biz.netcentric.cq.tools.actool.slingsettings.ExtendedSlingSettingsServiceImpl;

public class ConfigFilesRetrieverImplTest {

    private ExtendedSlingSettingsServiceImpl slingSettings;

    @Test
    public void testExtractRunModeSpecFromName() {
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName(""), Matchers.equalTo(""));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("namewithoutrunmodes"), Matchers.equalTo(""));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("name.runmode1"), Matchers.equalTo("runmode1"));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("name.runmode1.runmode2"), Matchers.equalTo("runmode1.runmode2"));
        // this specifies an empty run mode
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModeSpecFromName("namewithoutrunmodes."), Matchers.equalTo(""));
    }

    @Test
    public void testIsRelevantConfiguration() {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        slingSettings = new ExtendedSlingSettingsServiceImpl(currentRunmodes);
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("", "fragments", slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test", "fragments", slingSettings)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments", slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish", slingSettings)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author", slingSettings)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent", slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yam", "fragments.samplecontent", slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.publish",
                slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.foo.publish", slingSettings)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.local", slingSettings)));

    }

    @Test
    public void testIsRelevantConfigurationWithOrCombinations() {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));
        slingSettings = new ExtendedSlingSettingsServiceImpl(currentRunmodes);
        // testing 'or' combinations with
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.dev,local", slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.int,prod", slingSettings)));

        // combined 'and' and 'or'
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author.dev,author.local",
                slingSettings)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish.dev,publish.local",
                slingSettings)));
    }

}
