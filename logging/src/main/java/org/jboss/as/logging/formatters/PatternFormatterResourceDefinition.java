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

package org.jboss.as.logging.formatters;

import static org.jboss.as.logging.Logging.createOperationFailure;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.validators.RegexValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.formatters.PatternFormatter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PatternFormatterResourceDefinition extends SimpleResourceDefinition {

    private static final String COLOR_MAP_VALIDATION_PATTERN = "^((severe|fatal|error|warn|warning|info|debug|trace|config|fine|finer|finest|):(clear|black|green|red|yellow|blue|magenta|cyan|white|brightblack|brightred|brightgreen|brightblue|brightyellow|brightmagenta|brightcyan|brightwhite|)(,(?!$)|$))*$";

    public static final String NAME = "pattern-formatter";

    public static final String DEFAULT_FORMATTER_SUFFIX = "-wfcore-pattern-formatter";

    public static String getDefaultFormatterName(String name) {
        return name + DEFAULT_FORMATTER_SUFFIX;
    }

    // Pattern formatter options
    public static final SimpleAttributeDefinition COLOR_MAP = SimpleAttributeDefinitionBuilder.create("color-map", ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(false)
            .setValidator(new RegexValidator(ModelType.STRING, true, true, COLOR_MAP_VALIDATION_PATTERN))
            .build();

    public static final SimpleAttributeDefinition PATTERN = SimpleAttributeDefinitionBuilder.create("pattern", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n"))
            .build();

    public static final ObjectTypeAttributeDefinition PATTERN_FORMATTER = ObjectTypeAttributeDefinition.Builder.of(NAME, PATTERN, COLOR_MAP)
            .setAllowExpression(false)
            .setRequired(false)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    // We always want to marshal the element
                    writer.writeStartElement(attribute.getXmlName());
                    // We also need to always marshal the pattern has it's a required attribute in the XML.
                    final String pattern;
                    if (resourceModel.hasDefined(PATTERN.getName())) {
                        pattern = resourceModel.get(PATTERN.getName()).asString();
                    } else {
                        pattern = PATTERN.getDefaultValue().asString();
                    }
                    writer.writeAttribute(PATTERN.getXmlName(), pattern);
                    // Only marshal the color-map if defined as this is a newer attribute.
                    if (resourceModel.hasDefined(COLOR_MAP.getName())) {
                        final String colorMap = resourceModel.get(COLOR_MAP.getName()).asString();
                        writer.writeAttribute(COLOR_MAP.getXmlName(), colorMap);
                    }
                    writer.writeEndElement();
                }
            })
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final SimpleAttributeDefinition[] ATTRIBUTES = {
            COLOR_MAP,
            PATTERN,
    };


    /**
     * A step handler to add a pattern formatter
     */
    private static final OperationStepHandler ADD = new AbstractAddStepHandler(ATTRIBUTES) {
        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            if (name.endsWith(DEFAULT_FORMATTER_SUFFIX)) {
                throw LoggingLogger.ROOT_LOGGER.illegalFormatterName();
            }
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            LoggingLogger.ROOT_LOGGER.tracef("Adding formatter '%s' at '%s'", name, context.getCurrentAddress());
            final var pattern = PATTERN.resolveModelAttribute(context, model).asString();
            final PatternFormatter patternFormatter;
            if (model.hasDefined(COLOR_MAP.getName())) {
                patternFormatter = new PatternFormatter(pattern, COLOR_MAP.resolveModelAttribute(context, model)
                        .asString());
            } else {
                patternFormatter = new PatternFormatter(pattern);
            }
            configuration.addFormatter(name, () -> patternFormatter);
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) implement this
            super.rollbackRuntime(context, operation, resource);
        }
    };

    private static final OperationStepHandler WRITE = new AbstractWriteAttributeHandler<PatternFormatter>(ATTRIBUTES) {

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<PatternFormatter> handbackHolder) throws OperationFailedException {
            return applyUpdate(context, attributeName, resolvedValue);
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final PatternFormatter handback) throws OperationFailedException {
            applyUpdate(context, attributeName, valueToRestore);
        }

        private boolean applyUpdate(final OperationContext context, final String attributeName, final ModelNode value) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            LoggingLogger.ROOT_LOGGER.tracef("Updating formatter '%s' at '%s'", name, context.getCurrentAddress());
            final var formatter = configuration.getFormatter(name);
            if (!(formatter instanceof PatternFormatter)) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(name));
            }
            final var patternFormatter = (PatternFormatter) formatter;
            if (attributeName.equals(PATTERN.getName())) {
                patternFormatter.setPattern(value.asString());
            } else if (name.equals(COLOR_MAP.getName())) {
                patternFormatter.setColors(value.asString());
            }
            return false;
        }
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new AbstractRemoveStepHandler() {
        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            // This should not be in-use because the capability check should fail if it is
            configuration.removeFormatter(context.getCurrentAddressValue());
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            // TODO (jrp) need to implement
            super.recoverServices(context, operation, model);
        }
    };

    public static final PatternFormatterResourceDefinition INSTANCE = new PatternFormatterResourceDefinition();

    public PatternFormatterResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setCapabilities(Capabilities.FORMATTER_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, WRITE);
        }
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }
}
