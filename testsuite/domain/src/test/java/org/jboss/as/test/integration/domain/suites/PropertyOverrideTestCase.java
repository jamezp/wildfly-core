/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.suites;

import java.io.IOException;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport.Configuration;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyOverrideTestCase {

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;

    @BeforeClass
    public static void setupDomain() {
        testSupport = DomainTestSupport.createAndStartSupport(Configuration.create(PropertyOverrideTestCase.class.getName(),
                "domain-configs/domain-standard.xml", "host-configs/host-system-property-overrides.xml", null));

        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
    }

    @AfterClass
    public static void tearDownDomain() {
        DomainTestSuite.stopSupport();
        testSupport = null;
        domainMasterLifecycleUtil = null;
    }

    @Test
    public void test() throws IOException {
        final DomainClient domainClient = domainMasterLifecycleUtil.getDomainClient();
        final ModelNode domainOp = Operations.createReadResourceOperation(Operations.createAddress("host", "master", "server-config", "main-one", "system-property"), true);

        ModelNode response = domainClient.execute(domainOp);
        System.out.println(response);
    }
}
