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
import org.jboss.logmanager.LogContextSelector;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * The log context selector to use for the WildFly logging extension.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface WildFlyLogContextSelector extends LogContextSelector {

    /**
     * Get and set the log context.
     *
     * @param newValue the new log context value, or {@code null} to clear
     *
     * @return the previous log context value, or {@code null} if none was set
     *
     * @see org.jboss.logmanager.ThreadLocalLogContextSelector#getAndSet(Object, org.jboss.logmanager.LogContext)
     */
    // TODO (jrp) shouldn't this be used for logging profiles? Maybe it's not working correctly.
    LogContext setLocalContext(LogContext newValue);

    /**
     * Register a class loader with a log context.
     *
     * @param classLoader the class loader
     * @param logContext  the log context
     *
     * @throws IllegalArgumentException if the class loader is already associated with a log context
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#registerLogContext(ClassLoader,
     * org.jboss.logmanager.LogContext)
     */
    void registerLogContext(ClassLoader classLoader, LogContext logContext);

    /**
     * Unregister a class loader/log context association.
     *
     * @param classLoader the class loader
     * @param logContext  the log context
     *
     * @return {@code true} if the association exists and was removed, {@code false} otherwise
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#unregisterLogContext(ClassLoader,
     * org.jboss.logmanager.LogContext)
     */
    boolean unregisterLogContext(ClassLoader classLoader, LogContext logContext);

    /**
     * Register a class loader which is a known log API, and thus should be skipped over when searching for the
     * log context to use for the caller class.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if this class loader was previously unknown, or {@code false} if it was already
     * registered
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#addLogApiClassLoader(ClassLoader)
     */
    boolean addLogApiClassLoader(ClassLoader apiClassLoader);

    /**
     * Remove a class loader from the known log APIs set.
     *
     * @param apiClassLoader the API class loader
     *
     * @return {@code true} if the class loader was removed, or {@code false} if it was not known to this selector
     *
     * @see org.jboss.logmanager.ClassLoaderLogContextSelector#removeLogApiClassLoader(ClassLoader)
     */
    boolean removeLogApiClassLoader(ClassLoader apiClassLoader);

    /**
     * Returns the number of registered {@link org.jboss.logmanager.LogContext log contexts}.
     *
     * @return the number of registered log contexts
     */
    int registeredCount();

    /**
     * Get or create the log context based on the logging profile.
     *
     * @param profileName the logging profile to get or create the log context for
     *
     * @return the log context that was found or a new log context
     */
    LogContext getOrCreateProfile(final String profileName);

    /**
     * Returns the log context associated with the logging profile or {@code null} if the logging profile does not have
     * an associated log context.
     *
     * @param loggingProfile the logging profile associated with the log context
     *
     * @return the log context or {@code null} if the logging profile is not associated with a log context
     */
    LogContext getProfileContext(String loggingProfile);

    /**
     * Checks to see if the logging profile has a log context associated with it.
     *
     * @param loggingProfile the logging profile to check
     *
     * @return {@code true} if the logging profile has an associated log context, otherwise {@code false}
     */
    boolean profileContextExists(String loggingProfile);

    /**
     * Adds the associated log context from the logging profile.
     *
     * @param loggingProfile the logging profile associated with the log context
     * @param context        the context to associate the profile to
     *
     * @return the log context that was removed or {@code null} if there was no log context associated
     */
    LogContext addProfileContext(String loggingProfile, LogContext context);

    /**
     * Removes the associated log context from the logging profile.
     *
     * @param loggingProfile the logging profile associated with the log context
     *
     * @return the log context that was removed or {@code null} if there was no log context associated
     */
    LogContext removeProfileContext(String loggingProfile);

    static WildFlyLogContextSelector getContextSelector() {
        final var found = LogContext.getLogContextSelector();
        if (found instanceof WildFlyLogContextSelector) {
            return (WildFlyLogContextSelector) found;
        }
        // We need to create a new instance and wrap the current one
        // TODO (jrp) this is not thread-safe
        final WildFlyLogContextSelector wrapped = new WildFlyLogContextSelectorImpl(found);
        LogContext.setLogContextSelector(wrapped);
        return wrapped;
    }

    class Factory {
        // TODO (jrp) do we need to use the WildFlyConfiguratorFactory? We need to remind ourselves how this works
        private static final LogContext EMBEDDED_LOG_CONTEXT = LogContext.create(true, new WildFlyLogContextInitializer());

        /**
         * Creates a new selector which by default returns a static embedded context which can be used.
         *
         * @return a new selector
         */
        public static WildFlyLogContextSelector createEmbedded() {
            clearLogContext();
            return new WildFlyLogContextSelectorImpl(EMBEDDED_LOG_CONTEXT);
        }

        private static void clearLogContext() {
            // Remove the configurator and clear the log context
            final ContextConfiguration configuration = EMBEDDED_LOG_CONTEXT.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
            if (configuration != null) {
                try {
                    configuration.close();
                } catch (Exception e) {
                    // TODO (jrp) don't throw this, log something which feels counterintuitive.
                    throw new RuntimeException(e);
                }
            }
            try {
                EMBEDDED_LOG_CONTEXT.close();
                // TODO (jrp) don't throw this, log something which feels counterintuitive.
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
