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

import org.jboss.logmanager.ClassLoaderLogContextSelector;
import org.jboss.logmanager.ContextClassLoaderLogContextSelector;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.LoggerRouter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class WildFlyLogContextSelectorImpl implements WildFlyLogContextSelector {

    private final LogContextSelector defaultLogContextSelector;
    private final ClassLoaderLogContextSelector clSelector;
    private final ContextClassLoaderLogContextSelector tcclSelector;

    private final ThreadLocal<LogContext> localContext = new ThreadLocal<>();
    private int counter;
    private int dftCounter;

    WildFlyLogContextSelectorImpl(final LogContext defaultLogContext, final boolean useLogRouting) {
        this(() -> defaultLogContext, useLogRouting);
    }

    WildFlyLogContextSelectorImpl(final LogContextSelector defaultLogContextSelector, final boolean useLogRouting) {
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
        counter = 0;
        dftCounter = 0;
        if (useLogRouting) {
            tcclSelector = new ContextClassLoaderLogContextSelector(defaultLogContextSelector);
            clSelector = null;
            // This needs to be set last since it delegates to this selector
            Logger.setLoggerRouter(new LoggerRouter(defaultLogContextSelector.getLogContext()) {
                @Override
                public LogContext getLogContext() {
                    return WildFlyLogContextSelectorImpl.this.getLogContext();
                }
            });
        } else {
            Logger.setLoggerRouter(null);
            clSelector = new ClassLoaderLogContextSelector(defaultLogContextSelector, true);
            tcclSelector = null;
        }
    }

    @Override
    public LogContext getLogContext() {
        final LogContext localContext = this.localContext.get();
        if (localContext != null) {
            return localContext;
        }
        final int counter;
        synchronized (this) {
            counter = this.counter;
        }
        // If we have no registered contexts we can just use the default selector. This should improve performance
        // in most cases as the call stack will not be walked. This does depend on the on what was used for the
        // default selector, however in most cases it should perform better.
        return counter > 0 ? (tcclSelector == null ? clSelector.getLogContext() : tcclSelector.getLogContext()) : defaultLogContextSelector.getLogContext();
    }

    @Override
    public LogContext setLocalContext(final LogContext newValue) {
        try {
            return localContext.get();
        } finally {
            if (newValue == null) {
                localContext.remove();
            } else {
                localContext.set(newValue);
            }
        }
    }

    @Override
    public void registerLogContext(final ClassLoader classLoader, final LogContext logContext) {
        // We want to register regardless of the current counter for cases when a different log context is registered
        // later.
        if (clSelector != null) {
            clSelector.registerLogContext(classLoader, logContext);
        }
        if (tcclSelector != null) {
            tcclSelector.registerLogContext(classLoader, logContext);
        }
        synchronized (this) {
            if (counter > 0) {
                counter++;
            } else if (logContext != defaultLogContextSelector.getLogContext()) {
                // Move the dftCounter to the counter and add one for this specific log context
                counter = dftCounter + 1;
                dftCounter = 0;
            } else {
                // We're using the default log context at this point
                dftCounter++;
            }
        }
    }

    @Override
    public boolean unregisterLogContext(final ClassLoader classLoader, final LogContext logContext) {
        boolean removed = false;
        if (clSelector != null) {
            removed = clSelector.unregisterLogContext(classLoader, logContext);
        }
        if (tcclSelector != null) {
            removed = tcclSelector.unregisterLogContext(classLoader, logContext);
        }
        if (removed) {
            synchronized (this) {
                if (counter > 0) {
                    counter--;
                } else if (dftCounter > 0) {
                    // We don't test the log context here and just assume we're using the default. This is safe as the
                    // registered log contexts must be the default log context.
                    dftCounter--;
                }
            }
        }
        return removed;
    }

    @Override
    public boolean addLogApiClassLoader(final ClassLoader apiClassLoader) {
        if (clSelector != null) {
            return clSelector.addLogApiClassLoader(apiClassLoader);
        }
        return false;
    }

    @Override
    public boolean removeLogApiClassLoader(final ClassLoader apiClassLoader) {
        if (clSelector != null) {
            return clSelector.removeLogApiClassLoader(apiClassLoader);
        }
        return false;
    }

    @Override
    public int registeredCount() {
        synchronized (this) {
            return counter;
        }
    }
}
