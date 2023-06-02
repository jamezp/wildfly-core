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

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * Describes a logger used on a deployment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggerResourceDefinition extends SimpleResourceDefinition {

    private static final String NAME = "logger";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleListAttributeDefinition HANDLERS = SimpleListAttributeDefinition.Builder.of("handlers", HANDLER)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition FILTER = SimpleAttributeDefinitionBuilder.create("filter", ModelType.STRING, true)
            .setStorageRuntime()
            .build();

    private static final SimpleAttributeDefinition USE_PARENT_HANDLERS = SimpleAttributeDefinitionBuilder.create("use-parent-handlers", ModelType.BOOLEAN, true)
            .setStorageRuntime()
            .build();

    public LoggerResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver("deployment", NAME)).setRuntime());
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(LEVEL, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Logger logger = configuration.getLogger(name);
                if (logger != null) {
                    setModelValue(model, logger.getLevel());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(FILTER, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Logger logger = configuration.getLogger(name);
                if (logger != null) {
                    // TODO (jrp) we need the filter/filter-spec
                    setModelValue(model, logger.getFilter());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(HANDLERS, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Logger logger = configuration.getLogger(name);
                if (logger != null) {
                    // TODO (jrp) we need the handler names
                    setModelValue(model, logger.getHandlers());
                }
            }
        });
        resourceRegistration.registerReadOnlyAttribute(USE_PARENT_HANDLERS, new LoggingConfigurationReadStepHandler() {
            @Override
            protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                final Logger logger = configuration.getLogger(name);
                if (logger != null) {
                    setModelValue(model, logger.getUseParentHandlers());
                }
            }
        });
    }

}
