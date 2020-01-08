/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.LoggerRouter;
import org.junit.Assert;

/**
 * A log router for testing with logging profiles. This is required because the
 * {@linkplain org.jboss.as.logging.logmanager.WildFlyLogContextSelector} is configured with a log router that expects
 * deployments to set up the log context for the profile.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class TestLoggingProfileLoggerRouter extends LoggerRouter implements AutoCloseable {

    private final LoggerRouter dft;
    private final AtomicReference<LogContext> currentLogContext = new AtomicReference<>();

    private TestLoggingProfileLoggerRouter(final LogContext logContext) {
        super(logContext);
        final LoggerRouter dft = Logger.getLoggerRouter();
        currentLogContext.set(logContext);
        if (dft instanceof TestLoggingProfileLoggerRouter) {
            this.dft = ((TestLoggingProfileLoggerRouter) dft).dft;
        } else {
            this.dft = dft;
        }
    }

    /**
     * Creates a new log router for the profile.
     *
     * @param profileName the profile name
     *
     * @return the test log router
     */
    public static TestLoggingProfileLoggerRouter create(final String profileName) {
        Assert.assertNotNull("The profile name cannot be null", profileName);
        final LogContext logContext = LoggingProfileContextSelector.getInstance().get(profileName);
        Assert.assertNotNull(String.format("The context for profile %s could not be found.", profileName), logContext);
        final TestLoggingProfileLoggerRouter result = new TestLoggingProfileLoggerRouter(logContext);
        Logger.setLoggerRouter(result);
        return result;
    }

    @Override
    public LogContext getLogContext() {
        return currentLogContext.get();
    }

    @Override
    public void close() {
        Logger.setLoggerRouter(dft);
        currentLogContext.set(null);
    }

}
