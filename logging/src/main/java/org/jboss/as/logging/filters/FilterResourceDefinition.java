/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.filters;

import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Filter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.PropertyAttributeMarshaller;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.core.logmanager.ObjectBuilder;
import org.wildfly.core.logmanager.ObjectUpdater;

/**
 * The resource definition for {@code /subsystem=logging/filter=*}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FilterResourceDefinition extends SimpleResourceDefinition {
    public static final String NAME = "filter";

    public static final SimpleMapAttributeDefinition CONSTRUCTOR_PROPERTIES = new SimpleMapAttributeDefinition.Builder(
            "constructor-properties",
            true)
            .setAllowExpression(true)
            .setAttributeMarshaller(PropertyAttributeMarshaller.INSTANCE)
            .setXmlName("constructor-properties")
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = {
            CLASS,
            MODULE,
            CONSTRUCTOR_PROPERTIES,
            PROPERTIES,
    };


    /**
     * A step handler to add a custom filter
     */
    private static final OperationStepHandler ADD = new AbstractAddStepHandler(ATTRIBUTES) {
        private final List<String> reservedNames = Arrays.asList(
                "accept",
                "deny",
                "not",
                "all",
                "any",
                "levelChange",
                "levels",
                "levelRange",
                "match",
                "substitute",
                "substituteAll"
        );

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws
                OperationFailedException {
            // Check the name isn't a reserved filter name
            final String name = context.getCurrentAddressValue();
            if (reservedNames.contains(name)) {
                throw LoggingLogger.ROOT_LOGGER.reservedFilterName(name, reservedNames);
            }
            // Check the name has no special characters
            if (!Character.isJavaIdentifierStart(name.charAt(0))) {
                throw LoggingLogger.ROOT_LOGGER.invalidFilterNameStart(name, name.charAt(0));
            }
            for (char c : name.toCharArray()) {
                if (!Character.isJavaIdentifierPart(c)) {
                    throw LoggingLogger.ROOT_LOGGER.invalidFilterName(name, c);
                }
            }
            super.populateModel(context, operation, resource);
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws
                OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final ModelNode moduleNameNode = MODULE.resolveModelAttribute(context, model);
            final String moduleName = moduleNameNode.isDefined() ? moduleNameNode.asString() : null;
            final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
            final ModelNode constructorProperties = CONSTRUCTOR_PROPERTIES.resolveModelAttribute(context, model);

            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());

            final ObjectBuilder<Filter> filterBuilder = ObjectBuilder.of(configuration, Filter.class, className)
                    .setModuleName(moduleName);

            if (constructorProperties.isDefined()) {
                for (var property : constructorProperties.asPropertyList()) {
                    filterBuilder.addConstructorProperty(property.getName(), property.getValue().asString());
                }
            }

            if (properties.isDefined()) {
                for (var property : properties.asPropertyList()) {
                    filterBuilder.addProperty(property.getName(), property.getValue().asString());
                }
            }
            configuration.addFilter(name, filterBuilder.buildLazy());
        }

        @Override
        protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
            // TODO (jrp) how do we do this while keeping integrity? We'd need to make sure any logger or handler which
            // TODO (jrp) has this filter, gets it removed. Ordering could be important here too.
            super.rollbackRuntime(context, operation, resource);
        }
    };

    private static final OperationStepHandler WRITE = new AbstractWriteAttributeHandler<Filter>(ATTRIBUTES) {

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName,
                                               final ModelNode resolvedValue, final ModelNode currentValue, final HandbackHolder<Filter> handbackHolder) throws
                OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            final Filter filter = configuration.getFilter(context.getCurrentAddressValue());
            if (filter == null) {
                throw LoggingLogger.ROOT_LOGGER.filterNotFound(context.getCurrentAddressValue());
            }
            handbackHolder.setHandback(filter);
            final var updater = ObjectUpdater.of(configuration, Filter.class, filter);
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
            // Writing a class attribute or module will require the previous filter to be removed and a new filter
            // added. This also would require each logger or handler that has the filter assigned to reassign the
            // filter. The configuration API does not handle this so a reload will be required.
            return reloadRequired;
        }

        @Override
        protected void revertUpdateToRuntime(final OperationContext context, final ModelNode operation, final String attributeName,
                                             final ModelNode valueToRestore, final ModelNode valueToRevert, final Filter handback) throws
                OperationFailedException {
            // If the handback is null, we can't reset anything, but also nothing should have been set
            if (handback != null) {
                final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
                final var updater = ObjectUpdater.of(configuration, Filter.class, handback);
                if (PROPERTIES.getName().equals(attributeName)) {
                    if (valueToRestore.isDefined()) {
                        for (Property property : valueToRestore.asPropertyList()) {
                            updater.addProperty(property.getName(), property.getValue().asString());
                        }
                    }
                }
                updater.update();
            }
        }
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new AbstractRemoveStepHandler() {

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws
                OperationFailedException {
            final var configuration = Logging.getContextConfiguration(context.getCurrentAddress());
            // This should not be in-use because the capability check should fail if it is
            configuration.removeFilter(context.getCurrentAddressValue());
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) throws
                OperationFailedException {
            // TODO (jrp) how do we do this
        }
    };

    public static final FilterResourceDefinition INSTANCE = new FilterResourceDefinition();

    private FilterResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .addCapabilities(Capabilities.FILTER_CAPABILITY)
        );
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
            if (modelVersion == KnownModelVersion.VERSION_8_0_0) {
                rootResourceBuilder.rejectChildResource(getPathElement());
                loggingProfileBuilder.rejectChildResource(getPathElement());
            }
        }
    }
}
