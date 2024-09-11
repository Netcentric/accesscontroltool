package biz.netcentric.cq.tools.actool.helper.runtime;

/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2024 Cognizant Netcentric
 * %%
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * #L%
 */

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeHelperTest {

    @Mock
    Session session;

    @Test
    void shouldBeReadonlyIfRootWritable() throws RepositoryException {
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(true);
        Assertions.assertTrue(RuntimeHelper.isAppsReadOnly(session));
    }

    @Test
    void shouldNotBeReadonlyIfRootNotWritable() throws RepositoryException {
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(false);
        Assertions.assertFalse(RuntimeHelper.isAppsReadOnly(session));
    }
}