/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

public class DumpSectionElement implements StructuralDumpElement {

    private String value;
    public static final String YAML_DUMP_SECTION_PREFIX = "- ";

    public DumpSectionElement(final String value) {
        this.value = value;
    }

    public String getString() {
        return this.value;
    }

    @Override
    public int getLevel() {
        return 0;
    }
}
