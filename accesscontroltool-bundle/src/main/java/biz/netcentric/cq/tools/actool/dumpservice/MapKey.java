/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

public class MapKey implements StructuralDumpElement {

    private String key;
    public static final String YAML_MAP_KEY_PREFIX = "- ";
    public static final String YAML_MAP_KEY_SUFFIX = ":";

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

}
