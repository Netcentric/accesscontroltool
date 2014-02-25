package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import biz.netcentric.cq.tools.actool.helper.AceBean;

public class AcePathComparator implements Comparator<AceBean>{

	@Override
	public int compare(AceBean ace1, AceBean ace2) {
		if(ace1.getJcrPath().compareTo(ace2.getJcrPath()) > 1){
			return 1;
		}else  if(ace1.getJcrPath().compareTo(ace2.getJcrPath()) < 1){
			return -1;
		}
		else if(ace1.getJcrPath().compareTo(ace2.getJcrPath()) == 0){
			return 1;
		}
		return 1;
	}

}
