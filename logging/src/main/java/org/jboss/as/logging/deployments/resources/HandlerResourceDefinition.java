/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.deployments.resources;

import java.util.logging.Handler;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * Describes a handler used on a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class HandlerResourceDefinition extends SimpleResourceDefinition {

    private static final String NAME = "handler";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final SimpleAttributeDefinition CLASS_NAME = SimpleAttributeDefinitionBuilder.create("class-name", ModelType.STRING)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition MODULE = SimpleAttributeDefinitionBuilder.create("module", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition ENCODING = SimpleAttributeDefinitionBuilder.create("encoding", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleListAttributeDefinition HANDLERS = SimpleListAttributeDefinition.Builder.of("handlers", HANDLER)
            .setRequired(false)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FORMATTER = SimpleAttributeDefinitionBuilder.create("formatter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FILTER = SimpleAttributeDefinitionBuilder.create("filter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition ERROR_MANAGER = SimpleAttributeDefinitionBuilder.create("error-manager", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    HandlerResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver("deployment", NAME)).setRuntime());
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(CLASS_NAME, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    setModelValue(model, handler.getClass().getName());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(MODULE, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) how do we do this?
                    //setModelValue(model, handler.getClass().getModule());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(ENCODING, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    setModelValue(model, handler.getEncoding());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(LEVEL, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    setModelValue(model, handler.getLevel());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FORMATTER, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) we really need the formatter name
                    setModelValue(model, handler.getFormatter());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FILTER, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) we really need the filter name/expression
                    setModelValue(model, handler.getFilter());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(HANDLERS, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) we need the handler names associated with this handler
                    setModelValue(model, new ModelNode().setEmptyList());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(PROPERTIES, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) we need the properties names associated with this handler
                    setModelValue(model, new ModelNode().setEmptyObject());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(ERROR_MANAGER, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Handler handler = configuration.getHandler(name);
                if (handler != null) {
                    // TODO (jrp) we need the error manager name
                    setModelValue(model, handler.getErrorManager());
                }
            }
        });
    }

}
