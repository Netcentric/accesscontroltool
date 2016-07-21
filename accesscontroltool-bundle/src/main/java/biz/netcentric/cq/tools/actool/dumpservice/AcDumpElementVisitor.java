/*
 * (C) Copyright 2015 Netcentric AG.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package biz.netcentric.cq.tools.actool.dumpservice;

import biz.netcentric.cq.tools.actool.authorizableutils.AuthorizableConfigBean;
import biz.netcentric.cq.tools.actool.configmodel.AceBean;

public interface AcDumpElementVisitor {

    void visit(AuthorizableConfigBean authorizableConfigBean);

    void visit(AceBean aceBean);

    void visit(CommentingDumpElement commentingDumpElement);

    void visit(StructuralDumpElement structuralDumpElement);
}
