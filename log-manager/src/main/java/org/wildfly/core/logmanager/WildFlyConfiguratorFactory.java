/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.logmanager;

import java.util.logging.Level;

import org.jboss.logmanager.ConfiguratorFactory;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextConfigurator;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) do we need this or just the LogContextConfigurator?
// TODO (jrp) we need to consider what happens when we don't execute the runtime stage. Possibly with logging we do the
// TODO (jrp) configuration in the model stage. Or we have a default ConsoleHandler only. This can be handled in the
// TODO (jrp) subsystem though
public class WildFlyConfiguratorFactory implements ConfiguratorFactory {

    @Override
    public LogContextConfigurator create() {
        return (logContext, inputStream) -> {
            // TODO (jrp) should we ignore the logContext and only use the LogContext.getSystemLogContext()?
            // We will not have an input stream, we can safely ignore it.

            // TODO (jrp) what do we do if there is a logging.properties file? We do need to support this for
            // TODO (jrp) legacy reasons. However, we also need to support adding the DelayedHandler. The
            // TODO (jrp) PropertiesLogContextConfigurator will attempt to load implementations of LogContextConfigurator
            // TODO (jrp) with a service loader.
            // TODO (jrp) I think what we do is check if a ContextConfiguration is attached to the current context.
            // TODO (jrp) if it is, log a warning/error, if not continue.

            // TODO (jrp) we need a way to set the default log level
            final Level defaultLevel = Level.INFO;

            // Configure a DelayedHandler
            final WildFlyDelayedHandler delayedHandler = new WildFlyDelayedHandler(logContext);
            delayedHandler.setLevel(defaultLevel);
            delayedHandler.setCloseChildren(false);
            // Add the handler to the root logger
            final Logger root = logContext.getLogger("");
            root.addHandler(delayedHandler);
            root.setLevel(defaultLevel);

            // Add this configuration to a default ContextConfiguration
            var configuration = logContext.getAttachment(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
            if (configuration == null) {
                configuration = new WildFlyContextConfiguration(logContext);
            }
            logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
        };
    }

    @Override
    public int priority() {
        return 0;
    }

    static void reset() {
        final LogContext logContext = LogContext.getLogContext();

        // Add this configuration to a default ContextConfiguration
        var configuration = logContext.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configuration != null) {
            try {
                configuration.close();
            } catch (Exception e) {
                // TODO (jrp) what do we actually do here?
                throw new RuntimeException(e);
            }
        }
        final Level defaultLevel = Level.INFO;
        // Configure a DelayedHandler
        final WildFlyDelayedHandler delayedHandler = new WildFlyDelayedHandler(logContext);
        delayedHandler.setLevel(defaultLevel);
        delayedHandler.setCloseChildren(false);
        // Add the handler to the root logger
        final Logger root = logContext.getLogger("");
        root.addHandler(delayedHandler);
        root.setLevel(defaultLevel);
        configuration = new WildFlyContextConfiguration(logContext);
        logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
    }
}
