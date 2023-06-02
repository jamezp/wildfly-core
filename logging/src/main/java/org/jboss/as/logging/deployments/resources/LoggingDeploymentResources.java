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

import java.util.Collection;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.deployments.LoggingConfigurationService;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingDeploymentResources {

    public static final SimpleResourceDefinition CONFIGURATION = new SimpleResourceDefinition(new SimpleResourceDefinition.Parameters(PathElement.pathElement("configuration"), LoggingExtension.getResourceDescriptionResolver("deployment")).setRuntime());

    public static final SimpleResourceDefinition HANDLER = new HandlerResourceDefinition();

    public static final SimpleResourceDefinition LOGGER = new LoggerResourceDefinition();

    public static final SimpleResourceDefinition FORMATTER = new PropertiesResourceDefinition<Formatter>("formatter") {
        @Override
        protected Formatter getInstance(final ContextConfiguration configuration, final String name) {
            return configuration.getFormatter(name);
        }
    };

    public static final SimpleResourceDefinition FILTER = new PropertiesResourceDefinition<Filter>("filter") {
        @Override
        protected Filter getInstance(final ContextConfiguration configuration, final String name) {
            return configuration.getFilter(name);
        }
    };

    public static final SimpleResourceDefinition POJO = new PropertiesResourceDefinition<>("pojo") {
        @Override
        protected Object getInstance(final ContextConfiguration configuration, final String name) {
            return configuration.getObject(name);
        }
    };

    public static final SimpleResourceDefinition ERROR_MANAGER = new PropertiesResourceDefinition<ErrorManager>("error-manager") {
        @Override
        protected ErrorManager getInstance(final ContextConfiguration configuration, final String name) {
            return configuration.getErrorManager(name);
        }
    };

    /**
     * Registers the deployment resources needed.
     *
     * @param deploymentResourceSupport the deployment resource support
     * @param service                   the service, which may be {@code null}, used to find the resource names that need to be registered
     */
    public static void registerDeploymentResource(final DeploymentResourceSupport deploymentResourceSupport, final LoggingConfigurationService service) {
        final PathElement base = PathElement.pathElement("configuration", service.getConfiguration());
        deploymentResourceSupport.getDeploymentSubModel(LoggingExtension.SUBSYSTEM_NAME, base);
        final ContextConfiguration configuration = service.getValue();
        // Register the child resources if the configuration is not null in cases where a log4j configuration was used
        if (configuration != null) {
            registerDeploymentResource(deploymentResourceSupport, base, HANDLER, configuration.getHandlers().keySet());
            registerDeploymentResource(deploymentResourceSupport, base, LOGGER, configuration.getLoggers());
            registerDeploymentResource(deploymentResourceSupport, base, FORMATTER, configuration.getFormatters()
                    .keySet());
            registerDeploymentResource(deploymentResourceSupport, base, FILTER, configuration.getFilters().keySet());
            registerDeploymentResource(deploymentResourceSupport, base, POJO, configuration.getObjects().keySet());
            registerDeploymentResource(deploymentResourceSupport, base, ERROR_MANAGER, configuration.getErrorManagers()
                    .keySet());
        }
    }

    private static void registerDeploymentResource(final DeploymentResourceSupport deploymentResourceSupport, final PathElement base, final ResourceDefinition def, final Collection<String> names) {
        for (String name : names) {
            // Replace any blank values with the default root-logger name; this should only happen on loggers
            final String resourceName = name.isEmpty() ? RootLoggerResourceDefinition.RESOURCE_NAME : name;
            final PathAddress address = PathAddress.pathAddress(base, PathElement.pathElement(def.getPathElement()
                    .getKey(), resourceName));
            deploymentResourceSupport.getDeploymentSubModel(LoggingExtension.SUBSYSTEM_NAME, address);
        }
    }

    private abstract static class PropertiesResourceDefinition<T> extends SimpleResourceDefinition {

        static final SimpleAttributeDefinition CLASS_NAME = SimpleAttributeDefinitionBuilder.create("class-name", ModelType.STRING)
                .setStorageRuntime()
                .build();

        static final SimpleAttributeDefinition MODULE = SimpleAttributeDefinitionBuilder.create("module", ModelType.STRING, true)
                .setStorageRuntime()
                .build();

        static final SimpleMapAttributeDefinition PROPERTIES = new SimpleMapAttributeDefinition.Builder("properties", ModelType.STRING, true)
                .setStorageRuntime()
                .build();

        PropertiesResourceDefinition(final String name) {
            super(new Parameters(PathElement.pathElement(name), LoggingExtension.getResourceDescriptionResolver("deployment", name)).setRuntime());
        }

        @Override
        public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadOnlyAttribute(CLASS_NAME, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                    final T instance = getInstance(configuration, name);
                    setModelValue(model, instance.getClass().getName());
                }
            });
            resourceRegistration.registerReadOnlyAttribute(MODULE, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final ContextConfiguration configuration, final String name, final ModelNode model) {
                    final T instance = getInstance(configuration, name);
                    // TODO (jrp) how do we do this?
                    //setModelValue(model, configuration.getModuleName());
                }
            });
            resourceRegistration.registerReadOnlyAttribute(PROPERTIES, new LoggingConfigurationReadStepHandler() {
                @Override
                protected void updateModel(final ContextConfiguration logContextConfiguration, final String name, final ModelNode model) {
                    final T instance = getInstance(logContextConfiguration, name);
                    // TODO (jrp) how do we do this?
                    //addProperties(configuration, model);
                }
            });
        }

        protected abstract T getInstance(ContextConfiguration configuration, String name);
    }
}
