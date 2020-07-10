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

package org.wildfly.core.jar.boot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;

import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.handlers.DelayedHandler;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogManagerConfigurator implements Configurator {
    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    private static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";
    private static final String CONFIGURATION = "configuration";
    private static final String STANDALONE = "standalone";
    private static final String LOG = "log";
    private static final String LOG_MANAGER_PROP = "java.util.logging.manager";
    private static final String LOG_MANAGER_CLASS = "org.jboss.logmanager.LogManager";
    private static final String LOG_BOOT_FILE_PROP = "org.jboss.boot.log.file";
    private static final String LOGGING_PROPERTIES = "logging.properties";
    private static final String SERVER_LOG = "server.log";

    static void bootstrap(final Path wildflyHome) throws IOException {
        if (BOOTSTRAPPED.compareAndSet(false, true)) {
            // TODO (jrp) we should figure out if this was already set
            System.setProperty(LOG_MANAGER_PROP, LOG_MANAGER_CLASS);
            configureLogContext(wildflyHome);
        }
    }

    @Override
    public void configure(final InputStream inputStream) throws IOException {
        // TODO (jrp) We want to ensure we only do this once
        if (!BOOTSTRAPPED.get()) {
            // Configure a delayed handler
            final LogContext logContext = LogContext.getLogContext();
            final Logger rootLogger = logContext.getLogger("");
            final DelayedHandler handler = new DelayedHandler();
            // TODO (jrp) we need to set some reasonable defaults or have a way to override the level
            rootLogger.addHandler(handler);
        } else {
            // TODO (jrp) do we want to store the PropertyConfigurator and then re-initialize?
        }
    }

    private static void configureLogContext(final Path wildflyHome) throws IOException {
        final Path baseDir = wildflyHome.resolve(STANDALONE);
        String serverLogDir = System.getProperty(JBOSS_SERVER_LOG_DIR, null);
        if (serverLogDir == null) {
            serverLogDir = baseDir.resolve(LOG).toString();
            System.setProperty(JBOSS_SERVER_LOG_DIR, serverLogDir);
        }
        final String serverCfgDir = System.getProperty(JBOSS_SERVER_CONFIG_DIR, baseDir.resolve(CONFIGURATION).toString());
        // TODO (jrp) I'm not sure why we're using a new log context here, but it could be correct. However for testing I'm using the default selector
        //final LogContext embeddedLogContext = LogContext.create();
        final LogContext embeddedLogContext = LogContext.getLogContext();
        final Path bootLog = Paths.get(serverLogDir).resolve(SERVER_LOG);
        final Path loggingProperties = Paths.get(serverCfgDir).resolve(Paths.get(LOGGING_PROPERTIES));
        if (Files.exists(loggingProperties)) {
            try (final InputStream in = Files.newInputStream(loggingProperties)) {
                System.setProperty(LOG_BOOT_FILE_PROP, bootLog.toAbsolutePath().toString());
                PropertyConfigurator configurator = new PropertyConfigurator(embeddedLogContext);
                configurator.configure(in);
                // Check to see if the root logger has a DelayedHandler, if so we need to set the current handlers
                final LogContext logContext = LogContext.getLogContext();
                final Logger rootLogger = logContext.getLogger("");
                DelayedHandler delayedHandler = null;
                // TODO (jrp) may be best to just cache the handler or add it specifically to the LogContextConfiguration
                for (Handler handler : rootLogger.getHandlers()) {
                    if (handler instanceof DelayedHandler) {
                        delayedHandler = (DelayedHandler) handler;
                        delayedHandler.setCloseChildren(false);
                        break;
                    }
                }
                if (delayedHandler != null) {
                    final LogContextConfiguration logContextConfiguration = configurator.getLogContextConfiguration();
                    // Get the root logger from the configuration
                    final LoggerConfiguration config = logContextConfiguration.getLoggerConfiguration("");
                    final Collection<Handler> handlers = new ArrayList<>();
                    if (config != null) {
                        final List<String> handlerNames = config.getHandlerNames();
                        for (String handlerName : handlerNames) {
                            final HandlerConfiguration handlerConfiguration = logContextConfiguration.getHandlerConfiguration(handlerName);
                            handlers.add(handlerConfiguration.getInstance());
                        }
                        // TODO (jrp) this should activate the delayed handler
                        delayedHandler.setHandlers(handlers.toArray(new Handler[0]));
                        // TODO (jrp) This could lose log messages, but for testing sake it will work. If we don't
                        // TODO (jrp) remove the handler and close it we'll end up with duplicate messages.
                        rootLogger.removeHandler(delayedHandler);
                        delayedHandler.close();
                    }
                }
            }
        }
    }
}
