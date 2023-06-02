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
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.wildfly.core.logmanager.WildFlyContextConfiguration;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingProfileOperations {


    static class LoggingProfileAdd extends AbstractAddStepHandler {
        private final PathManager pathManager;

        LoggingProfileAdd(final PathManager pathManager) {
            this.pathManager = pathManager;
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
        protected void populateModel(final ModelNode operation, final ModelNode model) {
            model.setEmptyObject();
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            // TODO (jrp) this was not done before, but it seems like this should be done
            final var logContext = LogContext.create(true);
            // TODO (jrp) this kind of seems wierd and should maybe be done in the WildFlyLogContextSelector
            WildFlyContextConfiguration.getInstance(logContext);
            WildFlyLogContextSelector.getContextSelector()
                    .addProfileContext(Logging.getLoggingProfileName(context.getCurrentAddress()), logContext);
        }
    }

    static OperationStepHandler REMOVE_PROFILE = new AbstractRemoveStepHandler() {

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) {
            // Get the address and the name of the logger or handler
            final PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
            // Get the logging profile
            final String loggingProfile = Logging.getLoggingProfileName(address);
            final WildFlyLogContextSelector contextSelector = WildFlyLogContextSelector.getContextSelector();
            final LogContext logContext = contextSelector.removeProfileContext(loggingProfile);
            context.completeStep((resultAction, resultContext, resultOperation) -> {
                if (resultAction != ResultAction.KEEP) {
                    contextSelector.addProfileContext(loggingProfile, logContext);
                    resultContext.revertReloadRequired();
                }
            });
            if (logContext != null) {
                context.reloadRequired();
                context.addStep((runtimeContext, runtimeOperation) -> {
                    try (var configuration = logContext.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY)) {
                        logContext.close();
                    } catch (Exception e) {
                        throw new OperationFailedException(e);
                    }
                }, Stage.RUNTIME);
            }
        }

        @Override
        protected void recoverServices(final OperationContext context, final ModelNode operation, final ModelNode model) {
        }
    };

}
