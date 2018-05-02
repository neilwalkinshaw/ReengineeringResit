/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ki.mgt;

import org.junit.After;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import org.apache.ki.SecurityUtils;
import org.apache.ki.authc.AuthenticationToken;
import org.apache.ki.authc.UsernamePasswordToken;
import org.apache.ki.realm.text.PropertiesRealm;
import org.apache.ki.subject.Subject;
import org.apache.ki.util.ThreadContext;


/**
 * @author Les Hazlewood
 * @since May 8, 2008 12:26:23 AM
 */
public class VMSingletonDefaultSecurityManagerTest {

    @Before
    public void setUp() {
        ThreadContext.clear();
    }

    @After
    public void tearDown() {
        ThreadContext.clear();
    }

    @Test
    public void testVMSingleton() {
        DefaultSecurityManager sm = new DefaultSecurityManager();
        sm.setRealm(new PropertiesRealm());
        SecurityUtils.setSecurityManager(sm);

        Subject subject = SecurityUtils.getSubject();

        AuthenticationToken token = new UsernamePasswordToken("guest", "guest");
        subject.login(token);
        subject.getSession().setAttribute("key", "value");
        assertTrue(subject.getSession().getAttribute("key").equals("value"));

        subject = SecurityUtils.getSubject();

        assertTrue(subject.isAuthenticated());
        assertTrue(subject.getSession().getAttribute("key").equals("value"));

        sm.destroy();
    }
}
