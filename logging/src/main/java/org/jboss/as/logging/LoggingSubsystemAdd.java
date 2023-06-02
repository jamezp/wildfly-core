/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.logging.deployments.LoggingCleanupDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingConfigDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingDependencyDeploymentProcessor;
import org.jboss.as.logging.deployments.LoggingDeploymentResourceProcessor;
import org.jboss.as.logging.deployments.LoggingProfileDeploymentProcessor;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.logmanager.WildFlyContextConfiguration;
import org.wildfly.core.logmanager.WildFlyDelayedHandler;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) we need to ensure before this the log manager as been reset.
class LoggingSubsystemAdd extends AbstractAddStepHandler {

    private final PathManager pathManager;
    private final WildFlyLogContextSelector contextSelector;

    LoggingSubsystemAdd(final PathManager pathManager, final WildFlyLogContextSelector contextSelector) {
        super(LoggingResourceDefinition.ATTRIBUTES);
        this.pathManager = pathManager;
        this.contextSelector = contextSelector;
    }

    @Override
    protected Resource createResource(final OperationContext context) {
        if (pathManager == null) {
            return super.createResource(context);
        }
        final Resource resource = new LoggingResource(pathManager);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        return resource;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws
            OperationFailedException {
        final boolean addDependencies = LoggingResourceDefinition.ADD_LOGGING_API_DEPENDENCIES.resolveModelAttribute(
                context,
                model).asBoolean();
        final boolean useLoggingConfig = LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG.resolveModelAttribute(
                context,
                model).asBoolean();
        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            protected void execute(final DeploymentProcessorTarget processorTarget) {
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME,
                        Phase.STRUCTURE,
                        Phase.STRUCTURE_LOGGING_CLEANUP,
                        new LoggingCleanupDeploymentProcessor());
                if (addDependencies) {
                    processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME,
                            Phase.DEPENDENCIES,
                            Phase.DEPENDENCIES_LOGGING,
                            new LoggingDependencyDeploymentProcessor());
                }
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME,
                        Phase.POST_MODULE,
                        Phase.POST_MODULE_LOGGING_CONFIG,
                        new LoggingConfigDeploymentProcessor(contextSelector,
                                LoggingResourceDefinition.USE_DEPLOYMENT_LOGGING_CONFIG.getName(),
                                useLoggingConfig));
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME,
                        Phase.POST_MODULE,
                        Phase.POST_MODULE_LOGGING_PROFILE,
                        new LoggingProfileDeploymentProcessor(contextSelector));
                processorTarget.addDeploymentProcessor(LoggingExtension.SUBSYSTEM_NAME,
                        Phase.INSTALL,
                        Phase.INSTALL_LOGGING_DEPLOYMENT_RESOURCES,
                        new LoggingDeploymentResourceProcessor());
            }
        }, Stage.RUNTIME);

        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);

        // TODO (jrp) as the last step, we need to ensure we remove the DelayedHandler and replay the log messages

        final var configuration = WildFlyContextConfiguration.getInstance();
        // TODO (jrp) for now throw an exception because something has gone wrong, however we can recover safely if we reach this point
        if (configuration == null) {
            throw new OperationFailedException(
                    "Could not configure the ConfigurationContext as it has not been initialized.");
        }
        WildFlyDelayedHandler.reset();
        LoggingLogger.ROOT_LOGGER.trace("Logging subsystem has been added.");
    }
}
