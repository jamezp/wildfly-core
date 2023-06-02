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

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.filters.DenyAllFilter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Handlers {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)([kKmMgGbBtT])?");

    private static final Logger.AttachmentKey<Map<String, Filter>> DISABLED_HANDLERS_KEY = new Logger.AttachmentKey<>();

    /**
     * Checks to see if a handler is enabled
     *
     * @param handlerName the name of the handler to enable.
     */
    static boolean isEnabled(final LogContext logContext, final String handlerName) {
        final Map<String, Filter> disableHandlers = logContext.getAttachment(CommonAttributes.ROOT_LOGGER_NAME, DISABLED_HANDLERS_KEY);
        return disableHandlers == null || !disableHandlers.containsKey(handlerName);
    }


    /**
     * Enables the handler if it was previously disabled.
     * <p/>
     * If it was not previously disable, nothing happens.
     *
     * @param configuration the log context configuration.
     * @param handlerName   the name of the handler to enable.
     */
    static void enableHandler(final ContextConfiguration configuration, final Handler handler, final String handlerName) {
        final Map<String, Filter> disableHandlers = configuration.getContext()
                .getAttachment(CommonAttributes.ROOT_LOGGER_NAME, DISABLED_HANDLERS_KEY);
        if (disableHandlers != null && disableHandlers.containsKey(handlerName)) {
            synchronized (DISABLED_HANDLERS_KEY) {
                final Filter filter = disableHandlers.get(handlerName);
                handler.setFilter(filter);
                disableHandlers.remove(handlerName);
            }
        }
    }

    /**
     * Disables the handler if the handler exists and is not already disabled.
     * <p/>
     * If the handler does not exist or is already disabled nothing happens.
     *
     * @param configuration the log context configuration.
     * @param handlerName   the handler name to disable.
     */
    static void disableHandler(final ContextConfiguration configuration, final Handler handler, final String handlerName) {
        final Logger root = configuration.getContext().getLogger(CommonAttributes.ROOT_LOGGER_NAME);
        Map<String, Filter> disableHandlers = root.getAttachment(DISABLED_HANDLERS_KEY);
        synchronized (DISABLED_HANDLERS_KEY) {
            if (disableHandlers == null) {
                disableHandlers = new HashMap<>();
                final Map<String, Filter> current = root.attachIfAbsent(DISABLED_HANDLERS_KEY, disableHandlers);
                if (current != null) {
                    disableHandlers = current;
                }
            }
            if (!disableHandlers.containsKey(handlerName)) {
                disableHandlers.put(handlerName, configuration.getFilter(handlerName));
                handler.setFilter(DenyAllFilter.getInstance());
            }
        }
    }

    public static long parseSize(final ModelNode value) throws OperationFailedException {
        final Matcher matcher = SIZE_PATTERN.matcher(value.asString());
        if (!matcher.matches()) {
            throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(value.asString()));
        }
        long qty = Long.parseLong(matcher.group(1), 10);
        final String chr = matcher.group(2);
        if (chr != null) {
            switch (chr.charAt(0)) {
                case 'b':
                case 'B':
                    break;
                case 'k':
                case 'K':
                    qty <<= 10L;
                    break;
                case 'm':
                case 'M':
                    qty <<= 20L;
                    break;
                case 'g':
                case 'G':
                    qty <<= 30L;
                    break;
                case 't':
                case 'T':
                    qty <<= 40L;
                    break;
                default:
                    throw createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidSize(value.asString()));
            }
        }
        return qty;

    }
}
