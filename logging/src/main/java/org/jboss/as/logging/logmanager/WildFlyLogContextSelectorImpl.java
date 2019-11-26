/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging.logmanager;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.logmanager.ClassLoaderLogContextSelector;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.logmanager.ThreadLocalLogContextSelector;
import org.jboss.modules.ModuleClassLoader;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WildFlyLogContextSelectorImpl implements WildFlyLogContextSelector {

    private final LogContextSelector defaultLogContextSelector;
    private final ClassLoaderLogContextSelector contextSelector;

    private final ThreadLocalLogContextSelector threadLocalContextSelector;

    private final Map<ClassLoader, ValueHolder> registered;

    WildFlyLogContextSelectorImpl(final LogContext defaultLogContext) {
        this(new ClassLoaderLogContextSelector(new LogContextSelector() {
            @Override
            public LogContext getLogContext() {
                return defaultLogContext;
            }
        }));
    }

    WildFlyLogContextSelectorImpl(final LogContextSelector defaultLogContextSelector) {
        // There is not a way to reset the LogContextSelector after a reload. If the current selector is already a
        // WildFlyLogContextSelectorImpl we should use the previous default selector. This avoids possibly wrapping the
        // same log context several times. It should also work with the embedded CLI selector as the commands handle
        // setting and resetting the contexts.
        final LogContextSelector dft;
        if (defaultLogContextSelector instanceof WildFlyLogContextSelectorImpl) {
            dft = ((WildFlyLogContextSelectorImpl) defaultLogContextSelector).defaultLogContextSelector;
        } else {
            dft = defaultLogContextSelector;
        }
        this.defaultLogContextSelector = dft;
        contextSelector = new ClassLoaderLogContextSelector(dft, true);
        threadLocalContextSelector = new ThreadLocalLogContextSelector(contextSelector);
        registered = new HashMap<>();
    }

    @Override
    public LogContext getLogContext() {
        return threadLocalContextSelector.getLogContext();
    }

    @Override
    public LogContext getAndSet(final Object securityKey, final LogContext newValue) {
        return threadLocalContextSelector.getAndSet(securityKey, newValue);
    }

    @Override
    public void registerLogContext(final ClassLoader classLoader, final LogContext logContext) {
        synchronized (registered) {
            ValueHolder value = registered.get(classLoader);
            if (value == null) {
                value = new ValueHolder(logContext);
                final ValueHolder appearing = registered.putIfAbsent(classLoader, value);
                if (appearing == null) {
                    contextSelector.registerLogContext(classLoader, logContext);
                }
            } else {
                if (!value.logContext.equals(logContext)) {
                    throw LoggingLogger.ROOT_LOGGER.classLoaderAlreadyRegistered(classLoader);
                }
            }
            value.count++;
        }
    }

    @Override
    public boolean unregisterLogContext(final ClassLoader classLoader, final LogContext logContext) {
        synchronized (registered) {
            final ValueHolder value = registered.get(classLoader);
            // Shouldn't be null, but we should check
            if (value == null) {
                return contextSelector.unregisterLogContext(classLoader, logContext);
            }
            // TODO (jrp) it would be nice to have a test which could ensure we end up with an empty map
            if (--value.count == 0) {
                registered.remove(classLoader);
                // TODO (jrp) or change to a trace, definitely remove the casting
                LoggingLogger.ROOT_LOGGER.warnf("Removed class loader %s from the registered class loaders.", ((ModuleClassLoader) classLoader).getModule().getName());
                return contextSelector.unregisterLogContext(classLoader, logContext);
            }
        }
        return true; // TODO (jrp) is this correct? seems a bit odd
    }

    @Override
    public boolean addLogApiClassLoader(final ClassLoader apiClassLoader) {
        return contextSelector.addLogApiClassLoader(apiClassLoader);
    }

    @Override
    public boolean removeLogApiClassLoader(final ClassLoader apiClassLoader) {
        return contextSelector.removeLogApiClassLoader(apiClassLoader);
    }

    @Override
    public int registeredCount() {
        return registered.size();
    }

    private static class ValueHolder {
        final LogContext logContext;
        int count;

        private ValueHolder(final LogContext logContext) {
            this.logContext = logContext;
            count = 0;
        }
    }
}
