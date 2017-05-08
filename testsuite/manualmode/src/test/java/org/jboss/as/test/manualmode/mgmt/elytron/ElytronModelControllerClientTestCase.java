/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.manualmode.mgmt.elytron;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;

import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests that the {@link ManagementClient} will connect to a server configured for an Elytron realm.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ElytronModelControllerClientTestCase {

    private static final URL WILDFLY_CONFIG = ElytronModelControllerClientTestCase.class.getClassLoader().getResource("test-wildfly-config.xml");

    @Inject
    private static ServerController CONTROLLER;

    @After
    public void tearDown() throws Exception {
        if (CONTROLLER.isStarted()) {
            CONTROLLER.stop();
        }
    }

    @Test
    public void testElytronAdminConfig() throws Exception {
        Assert.assertNotNull("Could not find test-wildfly-config.xml", WILDFLY_CONFIG);
        final Path copiedConfig = configureElytron();
        // Start the server
        CONTROLLER.start(copiedConfig.getFileName().toString(), WILDFLY_CONFIG.toURI());

        testConnection();

        // Stop the container, then remove the copied config
        CONTROLLER.stop();
        Files.deleteIfExists(copiedConfig);
    }

    @Test
    public void testDefaultClient() throws Exception {
        // Start the server
        CONTROLLER.start();

        testConnection();

        // Stop the container
        CONTROLLER.stop();
    }

    private void testConnection() throws IOException {

        final ModelNode op = Operations.createReadAttributeOperation(new ModelNode().setEmptyList(), "server-state");
        ModelNode result = CONTROLLER.getClient().getControllerClient().execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            Assert.fail(Operations.getFailureDescription(result).asString());
        }
    }

    private Path configureElytron() throws Exception {
        final String jbossHome = TestSuiteEnvironment.getJBossHome();
        Assert.assertNotNull("Could not find the JBoss home directory", jbossHome);

        final Path configPath = Paths.get(jbossHome, "standalone", "configuration");

        // We copy the config here as we don't know what the default value of the http-upgrade.sasl-authentication-factory
        // attribute on the /core-service=management/management-interface=http-interface resource is.
        final String config = "test-standalone-elytron.xml";
        final Path configFile = configPath.resolve(config);
        Files.copy(configPath.resolve("standalone.xml"), configFile, StandardCopyOption.REPLACE_EXISTING);

        try (CLIWrapper cli = new CLIWrapper(false)) {
            cli.sendLine("embed-server --server-config=" + config + " --jboss-home=" + jbossHome);
            cli.sendLine("/subsystem=elytron/filesystem-realm=testRealm:add(path=fs-realm-users,relative-to=jboss.server.config.dir)");
            cli.sendLine("/subsystem=elytron/filesystem-realm=testRealm/identity=test-admin:add()");
            cli.sendLine("/subsystem=elytron/filesystem-realm=testRealm/identity=test-admin:set-password(clear={password=\"admin.12345\"})");
            cli.sendLine("/subsystem=elytron/security-domain=testSecurityDomain:add(realms=[{realm=testRealm}],default-realm=testRealm,permission-mapper=default-permission-mapper)");
            cli.sendLine("/subsystem=elytron/sasl-authentication-factory=test-sasl-auth:add(sasl-server-factory=configured, security-domain=testSecurityDomain, mechanism-configurations=[{mechanism-name=DIGEST-MD5, mechanism-realm-configurations=[{realm-name=testRealm}]}])");
            cli.sendLine("/core-service=management/management-interface=http-interface:write-attribute(name=http-upgrade.sasl-authentication-factory, value=test-sasl-auth)");
            cli.sendLine("stop-embedded-server");
        }
        return configFile;
    }
}
