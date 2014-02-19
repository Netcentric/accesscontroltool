package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import biz.netcentric.cq.tools.actool.helper.AceBean;

public class AcePathComparator implements Comparator<AceBean>{

	@Override
	public int compare(AceBean ace1, AceBean ace2) {
		return(ace1.getJcrPath().compareTo(ace2.getJcrPath()));
	}

}
