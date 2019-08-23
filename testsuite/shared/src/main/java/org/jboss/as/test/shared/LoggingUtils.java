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

package org.jboss.as.test.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingUtils {

    public static class JsonLogFileSetupTask implements ServerSetupTask {
        public static final String DEFAULT_FILE_NAME = "json-file.log";

        private final Deque<ModelNode> tearDownOps = new ArrayDeque<>();

        @Override
        public void setup(final ManagementClient managementClient) throws Exception {
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

            // Add a JSON formatter
            final ModelNode formatterAddress = Operations.createAddress("subsystem", "logging", "json-formatter", getFormatterName());
            ModelNode op = Operations.createAddOperation(formatterAddress);
            op.get("pretty-print").set(false);
            op.get("exception-output-type").set("detailed-and-formatted");
            builder.addStep(op);
            tearDownOps.addLast(Operations.createRemoveOperation(formatterAddress));

            // Create the file handler
            final ModelNode handlerAddress = Operations.createAddress("subsystem", "logging", "file-handler", getHandlerName());
            op = Operations.createAddOperation(handlerAddress);
            final ModelNode file = op.get("file").setEmptyObject();
            file.get("path").set(getFileName());
            file.get("relative-to").set("jboss.server.log.dir");
            op.get("autoflush").set(true);
            op.get("append").set(false);
            op.get("named-formatter").set(getFormatterName());
            builder.addStep(op);
            tearDownOps.addFirst(Operations.createRemoveOperation(handlerAddress));

            // If the logger is null it's the root logger and we should just add the handler
            final String loggerName = getLoggerName();
            if (loggerName == null) {
                final ModelNode loggerAddress = Operations.createAddress("subsystem", "logging", "root-logger", "ROOT");
                op = Operations.createOperation("add-handler", loggerAddress);
                op.get("name").set(getHandlerName());
                builder.addStep(op);
                op = Operations.createOperation("remove-handler", loggerAddress);
                op.get("name").set(getHandlerName());
                tearDownOps.addFirst(op);
            } else {
                // We'll assume the logger needs to be added
                final ModelNode loggerAddress = Operations.createAddress("subsystem", "logging", "logger", loggerName);
                op = Operations.createAddOperation(loggerAddress);
                final ModelNode handlers = op.get("handlers").setEmptyList();
                handlers.add(getHandlerName());
                op.get("level").set(getLevel());
                builder.addStep(op);

                tearDownOps.addFirst(Operations.createRemoveOperation(loggerAddress));
            }

            for (ModelNode additionalOp : additionalSetupOps()) {
                builder.addStep(additionalOp);
            }

            tearDownOps.addAll(additionalTearDownOps());

            execute(managementClient.getControllerClient(), builder.build());
            ServerReload.reloadIfRequired(managementClient.getControllerClient());
        }

        @Override
        public void tearDown(final ManagementClient managementClient) throws Exception {
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
            ModelNode op;
            while ((op = tearDownOps.pollFirst()) != null) {
                builder.addStep(op);
            }

            execute(managementClient.getControllerClient(), builder.build());
            ServerReload.reloadIfRequired(managementClient.getControllerClient());
            // Delete the file
            final Path logFile = resolveStandaloneLogDirectory(managementClient.getControllerClient()).resolve(getFileName());
            Files.deleteIfExists(logFile);
        }

        protected String getHandlerName() {
            return "json-file";
        }

        protected String getFormatterName() {
            return "json";
        }

        protected String getFileName() {
            return DEFAULT_FILE_NAME;
        }

        protected String getLoggerName() {
            return null;
        }

        protected String getLevel() {
            return "INFO";
        }

        protected Collection<ModelNode> additionalSetupOps() {
            return Collections.emptyList();
        }

        protected Collection<ModelNode> additionalTearDownOps() {
            return Collections.emptyList();
        }

        public List<JsonObject> getLogLines(final ModelControllerClient client) throws IOException {
            return readJsonLogFile(client, getFileName());
        }

        public List<JsonObject> getLogLines(final ModelControllerClient client, final int offset) throws IOException {
            return readJsonLogFile(client, getFileName(), offset);
        }

    }

    public static Path resolveStandaloneLogDirectory(final ModelControllerClient client) throws IOException {
        final ModelNode address = Operations.createAddress("path", "jboss.server.log.dir");
        final ModelNode result = execute(client, Operations.createOperation("path-info", address));
        final Path logDir = Paths.get(result.get("path", "resolved-path").asString());
        if (Files.notExists(logDir)) {
            throw new RuntimeException("The log directory does not exist: " + logDir);
        }
        return logDir;
    }

    public static List<JsonObject> readJsonLogFile(final ModelControllerClient client, final String logFileName) throws IOException {
        return readJsonLogFile(client, logFileName, -1);
    }

    public static List<JsonObject> readJsonLogFile(final ModelControllerClient client, final String logFileName,
                                                   final int offset) throws IOException {
        final List<JsonObject> lines = new ArrayList<>();
        try (BufferedReader reader = readLogFile(client, logFileName)) {
            int lineCount = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineCount++ > offset) {
                    try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                        lines.add(jsonReader.readObject());
                    }
                }
            }
        }
        return lines;
    }

    public static BufferedReader readLogFile(final ModelControllerClient client, final String logFileName) throws IOException {
        final ModelNode address = Operations.createAddress("subsystem", "logging", "log-file", logFileName);
        final ModelNode op = Operations.createReadAttributeOperation(address, "stream");
        final OperationResponse response = client.executeOperation(Operation.Factory.create(op), OperationMessageHandler.logging);
        final ModelNode result = response.getResponseNode();
        if (Operations.isSuccessfulOutcome(result)) {
            final OperationResponse.StreamEntry entry = response.getInputStream(Operations.readResult(result).asString());
            if (entry == null) {
                throw new RuntimeException(String.format("Failed to find entry with UUID %s for log file %s",
                        Operations.readResult(result).asString(), logFileName));
            }
            return new BufferedReader(new InputStreamReader(entry.getStream(), StandardCharsets.UTF_8));
        }
        throw new RuntimeException(String.format("Failed to read log file %s: %s", logFileName, Operations.getFailureDescription(result).asString()));
    }

    private static ModelNode execute(final ModelControllerClient client, final ModelNode op) throws IOException {
        return execute(client, Operation.Factory.create(op));
    }

    private static ModelNode execute(final ModelControllerClient client, final Operation op) throws IOException {
        final ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            return Operations.readResult(result);
        }
        throw new RuntimeException(String.format("Failed to execute op: %s%n%s", op,
                Operations.getFailureDescription(result).asString()));
    }
}
