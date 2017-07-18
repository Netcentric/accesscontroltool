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

public class ConfigFilesRetrieverImplTest {

    @Test
    public void testExtractSimpleRunModesFromName() {
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName(""), Matchers
                .hasSize(0));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("namewithoutrunmodes"), Matchers
                .hasSize(0));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name.runmode1"), Matchers
                .contains(Matchers.contains("runmode1")));
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name.runmode1.runmode2"), Matchers
                .contains(Matchers.containsInAnyOrder("runmode1", "runmode2")));
        // this specifies an empty run mode
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("namewithoutrunmodes."), Matchers.contains(Matchers.contains("")));
    }

    @Test
    public void testExtractRunModesFromNameWithAndAndOr() {
        Assert.assertThat(ConfigFilesRetrieverImpl
                .extractRunModesFromName("name.runmode1,runmode2"), Matchers
                .containsInAnyOrder(Matchers.contains("runmode1"), Matchers.contains("runmode2")));
        Assert.assertThat(
                ConfigFilesRetrieverImpl
                        .extractRunModesFromName("name.runmode1.runmode1a,runmode2.runmode2a"),
                Matchers
                        .containsInAnyOrder(Matchers.containsInAnyOrder("runmode1", "runmode1a"),
                                Matchers.containsInAnyOrder("runmode2", "runmode2a")));
    }

    @Test
    public void testIsRelevantConfiguration() {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("", "fragments", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test", "fragments", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yam", "fragments.samplecontent", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.publish",
                currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.foo.publish", currentRunmodes)));
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.samplecontent.local", currentRunmodes)));

    }

    @Test
    public void testIsRelevantConfigurationWithOrCombinations() {
        Set<String> currentRunmodes = new HashSet<String>(
                Arrays.asList("samplecontent", "author", "netcentric", "crx3tar", "crx2", "local"));

        // testing 'or' combinations with
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.dev,local", currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.int,prod", currentRunmodes)));

        // combined 'and' and 'or'
        Assert.assertTrue((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.author.dev,author.local",
                currentRunmodes)));
        Assert.assertFalse((ConfigFilesRetrieverImpl.isRelevantConfiguration("test.yaml", "fragments.publish.dev,publish.local",
                currentRunmodes)));
    }

}
