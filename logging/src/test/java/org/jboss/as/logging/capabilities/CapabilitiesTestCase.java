/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.capabilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.logging.AbstractLoggingSubsystemTest;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingTestEnvironment;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class CapabilitiesTestCase extends AbstractSubsystemTest {

    private static volatile KernelServices kernelServices = null;

    @Parameterized.Parameter
    public String profileName;

    public CapabilitiesTestCase() {
        super(LoggingExtension.SUBSYSTEM_NAME, new LoggingExtension(), AbstractLoggingSubsystemTest.RemoveOperationComparator.INSTANCE);
    }

    @Parameterized.Parameters
    public static List<Object> data() {
        return Arrays.asList(null, "test-profile");
    }

    @Before
    public void startContainer() throws Exception {
        // capabilities-subsystem.xml
        if (kernelServices == null) {
            synchronized (CapabilitiesTestCase.class) {
                kernelServices = createKernelServicesBuilder(LoggingTestEnvironment.getManagementInstance())
                        .setSubsystemXml(readResource("/capabilities-subsystem.xml"))
                        .build();
            }
        }
    }

    @AfterClass
    public static void stopContainer() {
        synchronized (CapabilitiesTestCase.class) {
            if (kernelServices != null) {
                kernelServices.shutdown();
                kernelServices = null;
            }
        }
    }

    @Test
    public void testProfile() throws Exception {
        // Create an async-handler, add it to the root-logger then attempt to remove it
        final ModelNode handlerAddress = createAddress("async-handler", "async");
        ModelNode op = Operations.createAddOperation(handlerAddress);
        op.get("queue-length").set(5L);
        executeOperation(op);

        final ModelNode loggerAddress = createAddress("root-logger", "ROOT");
        op = Operations.createOperation("add-handler", loggerAddress);
        op.get("name").set("async");
        executeOperation(op);

        // Attempt to remove the handler
        ModelNode result = kernelServices.executeOperation(Operations.createRemoveOperation(handlerAddress));
        System.out.printf("profile: %s - %s%n", profileName, result);
        Assert.assertFalse(Operations.isSuccessfulOutcome(result));
    }

    private ModelNode createAddress(final String... addressParts) {
        final Collection<String> parts = new ArrayList<>();
        parts.add("subsystem");
        parts.add("logging");
        if (profileName != null) {
            parts.add("logging-profile");
            parts.add(profileName);
        }
        Collections.addAll(parts, addressParts);
        return Operations.createAddress(parts);
    }

    private static ModelNode executeOperation(final ModelNode op) throws IOException {
        Assert.assertNotNull(kernelServices);
        final ModelNode result = kernelServices.executeOperation(op);
        if (Operations.isSuccessfulOutcome(result)) {
            return Operations.readResult(result);
        }
        Assert.fail(Operations.getFailureDescription(result).asString());
        return null;
    }
}
