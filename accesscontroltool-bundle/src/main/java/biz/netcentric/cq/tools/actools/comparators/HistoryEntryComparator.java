package biz.netcentric.cq.tools.actools.comparators;

import java.util.Comparator;

import biz.netcentric.cq.tools.actool.installationhistory.HistoryEntry;

public class HistoryEntryComparator implements Comparator<HistoryEntry>{

	@Override
	public int compare(HistoryEntry entry1, HistoryEntry entry2) {
		if(entry1.getIndex() > (entry2.getIndex())){
			return 1;
		}
		
		return -1;
	}

}
