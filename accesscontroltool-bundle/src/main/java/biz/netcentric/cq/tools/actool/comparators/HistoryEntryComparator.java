/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import biz.netcentric.cq.tools.actool.api.HistoryEntry;

public class HistoryEntryComparator implements Comparator<HistoryEntry> {

    @Override
    public int compare(HistoryEntry entry1, HistoryEntry entry2) {
        if (entry1.getIndex() > (entry2.getIndex())) {
            return 1;
        }

        return -1;
    }

}
