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

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class WildFlyContextConfiguration extends ContextConfiguration {

    /**
     * Creates a new context configuration.
     */
    WildFlyContextConfiguration(final LogContext logContext) {
        super(logContext);
    }

    public static ContextConfiguration getInstance() {
        return getInstance(LogContext.getLogContext());
    }

    // TODO (jrp) what do we do here? Just get the LogContext and then use getInstance(LogContext)?
    public static ContextConfiguration getInstance(final String name) {
        // TODO (jrp) we'll need a way to get this for logging profiles as well.
        // TODO (jrp) what do we do if this is null? It shouldn't happen, but that means this has been accessed before
        // TODO (jrp) the WildFlyConfiguratorFactory has been completed

        // TODO (jrp) I think what we should do is move the LoggingProfileSelector and the WildFlyContextSelector here
        final var selector = WildFlyLogContextSelector.getContextSelector();
        final var profileContext = selector.getProfileContext(name);
        return profileContext == null ? getInstance(selector.getLogContext()) : getInstance(profileContext);
    }

    public static ContextConfiguration getInstance(final LogContext logContext) {
        var configuration = logContext.getAttachment(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configuration == null) {
            configuration = new ContextConfiguration(logContext);
            final var appearing = logContext.attachIfAbsent(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
            if (appearing != null) {
                configuration = appearing;
            }
        }
        return configuration;
    }

}
