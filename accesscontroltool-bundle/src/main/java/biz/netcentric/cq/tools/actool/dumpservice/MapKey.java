package biz.netcentric.cq.tools.actool.dumpservice;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class MapKey implements StructuralDumpElement {

    public static final String YAML_MAP_KEY_PREFIX = "- ";
    public static final String YAML_MAP_KEY_SUFFIX = ":";

    private String key;
    private String comment;

    public MapKey(final String key, String comment) {
        this.key = key;
        this.comment = comment;
    }
    public MapKey(final String key) {
        this.key = key;
    }
    @Override
    public String getString() {
        return this.key;
    }

    @Override
    public int getLevel() {
        return 2;
    }

    @Override
    public String getComment() {
        return comment;
    }

}
