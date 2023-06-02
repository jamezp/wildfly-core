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

import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;
import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
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
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.wildfly.core.logmanager.ObjectBuilder;
import org.wildfly.core.logmanager.ObjectUpdater;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomFormatterResourceDefinition extends SimpleResourceDefinition {
    public static final String NAME = "custom-formatter";

    public static final ObjectTypeAttributeDefinition CUSTOM_FORMATTER = ObjectTypeAttributeDefinition.Builder.of(NAME,
                    CLASS,
                    MODULE,
                    PROPERTIES)
            .setAllowExpression(false)
            .setRequired(false)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws
                        XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        MODULE.marshallAsAttribute(resourceModel, writer);
                        CLASS.marshallAsAttribute(resourceModel, writer);
                        if (resourceModel.hasDefined(PROPERTIES.getName())) {
                            PROPERTIES.marshallAsElement(resourceModel, writer);
                        }
                        writer.writeEndElement();
                    }
                }

                @Override
                public boolean isMarshallable(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault) {
                    return resourceModel.hasDefined(CLASS.getName());
                }
            })
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = {
            CLASS,
            MODULE,
            PROPERTIES
    };


    /**
     * A step handler to add a custom formatter
     */
    private static final OperationStepHandler ADD = new AbstractAddStepHandler(ATTRIBUTES) {
        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws
                OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final ModelNode moduleNameNode = MODULE.resolveModelAttribute(context, model);
            final String moduleName = moduleNameNode.isDefined() ? moduleNameNode.asString() : null;
            final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
            final ContextConfiguration configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final ObjectBuilder<Formatter> formatterBuilder = ObjectBuilder.of(configuration,
                            Formatter.class,
                            className)
                    .setModuleName(moduleName);

            if (properties.isDefined()) {
                for (var property : properties.asPropertyList()) {
                    formatterBuilder.addProperty(property.getName(), property.getValue().asString());
                }
            }

            configuration.addFormatter(name, formatterBuilder.buildLazy());
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) we need to implement this
            super.rollbackRuntime(context, operation, resource);
        }
    };

    private static final OperationStepHandler WRITE = new AbstractWriteAttributeHandler<Formatter>(ATTRIBUTES) {

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<Formatter> handbackHolder) throws
                OperationFailedException {
            final var name = context.getCurrentAddressValue();
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final Formatter formatter = configuration.getFormatter(name);
            if (formatter == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(name));
            }
            handbackHolder.setHandback(formatter);
            final var updater = ObjectUpdater.of(configuration, Formatter.class, formatter);
            boolean reloadRequired = true;
            if (PROPERTIES.getName().equals(attributeName)) {
                final List<String> changedProperties = new ArrayList<>();
                if (resolvedValue.isDefined()) {
                    for (var property : resolvedValue.asPropertyList()) {
                        changedProperties.add(property.getName());
                        updater.addProperty(property.getName(), property.getValue().asString());
                    }
                }
                // Find the properties we need to remove
                if (currentValue.hasDefined(PROPERTIES.getName())) {
                    currentValue.asPropertyList().stream()
                            .map(Property::getName)
                            .filter(propertyName -> !changedProperties.contains(propertyName))
                            .forEach(updater::clearProperty);
                }
                reloadRequired = false;
            }
            updater.update();
            // Writing a class attribute or module will require the previous formatter to be removed and a new formatter
            // added. This also would require each logger or handler that has the formatter assigned to reassign the
            // formatter. The configuration API does not handle this so a reload will be required.
            return reloadRequired;
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final Formatter handback) throws
                OperationFailedException {
            // If the handback is null, we can't reset anything, but also nothing should have been set
            if (handback != null) {
                final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
                final var updater = ObjectUpdater.of(configuration, Formatter.class, handback);
                if (PROPERTIES.getName().equals(attributeName)) {
                    if (valueToRestore.isDefined()) {
                        for (Property property : valueToRestore.asPropertyList()) {
                            updater.addProperty(property.getName(), property.getValue().asString());
                        }
                    }
                }
            }

        }
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
            // TODO (jrp) how do we do this
        }
    };

    public static final CustomFormatterResourceDefinition INSTANCE = new CustomFormatterResourceDefinition();

    private CustomFormatterResourceDefinition() {
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
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }
}
