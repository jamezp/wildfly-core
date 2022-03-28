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

import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.FILTER_SPEC;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.filters.DenyAllFilter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractHandlerWriteOperationStepHandler<T extends Handler> extends AbstractWriteAttributeHandler<T> {

    private static final Logger.AttachmentKey<Map<String, Filter>> DISABLED_HANDLERS_KEY = new Logger.AttachmentKey<>();
    private static final Object HANDLER_LOCK = new Object();
    private final AttributeDefinition[] attributes;

    protected AbstractHandlerWriteOperationStepHandler(final AttributeDefinition... definitions) {
        super(definitions);
        this.attributes = Arrays.copyOf(definitions, definitions.length);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                           final String attributeName, final ModelNode resolvedValue,
                                           final ModelNode currentValue, final HandbackHolder<T> handbackHolder) throws OperationFailedException {
        final T handler = (T) context.getServiceRegistry(true)
                .getRequiredService(Capabilities.HANDLER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue()))
                .getService().getValue();
        handbackHolder.setHandback((T) handler);
        // TODO (jrp) we need to make sure we're getting the correct context
        final LogContext logContext = LogContext.getLogContext();
        return handleDefaultAttributes(context, operation, attributeName, resolvedValue, logContext, handler);
    }

    @Override
    protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName,
                                         final ModelNode valueToRestore, final ModelNode valueToRevert, final T handback) throws OperationFailedException {
        // TODO (jrp) we need to make sure we're getting the correct context
        final LogContext logContext = LogContext.getLogContext();
        handleDefaultAttributes(context, operation, attributeName, valueToRestore, logContext, handback);
    }

    @Override
    protected void finishModelStage(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode newValue, final ModelNode oldValue, final Resource model) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
        // If a filter attribute, update the filter-spec attribute
        if (CommonAttributes.FILTER.getName().equals(attributeName)) {
            final String filterSpec = Filters.filterToFilterSpec(newValue);
            final ModelNode filterSpecValue = (filterSpec.isEmpty() ? new ModelNode() : new ModelNode(filterSpec));
            // Undefine the filter-spec
            model.getModel().get(FILTER_SPEC.getName()).set(filterSpecValue);
        }
    }

    protected boolean handleDefaultAttributes(final OperationContext context, final ModelNode operation, final String attributeName,
                                              final ModelNode value, final LogContext logContext, final T handler) throws OperationFailedException {
        if (CommonAttributes.LEVEL.getName().equals(attributeName)) {
            handler.setLevel(logContext.getLevelForName(value.asString().toUpperCase(Locale.ROOT)));
        } else if (CommonAttributes.ENCODING.getName().equals(attributeName)) {
            try {
                handler.setEncoding(value.asString());
            } catch (UnsupportedEncodingException e) {
                // TODO (jrp) i18n
                throw new OperationFailedException(e);
            }
        } else if (CommonAttributes.FILTER.getName().equals(attributeName)) {

        } else if (FILTER_SPEC.getName().equals(attributeName)) {

        } else if (AbstractHandlerDefinition.FORMATTER.getName().equals(attributeName)) {
            if (value.isDefined()) {
                // TODO (jrp) what do we do if there is a NAMED_FORMATTER too?
                final Formatter formatter = (Formatter) context.getServiceRegistry(false)
                        .getRequiredService(Capabilities.FORMATTER_CAPABILITY.getCapabilityServiceName(
                                PatternFormatterResourceDefinition.getDefaultFormatterName(context.getCurrentAddressValue())))
                        .getService().getValue();
                handler.setFormatter(formatter);
            } else {
                handler.setFormatter(null);
            }
        } else if (AbstractHandlerDefinition.NAMED_FORMATTER.getName().equals(attributeName)) {
            if (value.isDefined()) {
                final Formatter formatter = (Formatter) context.getServiceRegistry(false)
                        .getRequiredService(Capabilities.FORMATTER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue()))
                        .getValue();
                handler.setFormatter(formatter);
            } else {
                handler.setFormatter(null);
            }
        } else if (CommonAttributes.ENABLED.getName().equals(attributeName)) {
            if (handler instanceof ExtHandler) {
                ((ExtHandler) handler).setEnabled(value.asBoolean());
            } else {
                final String handlerName = context.getCurrentAddressValue();
                final Logger root = logContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
                Map<String, Filter> disableHandlers = root.getAttachment(DISABLED_HANDLERS_KEY);
                if (value.asBoolean()) {
                    if (disableHandlers != null && disableHandlers.containsKey(handlerName)) {
                        synchronized (HANDLER_LOCK) {
                            final Filter filter = disableHandlers.get(handlerName);
                            handler.setFilter(filter);
                            disableHandlers.remove(handlerName);
                        }
                    }
                } else {
                    if (disableHandlers == null) {
                        disableHandlers = new HashMap<>();
                        final Map<String, Filter> current = root.attachIfAbsent(DISABLED_HANDLERS_KEY, disableHandlers);
                        if (current != null) {
                            disableHandlers = current;
                        }
                    }
                    synchronized (HANDLER_LOCK) {
                        if (!disableHandlers.containsKey(handlerName)) {
                            disableHandlers.put(handlerName, handler.getFilter());
                            handler.setFilter(DenyAllFilter.getInstance());
                        }
                    }
                }
            }
        } else if (CommonAttributes.AUTOFLUSH.getName().equals(attributeName)) {
            // TODO (jrp) should likely never be false, but should we throw an exception just in case?
            if (handler instanceof ExtHandler) {
                ((ExtHandler) handler).setAutoFlush(value.asBoolean());
            }
        } else {
            return handleAttribute(context, operation, attributeName, value, logContext, handler);
        }
        return false;
    }

    protected boolean handleAttribute(final OperationContext context, final ModelNode operation, final String attributeName,
                                      final ModelNode value, final LogContext logContext, final T handler) {
        return false;
    }

    protected AttributeDefinition[] getAttributes() {
        return attributes;
    }
}
