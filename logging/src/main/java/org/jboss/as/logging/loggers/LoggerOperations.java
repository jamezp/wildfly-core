/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.loggers;

import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.HANDLER_NAME;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.ROOT_LOGGER_NAME;
import static org.jboss.as.logging.loggers.LoggerAttributes.FILTER_SPEC;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLER;
import static org.jboss.as.logging.loggers.LoggerAttributes.HANDLERS;
import static org.jboss.as.logging.loggers.LoggerResourceDefinition.USE_PARENT_HANDLERS;
import static org.jboss.as.logging.loggers.RootLoggerResourceDefinition.RESOURCE_NAME;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.Logger;
import org.wildfly.core.logmanager.WildFlyDelayedHandler;

/**
 * Date: 14.12.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class LoggerOperations {


    /**
     * A step handler for add operations of logging handlers. Adds default properties to the handler configuration.
     */
    static final class LoggerAddOperationStepHandler extends AbstractAddStepHandler {
        private final AttributeDefinition[] attributes;

        LoggerAddOperationStepHandler(final AttributeDefinition[] attributes) {
            super(attributes);
            this.attributes = attributes;
        }

        @Override
        public void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
            for (AttributeDefinition attribute : attributes) {
                // Filter attribute needs to be converted to filter spec
                if (CommonAttributes.FILTER.equals(attribute)) {
                    final ModelNode filter = CommonAttributes.FILTER.validateOperation(operation);
                    if (filter.isDefined()) {
                        final String value = Filters.filterToFilterSpec(filter);
                        model.get(LoggerAttributes.FILTER_SPEC.getName())
                                .set(value.isEmpty() ? new ModelNode() : new ModelNode(value));
                    }
                } else {
                    attribute.validateAndSet(operation, model);
                }
            }
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final String name = context.getCurrentAddressValue();
            final String loggerName = getLogManagerLoggerName(name);
            final Logger logger = configuration.getContext().getLogger(loggerName);
            logger.setLevel(configuration.getContext()
                    .getLevelForName(LEVEL.resolveModelAttribute(context, model).asString()));
            // Filters need to be in a new step for composite operations
            context.addStep((runtimeContext, runtimeOperation) -> {
                final var filter = FILTER_SPEC.resolveModelAttribute(runtimeContext, model);
                if (filter.isDefined()) {
                    logger.setFilter(Filters.createFilter(configuration, filter.asString()));
                } else {
                    // If a previous filter was added, we need to clear it.
                    logger.setFilter(null);
                }
            }, OperationContext.Stage.RUNTIME);
            // Handlers need to be added last
            context.addStep((runtimeContext, runtimeOperation) -> {
                final var handlers = HANDLERS.resolveModelAttribute(runtimeContext, model);
                if (handlers.isDefined()) {
                    // We use a DelayedHandler on the root logger and we'll add the handlers to that handler to activate it
                    WildFlyDelayedHandler.setHandlers(logger, HANDLERS.getHandlers(configuration, runtimeContext, model));
                }
            }, OperationContext.Stage.RUNTIME);
            // For non-root loggers the use-parent-handlers may have been set
            if (!loggerName.equals(ROOT_LOGGER_NAME)) {
                logger.setUseParentHandlers(USE_PARENT_HANDLERS.resolveModelAttribute(context, model).asBoolean());
            }
        }
    }


    /**
     * A default log handler write attribute step handler.
     */
    static class LoggerWriteAttributeHandler extends AbstractWriteAttributeHandler<Logger> {

        LoggerWriteAttributeHandler(final AttributeDefinition[] attributes) {
            super(attributes);
        }

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                               final String attributeName, final ModelNode resolvedValue,
                                               final ModelNode currentValue, final HandbackHolder<Logger> handbackHolder) throws OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var loggerName = getLogManagerLoggerName(context.getCurrentAddressValue());
            final var logger = configuration.getContext().getLogger(loggerName);
            handbackHolder.setHandback(logger);
            if (LEVEL.getName().equals(attributeName)) {
                logger.setLevel(configuration.getContext().getLevelForName(resolvedValue.asString()));
            } else if (FILTER.getName().equals(attributeName)) {
                // Filter should be replaced by the filter-spec in the super class
                // TODO (jrp) how do we get the filter-spec value?
                logger.setFilter(Filters.createFilter(configuration, resolvedValue.asStringOrNull()));
                //handleProperty(FILTER_SPEC, context, value, configuration, false);
            } else if (FILTER_SPEC.getName().equals(attributeName)) {
                logger.setFilter(Filters.createFilter(configuration, resolvedValue.asStringOrNull()));
            } else if (HANDLERS.getName().equals(attributeName)) {
                // We use a DelayedHandler on the root logger and we'll add the handlers to that handler to activate it
                WildFlyDelayedHandler.setHandlers(logger, HANDLERS.getHandlers(configuration, context, resolvedValue, true));
            } else if (USE_PARENT_HANDLERS.getName().equals(attributeName) && !loggerName.equals(ROOT_LOGGER_NAME)) {
                logger.setUseParentHandlers(resolvedValue.asBoolean());
            }
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                             final String attributeName, final ModelNode valueToRestore,
                                             final ModelNode valueToRevert, final Logger handback) throws OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            if (LEVEL.getName().equals(attributeName)) {
                handback.setLevel(configuration.getContext().getLevelForName(valueToRestore.asString()));
            } else if (FILTER.getName().equals(attributeName)) {
                // Filter should be replaced by the filter-spec in the super class
                // TODO (jrp) how do we get the filter-spec value?
                handback.setFilter(Filters.createFilter(configuration, valueToRestore.asStringOrNull()));
                //handleProperty(FILTER_SPEC, context, value, configuration, false);
            } else if (FILTER_SPEC.getName().equals(attributeName)) {
                handback.setFilter(Filters.createFilter(configuration, valueToRestore.asStringOrNull()));
            } else if (HANDLERS.getName().equals(attributeName)) {
                // We use a DelayedHandler on the root logger and we'll add the handlers to that handler to activate it
                WildFlyDelayedHandler.setHandlers(handback, HANDLERS.getHandlers(configuration, context, valueToRestore, true));
            } else if (USE_PARENT_HANDLERS.getName().equals(attributeName) && !handback.getName()
                    .equals(ROOT_LOGGER_NAME)) {
                handback.setUseParentHandlers(valueToRestore.asBoolean());
            }
        }

        @Override
        protected void finishModelStage(final OperationContext context, final ModelNode operation, final String attributeName,
                                        final ModelNode newValue, final ModelNode oldValue, final Resource model) throws OperationFailedException {
            super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
            // If a filter attribute, update the filter-spec attribute
            if (CommonAttributes.FILTER.getName().equals(attributeName)) {
                final String filterSpec = Filters.filterToFilterSpec(newValue);
                final ModelNode filterSpecValue = (filterSpec.isEmpty() ? new ModelNode() : new ModelNode(filterSpec));
                // Undefine the filter and set the filter-spec
                model.getModel().get(FILTER.getName()).set(new ModelNode());
                model.getModel().get(FILTER_SPEC.getName()).set(filterSpecValue);
            }
        }
    }

    /**
     * A step handler to remove a logger
     */
    static final OperationStepHandler REMOVE_LOGGER = new AbstractRemoveStepHandler() {
        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var loggerName = context.getCurrentAddressValue();
            final var logger = configuration.getContext().getLogger(loggerName);
            // Reset the logger
            logger.setFilter(null);
            logger.clearHandlers();
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(true);
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            // TODO (jrp) implement this
            super.recoverServices(context, operation, model);
        }
    };

    /**
     * A step handler to add a handler.
     */
    static final OperationStepHandler ADD_HANDLER = (context, operation) -> {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        final String handlerName = operation.get(HANDLER_NAME.getName()).asString();
        model.get(HANDLERS.getName()).add(handlerName);
        HANDLER.addCapabilityRequirements(context, resource, new ModelNode(handlerName));

        // TODO (jrp) we likely need this to be done very last so more nesting
        context.addStep(nestRuntime(1, (runtimeContext, runtimeOperation) -> {
            // Find the handler to remove
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var handler = configuration.getHandler(handlerName);
            final Logger logger = configuration.getContext()
                    .getLogger(getLogManagerLoggerName(context.getCurrentAddressValue()));
            if (handler != null) {
                logger.addHandler(handler);
            } else {
                throw new OperationFailedException(LoggingLogger.ROOT_LOGGER.handlerConfigurationNotFound(handlerName));
            }
            runtimeContext.completeStep((rollbackContext, rollbackOperation) -> {
                logger.removeHandler(handler);
            });

        }), OperationContext.Stage.RUNTIME);
    };

    /**
     * A step handler to remove a handler.
     */
    static final OperationStepHandler REMOVE_HANDLER = (context, operation) -> {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        final String handlerName = operation.get(HANDLER_NAME.getName()).asString();
        final List<ModelNode> newHandlers = model.get(HANDLERS.getName())
                .asList()
                .stream()
                .filter(name -> !handlerName.equals(name.asString()))
                .collect(Collectors.toList());
        model.get(HANDLERS.getName()).set(newHandlers);
        HANDLER.removeCapabilityRequirements(context, resource, new ModelNode(handlerName));

        // TODO (jrp) we likely need this to be done very last so more nesting
        context.addStep(nestRuntime(1, (runtimeContext, runtimeOperation) -> {
            // Find the handler to remove
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var handler = configuration.getHandler(handlerName);
            final Logger logger = configuration.getContext()
                    .getLogger(getLogManagerLoggerName(context.getCurrentAddressValue()));
            if (handler != null) {
                logger.removeHandler(handler);
            }
            runtimeContext.completeStep((rollbackContext, rollbackOperation) -> {
                if (handler != null) {
                    logger.addHandler(handler);
                }
            });

        }), OperationContext.Stage.RUNTIME);
    };

    /**
     * A step handler to remove a handler.
     */
    static final OperationStepHandler CHANGE_LEVEL = (context, operation) -> {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();
        final ModelNode currentValue = model.get(LEVEL.getName());
        LEVEL.validateAndSet(operation, model);
        final ModelNode newLevel = model.get(LEVEL.getName());

        // TODO (jrp) we likely need this to be done very last so more nesting
        context.addStep(nestRuntime(1, (runtimeContext, runtimeOperation) -> {
            // Find the handler to remove
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final Logger logger = configuration.getContext()
                    .getLogger(getLogManagerLoggerName(context.getCurrentAddressValue()));
            logger.setLevel(configuration.getContext().getLevelForName(newLevel.asString()));
            runtimeContext.completeStep((rollbackContext, rollbackOperation) -> {
                logger.setLevel(configuration.getContext().getLevelForName(currentValue.asString()));
            });

        }), OperationContext.Stage.RUNTIME);

    };

    /**
     * Returns the logger name that should be used in the log manager.
     *
     * @param name the name of the logger from the resource
     *
     * @return the name of the logger
     */
    private static String getLogManagerLoggerName(final String name) {
        return (name.equals(RESOURCE_NAME) ? CommonAttributes.ROOT_LOGGER_NAME : name);
    }

    private static OperationStepHandler nestRuntime(final int level, final OperationStepHandler handler) {
        if (level == 0) {
            return handler;
        }
        return nestRuntime(level - 1, ((context, operation) -> {
            context.addStep(handler, OperationContext.Stage.RUNTIME);
        }));
    }
}
