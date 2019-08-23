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

package org.jboss.as.test.manualmode.logging;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.json.JsonObject;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.LoggingUtils.JsonLogFileSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * This test ensures that log4j appenders weren't replaced and still log correctly after the server has been restarted.
 * Confirms the logging.properties file was wrote log4j appenders properly as well. See WFLY-1379 for further details.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class Slf4jTestCase extends AbstractLoggingTestCase {

    private final LoggingSetupTask setupTask = new LoggingSetupTask();

    @Before
    public void startContainer() throws Exception {
        // Start the server
        container.start();

        Assert.assertTrue("Container is not started", container.isStarted());
        setupTask.setup(container.getClient());

        // Deploy the servlet
        final JavaArchive deployment = createDeployment(Slf4jServiceActivator.class, join(Slf4jServiceActivator.DEPENDENCIES, org.slf4j.bridge.SLF4JBridgeHandler.class));
        deployment.addPackage(org.slf4j.Logger.class.getPackage());
        deployment.addPackage(Logger.class.getPackage());
        deploy(deployment, DEPLOYMENT_NAME);
    }

    @After
    public void stopContainer() throws Exception {
        setupTask.tearDown(container.getClient());
        // Remove the servlet
        undeploy();

        // Stop the container
        container.stop();
    }

    @Test
    public void testLog() throws Exception {
        // Write the message to the server
        final String msg = "Logging test: Slf4jHandlerTestCase.testLog";
        searchLog(msg, true);
    }

    private void searchLog(final String msg, final boolean expected) throws Exception {
        final int statusCode = getResponse(msg, Collections.singletonMap("activate", "true"));
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);
        final List<JsonObject> logLines = setupTask.getLogLines(container.getClient().getControllerClient());
        boolean logFound = false;
        for (JsonObject json : logLines) {
            if (json.getString("message").equals(msg)) {
                logFound = true;
                break;
            }
        }
        Assert.assertTrue(String.format("Did not find log message \"%s\" in the log file.", msg), logFound == expected);
    }

    private static class LoggingSetupTask extends JsonLogFileSetupTask {
        @Override
        protected Collection<ModelNode> additionalSetupOps() {
            return Collections.singleton(Operations.createWriteAttributeOperation(createAddress(), "add-logging-api-dependencies", false));
        }

        @Override
        protected Collection<ModelNode> additionalTearDownOps() {
            return Collections.singleton(Operations.createWriteAttributeOperation(createAddress(), "add-logging-api-dependencies", true));
        }
    }
}
