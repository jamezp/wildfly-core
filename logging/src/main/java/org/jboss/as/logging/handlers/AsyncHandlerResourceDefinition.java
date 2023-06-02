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

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.ADD_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.REMOVE_HANDLER_OPERATION_NAME;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.AsyncHandler;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AsyncHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String NAME = "async-handler";
    private static final String ADD_SUBHANDLER_OPERATION_NAME = "assign-subhandler";
    private static final String REMOVE_SUBHANDLER_OPERATION_NAME = "unassign-subhandler";
    private static final PathElement ASYNC_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition QUEUE_LENGTH = SimpleAttributeDefinitionBuilder.create("queue-length", ModelType.INT)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setValidator(new IntRangeValidator(1, false))
            .build();

    public static final SimpleAttributeDefinition OVERFLOW_ACTION = SimpleAttributeDefinitionBuilder.create("overflow-action", ModelType.STRING)
            .setAllowExpression(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        String content = resourceModel.get(attribute.getName()).asString().toLowerCase(Locale.ENGLISH);
                        writer.writeAttribute("value", content);
                        writer.writeEndElement();
                    }
                }
            })
            .setRequired(false)
            .setDefaultValue(new ModelNode(OverflowAction.BLOCK.name()))
            .setValidator(EnumValidator.create(OverflowAction.class))
            .build();

    static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING)
            .setAllowExpression(false)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.HANDLER_REFERENCE_RECORDER)
            .build();

    public static final LogHandlerListAttributeDefinition SUBHANDLERS = LogHandlerListAttributeDefinition.Builder.of("subhandlers", HANDLER)
            .setAllowDuplicates(false)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = {ENABLED, LEVEL, FILTER_SPEC, QUEUE_LENGTH, OVERFLOW_ACTION, SUBHANDLERS};
    private static final AttributeDefinition[] ALL_ATTRIBUTES = Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES);


    public AsyncHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(createParameters(ASYNC_HANDLER_PATH, new AddHandlerOperationStepHandler(includeLegacyAttributes)), true,
                new AsyncHandlerWriteStepHandler(includeLegacyAttributes), (includeLegacyAttributes ? ALL_ATTRIBUTES : ATTRIBUTES));
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        final ResourceDescriptionResolver resourceDescriptionResolver = getResourceDescriptionResolver();
        final OperationStepHandler addHandler = new LegacyUpdateStepHandler<AsyncHandler>(null, SUBHANDLERS) {
            @Override
            protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                                 final String attributeName, final ModelNode valueToRestore,
                                                 final ModelNode valueToRevert, final AsyncHandler handback) {
                final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
                final var handler = configuration.getHandler(valueToRevert.asString());
                if (handler != null) {
                    handback.removeHandler(handler);
                }
            }

            @Override
            void applyUpdateToRuntime(final OperationContext context, final String attributeName,
                                      final ModelNode resolvedValue, final ModelNode currentValue,
                                      final ContextConfiguration configuration, final AsyncHandler handler) {
                final var handlerToAdd = configuration.getHandler(resolvedValue.asString());
                if (handlerToAdd != null) {
                    handler.addHandler(handlerToAdd);
                }
            }

            @Override
            Optional<AttributeDefinition> findAttribute(final ModelNode op) {
                return Optional.of(SUBHANDLERS);
            }

            @Override
            ModelNode resolveValue(final OperationContext context, final ModelNode operation, final AttributeDefinition attribute) {
                final ModelNode newHandler = operation.get(ModelDescriptionConstants.NAME);
                // Get the old handlers
                final ModelNode currentModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                final ModelNode currentHandlers;
                if (currentModel.hasDefined(SUBHANDLERS.getName())) {
                    currentHandlers = currentModel.get(SUBHANDLERS.getName());
                } else {
                    currentHandlers = new ModelNode().setEmptyList();
                }
                currentHandlers.add(newHandler);
                return currentHandlers;
            }
        };
        final OperationStepHandler removeHandler = new LegacyUpdateStepHandler<AsyncHandler>(null, SUBHANDLERS) {
            @Override
            protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                                 final String attributeName, final ModelNode valueToRestore,
                                                 final ModelNode valueToRevert, final AsyncHandler handback) {
                final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
                final var handler = configuration.getHandler(valueToRevert.asString());
                if (handler != null) {
                    handback.addHandler(handler);
                }
            }

            @Override
            void applyUpdateToRuntime(final OperationContext context, final String attributeName,
                                      final ModelNode resolvedValue, final ModelNode currentValue,
                                      final ContextConfiguration configuration, final AsyncHandler handler) {
                final var handlerToAdd = configuration.getHandler(resolvedValue.asString());
                if (handlerToAdd != null) {
                    handler.removeHandler(handlerToAdd);
                }
            }

            @Override
            Optional<AttributeDefinition> findAttribute(final ModelNode op) {
                return Optional.of(SUBHANDLERS);
            }

            @Override
            ModelNode resolveValue(final OperationContext context, final ModelNode operation, final AttributeDefinition attribute) {
                final ModelNode toRemove = operation.get(ModelDescriptionConstants.NAME);
                // Get the old handlers
                final ModelNode currentModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                final ModelNode currentHandlers;
                if (currentModel.hasDefined(SUBHANDLERS.getName())) {
                    currentHandlers = currentModel.get(SUBHANDLERS.getName());
                } else {
                    currentHandlers = new ModelNode().setEmptyList();
                }
                return new ModelNode().set(currentHandlers.asList()
                        .stream()
                        .filter(handler -> !handler.asString().equals(toRemove.asString()))
                        .collect(Collectors.toList()));
            }
        };
        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), addHandler);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), removeHandler);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), addHandler);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), removeHandler);
    }

    private static OperationStepHandler nestRuntime(final int level, final OperationStepHandler handler) {
        if (level == 0) {
            return handler;
        }
        return nestRuntime(level - 1, ((context, operation) -> {
            context.addStep(handler, OperationContext.Stage.RUNTIME);
        }));
    }


    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(ASYNC_HANDLER_PATH);
        }
    }

    private static class AddHandlerOperationStepHandler extends AbstractHandlerAddStepHandler<AsyncHandler> {

        public AddHandlerOperationStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model,
                            final AsyncHandler handler, final ContextConfiguration configuration, final String name) throws OperationFailedException {
            // Handlers need to execute in a new stage
            context.addStep((runtimeContext, runtimeOperation) ->
                    handler.setHandlers(SUBHANDLERS.getHandlers(configuration, runtimeContext, model)), OperationContext.Stage.RUNTIME);
            handler.setOverflowAction(OverflowAction.valueOf(OVERFLOW_ACTION.resolveModelAttribute(context, model)
                    .asString()));
        }

        @Override
        AsyncHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                   final ContextConfiguration configuration, final String name) throws OperationFailedException {
            // Get the queue-length
            final int queueLength = QUEUE_LENGTH.resolveModelAttribute(context, operation).asInt();
            return new AsyncHandler(queueLength);
        }
    }

    private static class AsyncHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<AsyncHandler> {
        protected AsyncHandlerWriteStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final ContextConfiguration configuration, final AsyncHandler handler) throws OperationFailedException {
            if (attributeName.equals(SUBHANDLERS.getName())) {
                // Handlers need to execute in a new stage
                context.addStep((runtimeContext, runtimeOperation) ->
                        handler.setHandlers(SUBHANDLERS.getHandlers(configuration, runtimeContext, resolvedValue, true)), OperationContext.Stage.RUNTIME);
            } else if (attributeName.equals(OVERFLOW_ACTION.getName())) {
                handler.setOverflowAction(OverflowAction.valueOf(resolvedValue.asString()));
            }
        }

        @Override
        boolean requiresReload(final String attributeName) {
            return attributeName.equals(QUEUE_LENGTH.getName());
        }
    }
}
