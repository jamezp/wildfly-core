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

package org.wildfly.event.logger;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandardEventLoggerTestCase extends AbstractEventLoggerTestCase {
    private static final int LOG_COUNT = 10000;

    @Test
    public void testLogger() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-logger", writer);
        testLogger(logger, writer);
    }

    @Test
    public void testMultiLogger() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-multi-logger", writer);
        testMultiLogger(logger, writer);
    }

    private static void testMultiLogger(final EventLogger logger, final QueuedJsonWriter writer) throws Exception {
        final ExecutorService executor = createExecutor();
        try {
            final Map<Integer, TestValues> createValues = new HashMap<>();
            for (int i = 0; i < LOG_COUNT; i++) {
                final TestValues values = new TestValues()
                        .add("eventSource", logger.getEventSource(), JsonObject::getString)
                        .add("count", i, JsonObject::getInt);
                createValues.put(i, values);
                // Use a supplier for every 5th event
                final boolean useSupplier = (i % 5 == 0);
                executor.submit(() -> {
                    if (useSupplier) {
                        logger.log(values::asMap);
                    } else {
                        logger.log(values.asMap());
                    }
                });
            }

            for (int i = 0; i < LOG_COUNT; i++) {
                final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
                Assert.assertNotNull("Expected value written, but was null", jsonString);

                try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
                    final JsonObject jsonObject = reader.readObject();
                    final int count = jsonObject.getInt("count");
                    final TestValues values = createValues.remove(count);
                    Assert.assertNotNull("Failed to find value for entry " + count, values);
                    for (TestValue<?> testValue : values) {
                        testValue.compare(jsonObject);
                    }
                }
            }
            Assert.assertTrue("Values were created that were not logged: " + createValues, createValues.isEmpty());
        } finally {
            executor.shutdown();
            Assert.assertTrue(String.format("Executed did not complete within %d seconds", TIMEOUT),
                    executor.awaitTermination(TIMEOUT, TimeUnit.SECONDS));
        }
    }
}
