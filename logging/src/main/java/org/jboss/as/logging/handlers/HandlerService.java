/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.handlers;

import java.io.UnsupportedEncodingException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class HandlerService<T extends Handler> implements Service {
    private final Consumer<T> handlerConsumer;
    private final Supplier<Formatter> formatter;
    private final Supplier<T> handlerCreator;
    private volatile T handler;

    protected HandlerService(final Consumer<T> handlerConsumer, final Supplier<Formatter> formatter, final Supplier<T> handlerCreator) {
        this.handlerConsumer = handlerConsumer;
        this.formatter = formatter;
        this.handlerCreator = handlerCreator;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        // TODO (jrp) we'd need to ensure that handler does not get assigned to loggers or other handlers until this one
        // TODO (jrp) is configured
        final T handler = handlerCreator.get();
        handler.setFormatter(formatter.get());
        this.handler = handler;
        handlerConsumer.accept(handler);
    }

    @Override
    public void stop(final StopContext context) {
        handlerConsumer.accept(null);
        final T handler = this.handler;
        if (handler == null) {
            // Nothing to do
            return;
        }
        try {
            handler.setEncoding(null);
        } catch (UnsupportedEncodingException ignore) {
            // Doesn't really matter if we can't set this to null
        }
        handler.setErrorManager(null);
        handler.setFilter(null);
        handler.setFormatter(null);
        handler.setLevel(Level.ALL);
        if (handler instanceof ExtHandler) {
            ((ExtHandler) handler).clearHandlers();
        }
        handler.close();
        stop(handler);
    }

    protected void stop(T handler) {
        // Do nothing by default
    }
}
