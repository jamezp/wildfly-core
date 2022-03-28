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

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.logging.Formatter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.Reflection;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomFormatterResourceDefinition extends TransformerResourceDefinition {
    public static final String NAME = "custom-formatter";

    public static final ObjectTypeAttributeDefinition CUSTOM_FORMATTER = ObjectTypeAttributeDefinition.Builder.of(NAME, CLASS, MODULE, PROPERTIES)
            .setAllowExpression(false)
            .setRequired(false)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
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
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final ModelNode moduleNameNode = MODULE.resolveModelAttribute(context, model);
            // TODO (jrp) is this right?
            final String moduleName = moduleNameNode.isDefined() ? moduleNameNode.asString() : "org.jboss.as.logging";
            final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);

            final CapabilityServiceTarget target = context.getCapabilityServiceTarget();
            final CapabilityServiceBuilder<?> builder = target.addCapability(Capabilities.FORMATTER_CAPABILITY);
            final Consumer<Formatter> formatterConsumer = builder.provides(Capabilities.FORMATTER_CAPABILITY);

            builder.setInstance(new Service() {
                        @Override
                        public void start(final StartContext context) throws StartException {
                            try {
                                final Formatter formatter = Reflection.createInstance(Formatter.class, moduleName, className);
                                if (properties.isDefined()) {
                                    for (Property property : properties.asPropertyList()) {
                                        Reflection.setProperty(formatter, property.getName(), property.getValue()
                                                .asString());
                                    }
                                }
                                formatterConsumer.accept(formatter);
                            } catch (ModuleLoadException | ClassNotFoundException | NoSuchMethodException |
                                    InvocationTargetException | InstantiationException | IllegalAccessException e) {
                                // TODO (jrp) i18n
                                throw new StartException(e);
                            }
                        }

                        @Override
                        public void stop(final StopContext context) {
                            formatterConsumer.accept(null);
                        }
                    })
                    .install();
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) implement this
            super.rollbackRuntime(context, operation, resource);
        }
    };

    private static final OperationStepHandler WRITE = new AbstractWriteAttributeHandler<Formatter>(ATTRIBUTES) {

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<Formatter> handbackHolder) throws OperationFailedException {
            if (PROPERTIES.getName().equals(attributeName)) {
                final Formatter formatter = (Formatter) context.getServiceRegistry(true)
                        .getRequiredService(Capabilities.FORMATTER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue(), Formatter.class))
                        .getService().getValue();
                if (resolvedValue.isDefined()) {
                    for (Property property : resolvedValue.asPropertyList()) {
                        Reflection.setProperty(formatter, property.getName(), property.getValue()
                                .asString());
                    }
                } else {
                    // Remove all current properties
                    for (Property property : currentValue.asPropertyList()) {
                        Reflection.removeProperty(formatter, property.getName());
                    }
                }
            }
            return CLASS.getName().equals(attributeName) || MODULE.getName().equals(attributeName);
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode valueToRestore, final ModelNode valueToRevert, final Formatter handback) throws OperationFailedException {
            if (PROPERTIES.getName().equals(attributeName)) {
                final Formatter formatter = (Formatter) context.getServiceRegistry(true)
                        .getRequiredService(Capabilities.FORMATTER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue(), Formatter.class))
                        .getService().getValue();
                if (valueToRestore.isDefined()) {
                    for (Property property : valueToRestore.asPropertyList()) {
                        Reflection.setProperty(formatter, property.getName(), property.getValue()
                                .asString());
                    }
                } else if (valueToRevert.isDefined()) {
                    // Reset all current properties
                    for (Property property : valueToRevert.asPropertyList()) {
                        Reflection.setProperty(formatter, property.getName(), property.getValue()
                                .asString());
                    }
                }
            }
        }
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new LoggingOperations.LoggingRemoveOperationStepHandler() {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(name);
            if (configuration == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(name));
            }
            logContextConfiguration.removeFormatterConfiguration(name);
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

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        // do nothing by default
    }
}
