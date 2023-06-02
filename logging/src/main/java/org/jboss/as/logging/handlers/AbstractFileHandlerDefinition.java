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

import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.CommonAttributes.RELATIVE_TO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.handlers.FileHandler;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractFileHandlerDefinition extends AbstractHandlerDefinition {

    private static final String CHANGE_FILE_OPERATION_NAME = "change-file";

    private final ResolvePathHandler resolvePathHandler;
    private final PathInfoHandler diskUsagePathHandler;
    private final boolean registerLegacyOps;
    private final AbstractHandlerWriteStepHandler<? extends FileHandler> writeHandler;

    AbstractFileHandlerDefinition(final Parameters parameters, final boolean registerLegacyOps,
                                  final ResolvePathHandler resolvePathHandler,
                                  final PathInfoHandler diskUsagePathHandler,
                                  final AbstractHandlerWriteStepHandler<? extends FileHandler> writeHandler,
                                  final AttributeDefinition... attributes) {
        super(parameters, registerLegacyOps, writeHandler, attributes);
        this.registerLegacyOps = registerLegacyOps;
        this.resolvePathHandler = resolvePathHandler;
        this.diskUsagePathHandler = diskUsagePathHandler;
        this.writeHandler = writeHandler;
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        if (registerLegacyOps) {
            final OperationStepHandler changeFile = new LegacyUpdateStepHandler<>(writeHandler, FILE);
            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(CHANGE_FILE_OPERATION_NAME, getResourceDescriptionResolver())
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(CommonAttributes.FILE)
                    .build(), changeFile);
        }
        if (resolvePathHandler != null)
            registration.registerOperationHandler(resolvePathHandler.getOperationDefinition(), resolvePathHandler);
        if (diskUsagePathHandler != null)
            PathInfoHandler.registerOperation(registration, diskUsagePathHandler);
    }

    static Path resolveFile(final PathManager pathManager, final OperationContext context, final ModelNode model) throws OperationFailedException {
        final ModelNode pathNode = PATH.resolveModelAttribute(context, model);
        final ModelNode relativeToNode = RELATIVE_TO.resolveModelAttribute(context, model);
        String path = pathNode.asString();
        String result = path;
        if (relativeToNode.isDefined()) {
            result = pathManager.resolveRelativePathEntry(path, relativeToNode.asString());
        }
        if (result == null) {
            throw new IllegalStateException(LoggingLogger.ROOT_LOGGER.pathManagerServiceNotStarted());
        }
        final Path file = Paths.get(result);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw LoggingLogger.ROOT_LOGGER.invalidLogFile(file.normalize().toString());
        }
        return file;
    }

}
