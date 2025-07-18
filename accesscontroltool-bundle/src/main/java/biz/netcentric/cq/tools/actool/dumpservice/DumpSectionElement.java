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

    @Override
    public String getComment() {
        return null;
    }
}
