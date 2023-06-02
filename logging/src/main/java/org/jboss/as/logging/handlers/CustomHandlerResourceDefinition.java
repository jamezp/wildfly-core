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

import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Handler;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.logging.Logging;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.wildfly.core.logmanager.ObjectBuilder;
import org.wildfly.core.logmanager.ObjectUpdater;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomHandlerResourceDefinition extends AbstractHandlerDefinition {
    public static final String NAME = "custom-handler";
    private static final PathElement CUSTOM_HANDLE_PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, CLASS, MODULE, NAMED_FORMATTER, PROPERTIES);
    private static final AttributeDefinition[] ALL_ATTRIBUTES = Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES);

    public CustomHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(createParameters(CUSTOM_HANDLE_PATH, new CustomHandlerAddStepHandler(includeLegacyAttributes)), true,
                new CustomHandlerWriteStepHandler(includeLegacyAttributes), includeLegacyAttributes ? ATTRIBUTES : ALL_ATTRIBUTES);
    }


    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(CUSTOM_HANDLE_PATH);
        }
    }

    private static class CustomHandlerAddStepHandler extends AbstractHandlerAddStepHandler<Handler> {

        CustomHandlerAddStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        Handler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                              final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final String moduleName = MODULE.resolveModelAttribute(context, model).asString();
            final ObjectBuilder<Handler> handlerBuilder = ObjectBuilder.of(configuration, Handler.class, className)
                    .setModuleName(moduleName);
            // Set any additional properties
            final var properties = PROPERTIES.resolveModelAttribute(context, model);
            if (properties.isDefined()) {
                for (var property : properties.asPropertyList()) {
                    handlerBuilder.addProperty(property.getName(), property.getValue().asString());
                }
            }
            return handlerBuilder.build();
        }
    }

    private static class CustomHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<Handler> {
        protected CustomHandlerWriteStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue,
                                  final ModelNode currentValue, final ContextConfiguration configuration, final Handler handler) throws OperationFailedException {
            final var updater = ObjectUpdater.of(configuration, Formatter.class, handler);
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
            }
            updater.update();
        }

        @Override
        boolean requiresReload(final String attributeName) {
            // Writing a class attribute or module will require the previous handler to be removed and a new handler
            // added. This also would require each logger or handler that has the handler assigned to reassign the
            // handler.
            return !attributeName.equals(PROPERTIES.getName());
        }
    }
}
