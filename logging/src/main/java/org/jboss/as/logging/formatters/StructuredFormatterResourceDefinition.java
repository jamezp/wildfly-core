/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.formatters;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logmanager.PropertyValues;
import org.jboss.logmanager.formatters.StructuredFormatter;
import org.wildfly.core.logmanager.WildFlyContextConfiguration;

/**
 * An abstract resource definition for {@link org.jboss.logmanager.formatters.StructuredFormatter}'s.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class StructuredFormatterResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition DATE_FORMAT = SimpleAttributeDefinitionBuilder.create("date-format", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final SimpleAttributeDefinition EXCEPTION_OUTPUT_TYPE = SimpleAttributeDefinitionBuilder.create("exception-output-type", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("detailed"))
            .setValidator(new StringAllowedValuesValidator("detailed", "formatted", "detailed-and-formatted"))
            .build();

    private static final SimpleAttributeDefinition EXCEPTION = SimpleAttributeDefinitionBuilder.create("exception", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_CAUSED_BY = SimpleAttributeDefinitionBuilder.create("exception-caused-by", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_CIRCULAR_REFERENCE = SimpleAttributeDefinitionBuilder.create("exception-circular-reference", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME = SimpleAttributeDefinitionBuilder.create("exception-frame", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_CLASS = SimpleAttributeDefinitionBuilder.create("exception-frame-class", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_LINE = SimpleAttributeDefinitionBuilder.create("exception-frame-line", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_METHOD = SimpleAttributeDefinitionBuilder.create("exception-frame-method", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAMES = SimpleAttributeDefinitionBuilder.create("exception-frames", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_MESSAGE = SimpleAttributeDefinitionBuilder.create("exception-message", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_REFERENCE_ID = SimpleAttributeDefinitionBuilder.create("exception-reference-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_SUPPRESSED = SimpleAttributeDefinitionBuilder.create("exception-suppressed", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_TYPE = SimpleAttributeDefinitionBuilder.create("exception-type", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition HOST_NAME = SimpleAttributeDefinitionBuilder.create("host-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LOGGER_CLASS_NAME = SimpleAttributeDefinitionBuilder.create("logger-class-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LOGGER_NAME = SimpleAttributeDefinitionBuilder.create("logger-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition MDC = SimpleAttributeDefinitionBuilder.create("mdc", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition MESSAGE = SimpleAttributeDefinitionBuilder.create("message", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition NDC = SimpleAttributeDefinitionBuilder.create("ndc", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition PROCESS_ID = SimpleAttributeDefinitionBuilder.create("process-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition PROCESS_NAME = SimpleAttributeDefinitionBuilder.create("process-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition RECORD = SimpleAttributeDefinitionBuilder.create("record", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SEQUENCE = SimpleAttributeDefinitionBuilder.create("sequence", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_CLASS_NAME = SimpleAttributeDefinitionBuilder.create("source-class-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_FILE_NAME = SimpleAttributeDefinitionBuilder.create("source-file-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_LINE_NUMBER = SimpleAttributeDefinitionBuilder.create("source-line-number", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_METHOD_NAME = SimpleAttributeDefinitionBuilder.create("source-method-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_MODULE_NAME = SimpleAttributeDefinitionBuilder.create("source-module-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_MODULE_VERSION = SimpleAttributeDefinitionBuilder.create("source-module-version", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition STACK_TRACE = SimpleAttributeDefinitionBuilder.create("stack-trace", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition THREAD_ID = SimpleAttributeDefinitionBuilder.create("thread-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition THREAD_NAME = SimpleAttributeDefinitionBuilder.create("thread-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition TIMESTAMP = SimpleAttributeDefinitionBuilder.create("timestamp", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final ObjectTypeAttributeDefinition KEY_OVERRIDES = ObjectTypeAttributeDefinition.create("key-overrides",
                    EXCEPTION,
                    EXCEPTION_CAUSED_BY,
                    EXCEPTION_CIRCULAR_REFERENCE,
                    EXCEPTION_FRAME,
                    EXCEPTION_FRAME_CLASS,
                    EXCEPTION_FRAME_LINE,
                    EXCEPTION_FRAME_METHOD,
                    EXCEPTION_FRAMES,
                    EXCEPTION_MESSAGE,
                    EXCEPTION_REFERENCE_ID,
                    EXCEPTION_SUPPRESSED,
                    EXCEPTION_TYPE,
                    HOST_NAME,
                    LEVEL,
                    LOGGER_CLASS_NAME,
                    LOGGER_NAME,
                    MDC,
                    MESSAGE,
                    NDC,
                    PROCESS_ID,
                    PROCESS_NAME,
                    RECORD,
                    SEQUENCE,
                    SOURCE_CLASS_NAME,
                    SOURCE_FILE_NAME,
                    SOURCE_LINE_NUMBER,
                    SOURCE_METHOD_NAME,
                    SOURCE_MODULE_NAME,
                    SOURCE_MODULE_VERSION,
                    STACK_TRACE,
                    THREAD_ID,
                    THREAD_NAME,
                    TIMESTAMP
            )
            // This is done as the StructuredFormatter will need to be reconstructed even though there is no real
            // service. This could be done without requiring a restart, but making a change to a key name may not be
            // desired until both the target consumer of the log messages and the container are restarted.
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleMapAttributeDefinition META_DATA = new SimpleMapAttributeDefinition.Builder("meta-data", ModelType.STRING, true)
            .setAttributeMarshaller(new AttributeMarshallers.PropertiesAttributeMarshaller("meta-data", "property", true))
            .build();

    public static final SimpleAttributeDefinition PRETTY_PRINT = SimpleAttributeDefinitionBuilder.create("pretty-print", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition PRINT_DETAILS = SimpleAttributeDefinitionBuilder.create("print-details", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition RECORD_DELIMITER = SimpleAttributeDefinitionBuilder.create("record-delimiter", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("\n"))
            .build();

    public static final SimpleAttributeDefinition ZONE_ID = SimpleAttributeDefinitionBuilder.create("zone-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    static final AttributeDefinition[] DEFAULT_ATTRIBUTES = {
            DATE_FORMAT,
            EXCEPTION_OUTPUT_TYPE,
            KEY_OVERRIDES,
            META_DATA,
            PRETTY_PRINT,
            PRINT_DETAILS,
            RECORD_DELIMITER,
            ZONE_ID,
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new AbstractRemoveStepHandler() {
        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            // This should not be in-use because the capability check should fail if it is
            configuration.removeFormatter(context.getCurrentAddressValue());
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws
                OperationFailedException {
            // TODO (jrp) needs to be implemented
            super.recoverServices(context, operation, model);
        }
    };

    private final WriteStructuredFormatterStepHandler<?> writeHandler;

    StructuredFormatterResourceDefinition(final PathElement pathElement, final String descriptionPrefix,
                                          final AddStructuredFormatterStepHandler<?> addHandler,
                                          final WriteStructuredFormatterStepHandler<?> writeHandler) {
        super(
                new Parameters(pathElement, LoggingExtension.getResourceDescriptionResolver(descriptionPrefix))
                        .setAddHandler(addHandler)
                        .setRemoveHandler(REMOVE)
                        .setCapabilities(Capabilities.FORMATTER_CAPABILITY)
        );
        this.writeHandler = writeHandler;
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        if (writeHandler == null || writeHandler.attributes == null) {
            return;
        }
        for (AttributeDefinition attribute : writeHandler.attributes) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }

    @Override
    public void registerChildren(final ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
    }

    static class StructuredFormatterTransformerDefinition extends TransformerResourceDefinition {

        public StructuredFormatterTransformerDefinition(PathElement pathElement) {
            super(pathElement);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_5_0_0:
                    rootResourceBuilder.rejectChildResource(getPathElement());
                    loggingProfileBuilder.rejectChildResource(getPathElement());
                    break;
            }
        }
    }

    private static String modelValueToMetaData(final ModelNode metaData) {
        if (metaData.getType() != ModelType.OBJECT) {
            return null;
        }
        final List<Property> properties = metaData.asPropertyList();
        final StringBuilder result = new StringBuilder();
        final Iterator<Property> iterator = properties.iterator();
        while (iterator.hasNext()) {
            final Property property = iterator.next();
            PropertyValues.escapeKey(result, property.getName());
            result.append('=');
            final ModelNode value = property.getValue();
            if (value.isDefined()) {
                PropertyValues.escapeValue(result, value.asString());
            }
            if (iterator.hasNext()) {
                result.append(',');
            }
        }
        return result.toString();
    }

    private static StructuredFormatter.ExceptionOutputType resolveExceptionType(final ModelNode value) throws OperationFailedException {
        final String exceptionType = value.asString();
        if ("detailed".equals(exceptionType)) {
            return StructuredFormatter.ExceptionOutputType.DETAILED;
        } else if ("formatted".equals(exceptionType)) {
            return StructuredFormatter.ExceptionOutputType.FORMATTED;
        } else if ("detailed-and-formatted".equals(exceptionType)) {
            return StructuredFormatter.ExceptionOutputType.DETAILED_AND_FORMATTED;
        }
        // Should never be hit
        throw LoggingLogger.ROOT_LOGGER.invalidExceptionOutputType(exceptionType);
    }

    static class AddStructuredFormatterStepHandler<T extends StructuredFormatter> extends AbstractAddStepHandler {
        private final Function<String, T> formatCreator;

        AddStructuredFormatterStepHandler(final Function<String, T> formatCreator, final AttributeDefinition[] attributes) {
            super(attributes);
            this.formatCreator = formatCreator;
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            if (name.endsWith(PatternFormatterResourceDefinition.DEFAULT_FORMATTER_SUFFIX)) {
                throw LoggingLogger.ROOT_LOGGER.illegalFormatterName();
            }
            final var configuration = WildFlyContextConfiguration.getInstance(Logging.getLoggingProfileName(context.getCurrentAddress()));
            LoggingLogger.ROOT_LOGGER.tracef("Adding formatter '%s' at '%s'", name, context.getCurrentAddress());

            // Process the attributes
            String keyOverrides = "";
            if (model.hasDefined(KEY_OVERRIDES.getName())) {
                keyOverrides = modelValueToMetaData(KEY_OVERRIDES.resolveModelAttribute(context, model));
            }
            final T formatter = formatCreator.apply(keyOverrides);

            final String metaData = modelValueToMetaData(META_DATA.resolveModelAttribute(context, model));
            if (metaData != null) {
                formatter.setMetaData(metaData);
            }

            final var dateFormat = DATE_FORMAT.resolveModelAttribute(context, model);
            if (dateFormat.isDefined()) {
                formatter.setDateFormat(dateFormat.asString());
            }

            formatter.setExceptionOutputType(resolveExceptionType(EXCEPTION_OUTPUT_TYPE.resolveModelAttribute(context, model)));
            formatter.setPrintDetails(PRETTY_PRINT.resolveModelAttribute(context, model).asBoolean());
            formatter.setPrintDetails(PRINT_DETAILS.resolveModelAttribute(context, model).asBoolean());
            formatter.setRecordDelimiter(RECORD_DELIMITER.resolveModelAttribute(context, model).asString());

            final var zoneId = ZONE_ID.resolveModelAttribute(context, model);
            if (zoneId.isDefined()) {
                formatter.setZoneId(zoneId.asString());
            }

            // Process any additional attributes
            applyAdditionalAttributes(context, operation, model, formatter);
            configuration.addFormatter(name, () -> formatter);
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) needs to be implemented
            super.rollbackRuntime(context, operation, resource);
        }

        void applyAdditionalAttributes(final OperationContext context, final ModelNode operation, final ModelNode model, final T formatter) throws OperationFailedException {

        }
    }

    static class WriteStructuredFormatterStepHandler<T extends StructuredFormatter> extends AbstractWriteAttributeHandler<T> {
        private final AttributeDefinition[] attributes;

        WriteStructuredFormatterStepHandler(final AttributeDefinition[] attributes) {
            super(attributes);
            this.attributes = attributes;
        }

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName,
                                               final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<T> handbackHolder) throws OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final var name = context.getCurrentAddressValue();
            final var foundFormatter = configuration.getFormatter(name);
            if (!(foundFormatter instanceof StructuredFormatter)) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(name));
            }
            @SuppressWarnings("unchecked")
            final var formatter = (T) foundFormatter;
            handbackHolder.setHandback(formatter);
            return applyUpdateToRuntime(context, attributeName, resolvedValue, formatter);
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final T handback) throws OperationFailedException {
            applyUpdateToRuntime(context, attributeName, valueToRestore, handback);
        }

        boolean applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final T formatter) throws OperationFailedException {
            if (attributeName.equals(META_DATA.getName())) {
                final String metaData = modelValueToMetaData(resolvedValue);
                if (metaData != null) {
                    formatter.setMetaData(metaData);
                }
            } else if (attributeName.equals(KEY_OVERRIDES.getName())) {
                // Require a restart of the resource
                return true;
            } else if (attributeName.equals(DATE_FORMAT.getName())) {
                if (resolvedValue.isDefined()) {
                    formatter.setDateFormat(resolvedValue.asString());
                } else {
                    formatter.setDateFormat(null);
                }
            } else if (attributeName.equals(EXCEPTION_OUTPUT_TYPE.getName())) {
                formatter.setExceptionOutputType(resolveExceptionType(resolvedValue));
            } else if (attributeName.equals(PRETTY_PRINT.getName())) {
                formatter.setPrintDetails(resolvedValue.asBoolean());
            } else if (attributeName.equals(PRINT_DETAILS.getName())) {
                formatter.setPrintDetails(resolvedValue.asBoolean());
            } else if (attributeName.equals(RECORD_DELIMITER.getName())) {
                formatter.setRecordDelimiter(resolvedValue.asString());
            } else if (attributeName.equals(ZONE_ID.getName())) {
                formatter.setZoneId(resolvedValue.asStringOrNull());
            }
            return applyAdditionalAttributes(context, attributeName, resolvedValue, formatter);
        }

        boolean applyAdditionalAttributes(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final T formatter) throws OperationFailedException {
            return false;
        }
    }
}
