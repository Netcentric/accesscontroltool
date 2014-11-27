package biz.netcentric.cq.tools.actool.comparators;

import java.util.Comparator;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;

public class AuthorizableBeanIDComparator implements
        Comparator<AuthorizableConfigBean> {

    @Override
    public int compare(AuthorizableConfigBean bean1,
            AuthorizableConfigBean bean2) {
        if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) > 1) {
            return 1;
        } else if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) < 1) {
            return -1;
        } else if (bean1.getPrincipalID().compareTo(bean2.getPrincipalID()) == 0) {
            return 1;
        }
        return 1;
    }

}
