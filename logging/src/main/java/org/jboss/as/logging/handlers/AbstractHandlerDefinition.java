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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLE;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.NAME;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.ReadFilterOperationStepHandler;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.formatters.PatternFormatter;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractHandlerDefinition extends SimpleResourceDefinition {

    public static final String UPDATE_OPERATION_NAME = "update-properties";
    public static final String CHANGE_LEVEL_OPERATION_NAME = "change-log-level";

    public static final SimpleAttributeDefinition FILTER_SPEC = SimpleAttributeDefinitionBuilder.create("filter-spec", ModelType.STRING, true)
            .addAlternatives("filter")
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.HANDLER_FILTER_REFERENCE_RECORDER)
            .build();

    public static final SimpleAttributeDefinition FORMATTER = SimpleAttributeDefinitionBuilder.create("formatter", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives("named-formatter")
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        writer.writeStartElement(PatternFormatterResourceDefinition.PATTERN_FORMATTER.getXmlName());
                        final String pattern = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(PatternFormatterResourceDefinition.PATTERN.getXmlName(), pattern);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }
            })
            .setDefaultValue(new ModelNode("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n"))
            .build();

    public static final SimpleAttributeDefinition NAMED_FORMATTER = SimpleAttributeDefinitionBuilder.create("named-formatter", ModelType.STRING, true)
            .setAllowExpression(false)
            .setAlternatives("formatter")
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(FORMATTER.getXmlName());
                        writer.writeStartElement(attribute.getXmlName());
                        String content = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(CommonAttributes.NAME.getName(), content);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }
            })
            .setCapabilityReference(Capabilities.HANDLER_FORMATTER_REFERENCE_RECORDER)
            .build();

    static final AttributeDefinition[] DEFAULT_ATTRIBUTES = {
            LEVEL,
            ENABLED,
            ENCODING,
            FORMATTER,
            FILTER_SPEC,
    };

    static final AttributeDefinition[] LEGACY_ATTRIBUTES = {
            FILTER,
    };

    private final AbstractHandlerWriteStepHandler<? extends Handler> writeHandler;
    private final AttributeDefinition[] writableAttributes;
    private final AttributeDefinition[] readOnlyAttributes;
    private final boolean registerLegacyOps;

    protected AbstractHandlerDefinition(final Parameters parameters,
                                        final boolean registerLegacyOps,
                                        final AbstractHandlerWriteStepHandler<? extends Handler> writeHandler,
                                        final AttributeDefinition[] writableAttributes) {
        this(parameters, registerLegacyOps, writeHandler, null, writableAttributes);
    }

    protected AbstractHandlerDefinition(final Parameters parameters,
                                        final boolean registerLegacyOps,
                                        final AbstractHandlerWriteStepHandler<? extends Handler> writeHandler,
                                        final AttributeDefinition[] readOnlyAttributes,
                                        final AttributeDefinition[] writableAttributes) {
        super(parameters);
        this.registerLegacyOps = registerLegacyOps;
        this.writableAttributes = writableAttributes;
        this.writeHandler = writeHandler;
        this.readOnlyAttributes = readOnlyAttributes;
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : writableAttributes) {
            // Filter requires a special reader
            if (def.getName().equals(FILTER.getName())) {
                resourceRegistration.registerReadWriteAttribute(def, ReadFilterOperationStepHandler.INSTANCE, writeHandler);
            } else {
                resourceRegistration.registerReadWriteAttribute(def, null, writeHandler);
            }
        }
        if (readOnlyAttributes != null) {
            for (AttributeDefinition def : readOnlyAttributes) {
                resourceRegistration.registerReadOnlyAttribute(def, null);
            }
        }
        // Be careful with this attribute. It needs to show up in the "add" operation param list so ops from legacy
        // scripts will validate. It does because it's registered as an attribute but is not setResourceOnly(true)
        // so DefaultResourceAddDescriptionProvider adds it to the param list
        // TODO (jrp) this shouldn't be registered for every resource, but we need to figure out which ones
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);

        if (registerLegacyOps) {
            final var updateHandler = new EnableDisableHandlerStepHandler<>(writeHandler);
            final ResourceDescriptionResolver resourceDescriptionResolver = getResourceDescriptionResolver();
            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ENABLE, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .build(), updateHandler);

            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(DISABLE, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .build(), updateHandler);

            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(CHANGE_LEVEL_OPERATION_NAME, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(CommonAttributes.LEVEL)
                    .build(), new LegacyUpdateStepHandler<>(writeHandler, LEVEL));

            final SimpleOperationDefinition updateProperties = new SimpleOperationDefinitionBuilder(UPDATE_OPERATION_NAME, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(writableAttributes)
                    .build();
            final OperationStepHandler updateStepHandler = new LegacyUpdateStepHandler<>(writeHandler, writableAttributes);
            registration.registerOperationHandler(updateProperties, updateStepHandler);
        }
    }

    abstract static class AbstractHandlerAddStepHandler<T extends Handler> extends AbstractAddStepHandler {
        AbstractHandlerAddStepHandler(final boolean includeLegacyAttributes, final AttributeDefinition[] defaultAttributes,
                                      final AttributeDefinition... attributes) {
            super(includeLegacyAttributes ? new Parameters().addAttribute(defaultAttributes)
                    .addAttribute(LEGACY_ATTRIBUTES)
                    .addAttribute(attributes) :
                    new Parameters().addAttribute(defaultAttributes).addAttribute(attributes));
        }

        @Override
        protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
            super.populateModel(operation, model);
            // If a filter attribute, update the filter-spec attribute
            if (model.hasDefined(CommonAttributes.FILTER.getName())) {
                final String filterSpec = Filters.filterToFilterSpec(model.get(FILTER.getName()));
                final ModelNode filterSpecValue = (filterSpec.isEmpty() ? new ModelNode() : new ModelNode(filterSpec));
                // Undefine the filter and set the filter-spec
                model.get(FILTER.getName()).set(new ModelNode());
                model.get(FILTER_SPEC.getName()).set(filterSpecValue);
            }
        }

        @Override
        protected void performRuntime(final OperationContext contextX, final ModelNode operationX, final ModelNode model) throws OperationFailedException {
            // We do this in a new step to ensure other resources are added first
            contextX.addStep((context, operation) -> {
                final var name = context.getCurrentAddressValue();
                final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());

                // Create the handler
                final T handler = createHandler(context, operation, model, configuration, name);
                final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();
                // Configure the default properties
                if (handler instanceof ExtHandler) {
                    ((ExtHandler) handler).setEnabled(enabled);
                } else {
                    // On add, only disable the handler if it should be disabled
                    if (!enabled) {
                        Handlers.disableHandler(configuration, handler, name);
                    }
                }
                try {
                    handler.setEncoding(ENCODING.resolveModelAttribute(context, model).asStringOrNull());
                } catch (UnsupportedEncodingException e) {
                    // TODO (jrp) this should happen, but should we log or throw this?
                    throw new OperationFailedException(e);
                }
                // TODO (jrp) we need to verify this always works
                handler.setFilter(Filters.createFilter(configuration, FILTER_SPEC.resolveModelAttribute(context, model)
                        .asStringOrNull()));

                // First check we need to check the named-formatter
                if (model.hasDefined(NAMED_FORMATTER.getName())) {
                    final var formatterName = NAMED_FORMATTER.resolveModelAttribute(context, model)
                            .asString();
                    final var formatter = configuration.getFormatter(formatterName);
                    if (formatter == null) {
                        throw Logging.createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(formatterName));
                    }
                    handler.setFormatter(formatter);
                } else if (model.has(FORMATTER.getName())) {
                    final var formatter = new PatternFormatter(FORMATTER.resolveModelAttribute(context, model)
                            .asString());
                    configuration.addFormatter(PatternFormatterResourceDefinition.getDefaultFormatterName(name), () -> formatter);
                    handler.setFormatter(formatter);
                }
                handler.setLevel(Level.parse(LEVEL.resolveModelAttribute(context, model).asString()));

                performRuntime(context, operation, model, handler, configuration, name);
                // Add the handler
                configuration.addHandler(name, () -> handler);
            }, OperationContext.Stage.RUNTIME);
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) we can simply remove and close the handler, but we need to ensure it was not assigned to a logger/handler
            super.rollbackRuntime(context, operation, resource);
        }

        void performRuntime(OperationContext context, ModelNode operation, ModelNode model, T handler,
                            ContextConfiguration configuration, String name) throws OperationFailedException {

        }

        abstract T createHandler(OperationContext context, ModelNode operation, ModelNode model,
                                 ContextConfiguration configuration, String name) throws OperationFailedException;
    }

    static class LogHandlerRemoveStepHandler extends AbstractRemoveStepHandler {
        private final OperationStepHandler addHandler;

        LogHandlerRemoveStepHandler(final OperationStepHandler addHandler) {
            this.addHandler = addHandler;
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final var name = context.getCurrentAddressValue();
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var handler = configuration.removeHandler(name);
            // TODO (jrp) we don't actually want to close this unless it was an explicit remove operation.
            if (handler != null) {
                handler.get().close();
            }
            if (configuration.hasFormatter(PatternFormatterResourceDefinition.getDefaultFormatterName(name))) {
                configuration.removeFormatter(PatternFormatterResourceDefinition.getDefaultFormatterName(name));
            }
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            // TODO (jrp) is this right? Will it attempt to create the actual resource again?
            addHandler.execute(context, operation);
        }
    }

    abstract static class AbstractHandlerWriteStepHandler<T extends Handler> extends AbstractWriteAttributeHandler<T> {
        protected AbstractHandlerWriteStepHandler(final boolean includeLegacyAttributes, final AttributeDefinition... definitions) {
            super(includeLegacyAttributes ? Stream.concat(Stream.of(definitions), Stream.of(LEGACY_ATTRIBUTES))
                    .collect(Collectors.toList()) : List.of(definitions));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<T> handbackHolder) throws OperationFailedException {
            // This needs to be executed in a new step to ensure it happens last
            context.addStep((runtimeContext, runtimeOperation) -> {
                final var configuration = Logging.getContextConfiguration(runtimeContext.getCurrentAddress());
                final var handler = (T) configuration.getHandler(runtimeContext.getCurrentAddressValue());
                // Configure the default properties
                if (attributeName.equals(ENABLED.getName())) {
                    if (handler instanceof ExtHandler) {
                        ((ExtHandler) handler).setEnabled(resolvedValue.asBoolean());
                    } else {
                        if (resolvedValue.asBoolean()) {
                            Handlers.enableHandler(configuration, handler, runtimeContext.getCurrentAddressValue());
                        } else {
                            Handlers.disableHandler(configuration, handler, runtimeContext.getCurrentAddressValue());
                        }
                    }
                } else if (attributeName.equals(ENCODING.getName())) {
                    try {
                        handler.setEncoding(resolvedValue.asStringOrNull());
                    } catch (UnsupportedEncodingException e) {
                        // TODO (jrp) this should happen, but should we log or throw this?
                        throw new OperationFailedException(e);
                    }
                } else if (attributeName.equals(FILTER.getName())) {

                    if (resolvedValue.isDefined()) {
                        // TODO (jrp) we need to verify this always works
                        handler.setFilter(Filters.createFilter(configuration, Filters.filterToFilterSpec(resolvedValue)));
                    } else {
                        handler.setFilter(null);
                    }
                } else if (attributeName.equals(FILTER_SPEC.getName())) {

                    if (resolvedValue.isDefined()) {
                        // TODO (jrp) we need to verify this always works
                        handler.setFilter(Filters.createFilter(configuration, resolvedValue.asStringOrNull()));
                    } else {
                        handler.setFilter(null);
                    }
                } else if (attributeName.equals(FORMATTER.getName())) {
                    // TODO (jrp) this should always be defined because there is a default
                    if (resolvedValue.isDefined()) {
                        final var formatter = new PatternFormatter(resolvedValue.asString());
                        handler.setFormatter(formatter);
                        configuration.addFormatter(PatternFormatterResourceDefinition.getDefaultFormatterName(context.getCurrentAddressValue()), () -> formatter);
                    } else {
                        // TODO (jrp) what do we do here? If we have used a named-formatter then we don't want to override it
                        // TODO (jrp) for now we'll be safe and require a reload
                        runtimeContext.reloadRequired();
                    }
                } else if (attributeName.equals(NAMED_FORMATTER.getName())) {
                    final Formatter formatter;
                    if (resolvedValue.isDefined()) {
                        formatter = configuration.getFormatter(resolvedValue.asString());
                        // If we previously used a formatter based on the formatter attribute, we need to remove it
                        final var name = PatternFormatterResourceDefinition.getDefaultFormatterName(context.getCurrentAddressValue());
                        if (configuration.hasFormatter(name)) {
                            configuration.removeFormatter(name);
                        }
                    } else {
                        formatter = createDefaultFormatter();
                        configuration.addFormatter(PatternFormatterResourceDefinition.getDefaultFormatterName(context.getCurrentAddressValue()), () -> formatter);
                    }
                    if (formatter == null) {
                        throw Logging.createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(runtimeContext.getCurrentAddressValue()));
                    }
                    handler.setFormatter(formatter);
                } else if (attributeName.equals(LEVEL.getName())) {
                    handler.setLevel(Level.parse(resolvedValue.asString()));
                }
                applyUpdateToRuntime(runtimeContext, attributeName, resolvedValue, currentValue, configuration, handler);

                runtimeContext.completeStep(new OperationContext.RollbackHandler() {
                    @Override
                    public void handleRollback(OperationContext context, ModelNode operation) {
                        final var attributeDefinition = getAttributeDefinition(attributeName);
                        final var defaultValue = attributeDefinition.getDefaultValue();
                        try {
                            ModelNode valueToRestore = context.resolveExpressions(currentValue);
                            if (!valueToRestore.isDefined() && defaultValue != null) {
                                valueToRestore = defaultValue;
                            }
                            revertUpdateToRuntime(context, operation, attributeName, valueToRestore, resolvedValue, handler);
                        } catch (Exception e) {
                            MGMT_OP_LOGGER.errorRevertingOperation(e, getClass().getSimpleName(),
                                    operation.require(ModelDescriptionConstants.OP).asString(),
                                    context.getCurrentAddress());
                        }
                        if (requiresReload(attributeName)) {
                            if (attributeDefinition.getFlags().contains(AttributeAccess.Flag.RESTART_JVM)) {
                                context.revertRestartRequired();
                            } else {
                                context.revertReloadRequired();
                            }

                        }
                    }
                });
            }, OperationContext.Stage.RUNTIME);
            return requiresReload(attributeName);
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation,
                                             final String attributeName, final ModelNode valueToRestore,
                                             final ModelNode valueToRevert, final T handback) throws OperationFailedException {
            // TODO (jrp) abstract this a bit more?
            applyUpdateToRuntime(context, operation, attributeName, valueToRestore, valueToRevert, new HandbackHolder<>());
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

        boolean requiresReload(final String attributeName) {
            return false;
        }

        abstract void applyUpdateToRuntime(OperationContext context, String attributeName, ModelNode resolvedValue,
                                           ModelNode currentValue, ContextConfiguration configuration, T handler) throws OperationFailedException;

        Formatter createDefaultFormatter() {
            return new PatternFormatter(FORMATTER.getDefaultValue().asString());
        }
    }

    static class LegacyUpdateStepHandler<T extends Handler> extends AbstractHandlerWriteStepHandler<T> {
        private final AbstractHandlerWriteStepHandler<T> writeStepHandler;
        private final Map<String, AttributeDefinition> attributes;

        protected LegacyUpdateStepHandler(final AbstractHandlerWriteStepHandler<T> writeStepHandler, final AttributeDefinition... attributes) {
            super(false, attributes);
            this.writeStepHandler = writeStepHandler;
            this.attributes = Stream.of(attributes)
                    .collect(Collectors.toMap(AttributeDefinition::getName, a -> a));
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            // Create a new synthetic operation and pass it on to the write handler
            final ModelNode syntheticOp = operation.clone();
            // Find the attribute name
            final var foundAttribute = findAttribute(syntheticOp);
            if (foundAttribute.isEmpty()) {
                // TODO (jrp) i18n
                throw new OperationFailedException("Failed to find property name in attributes: " + attributes.keySet());
            }
            final var attribute = foundAttribute.get();
            syntheticOp.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
            // The filter/filter-spec is special and needs to be handled differently
            if (attribute == FILTER) {
                syntheticOp.get(ModelDescriptionConstants.NAME).set(FILTER_SPEC.getName());
                syntheticOp.get(ModelDescriptionConstants.VALUE)
                        .set(Filters.filterToFilterSpec(resolveValue(context, operation, attribute)));
            } else {
                syntheticOp.get(ModelDescriptionConstants.NAME).set(attribute.getName());
                syntheticOp.get(ModelDescriptionConstants.VALUE).set(resolveValue(context, operation, attribute));
            }
            syntheticOp.remove(attribute.getName());
            super.execute(context, syntheticOp);
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final T handback) throws OperationFailedException {
            writeStepHandler.revertUpdateToRuntime(context, operation, attributeName, valueToRestore, valueToRevert, handback);
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final ContextConfiguration configuration, final T handler) throws OperationFailedException {
            writeStepHandler.applyUpdateToRuntime(context, attributeName, resolvedValue, currentValue, configuration, handler);
        }

        Optional<AttributeDefinition> findAttribute(final ModelNode op) {
            return op.asPropertyList()
                    .stream()
                    .filter(property -> attributes.containsKey(property.getName()))
                    .map(property -> attributes.get(property.getName()))
                    .findAny();
        }

        ModelNode resolveValue(final OperationContext context, final ModelNode operation, final AttributeDefinition attribute) {
            return operation.get(attribute.getName());
        }
    }

    static class EnableDisableHandlerStepHandler<T extends Handler> extends LegacyUpdateStepHandler<T> {

        protected EnableDisableHandlerStepHandler(final AbstractHandlerWriteStepHandler<T> writeStepHandler) {
            super(writeStepHandler, ENABLED);
        }

        @Override
        Optional<AttributeDefinition> findAttribute(final ModelNode op) {
            return Optional.of(ENABLED);
        }

        @Override
        ModelNode resolveValue(final OperationContext context, final ModelNode operation, final AttributeDefinition attribute) {
            return context.getCurrentOperationName().equals(DISABLE) ? ModelNode.FALSE : ModelNode.TRUE;
        }
    }

    static class AbstractHandlerTransformerDefinition extends TransformerResourceDefinition {

        AbstractHandlerTransformerDefinition(PathElement pathElement) {
            super(pathElement);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion,
                                         final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                         final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            if (modelVersion.hasTransformers()) {
                final PathElement pathElement = getPathElement();
                final ResourceTransformationDescriptionBuilder resourceBuilder = rootResourceBuilder.addChildResource(pathElement);
                final ResourceTransformationDescriptionBuilder loggingProfileResourceBuilder = loggingProfileBuilder.addChildResource(pathElement);
                registerResourceTransformers(modelVersion, resourceBuilder, loggingProfileResourceBuilder);
            }
        }

        /**
         * Register the transformers for the resource.
         *
         * @param modelVersion          the model version we're registering
         * @param resourceBuilder       the builder for the resource
         * @param loggingProfileBuilder the builder for the logging profile
         */
        void registerResourceTransformers(final KnownModelVersion modelVersion,
                                          final ResourceTransformationDescriptionBuilder resourceBuilder,
                                          final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }


    /**
     * Creates the default {@linkplain org.jboss.as.controller.SimpleResourceDefinition.Parameters parameters} for
     * creating the source.
     *
     * @param path       the resource path
     * @param addHandler the handler used to add the resource
     *
     * @return the default parameters
     */
    static Parameters createParameters(final PathElement path, final OperationStepHandler addHandler) {
        return createParameters(path, addHandler, new LogHandlerRemoveStepHandler(addHandler));
    }

    /**
     * Creates the default {@linkplain org.jboss.as.controller.SimpleResourceDefinition.Parameters parameters} for
     * creating the source.
     *
     * @param path          the resource path
     * @param addHandler    the handler used to add the resource
     * @param removeHandler the handler used to remove the resource
     *
     * @return the default parameters
     */
    static Parameters createParameters(final PathElement path, final OperationStepHandler addHandler, final OperationStepHandler removeHandler) {
        return new Parameters(path, LoggingExtension.getResourceDescriptionResolver(path.getKey()))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setCapabilities(Capabilities.HANDLER_CAPABILITY);
    }
}
