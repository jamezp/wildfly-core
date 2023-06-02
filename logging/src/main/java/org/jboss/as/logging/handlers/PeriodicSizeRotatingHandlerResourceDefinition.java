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

import static org.jboss.as.logging.CommonAttributes.APPEND;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.FILE;
import static org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition.SUFFIX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.MAX_BACKUP_INDEX;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_ON_BOOT;
import static org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition.ROTATE_SIZE;

import java.io.FileNotFoundException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler;

/**
 * Resource for a {@link org.jboss.logmanager.handlers.PeriodicSizeRotatingFileHandler}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PeriodicSizeRotatingHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "periodic-size-rotating-file-handler";
    private static final PathElement PERIODIC_SIZE_ROTATING_HANDLER_PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, MAX_BACKUP_INDEX, ROTATE_SIZE, ROTATE_ON_BOOT, SUFFIX, NAMED_FORMATTER, FILE);

    public PeriodicSizeRotatingHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler,
                                                         final PathManager pathManager) {
        super(createParameters(PERIODIC_SIZE_ROTATING_HANDLER_PATH, new FileHandlerAddStepHandler(false, pathManager)),
                false, resolvePathHandler, diskUsagePathHandler,
                new FileHandlerWriteStepHandler(false, pathManager), ATTRIBUTES);
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion,
                                         final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                         final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_2_0_0: {
                    rootResourceBuilder.rejectChildResource(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
                    loggingProfileBuilder.rejectChildResource(PERIODIC_SIZE_ROTATING_HANDLER_PATH);
                    break;
                }
            }
        }
    }

    private static class FileHandlerAddStepHandler extends AbstractHandlerAddStepHandler<PeriodicSizeRotatingFileHandler> {
        private final PathManager pathManager;

        private FileHandlerAddStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model,
                            final PeriodicSizeRotatingFileHandler handler, final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final boolean autoFlush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
            handler.setAutoFlush(autoFlush);
            handler.setRotateOnBoot(ROTATE_ON_BOOT.resolveModelAttribute(context, model).asBoolean());
        }

        @Override
        PeriodicSizeRotatingFileHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                                      final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final var file = resolveFile(pathManager, context, FILE.resolveModelAttribute(context, operation));
            final var append = APPEND.resolveModelAttribute(context, model).asBoolean();
            final var maxBackupIndex = MAX_BACKUP_INDEX.resolveModelAttribute(context, model).asInt();
            final var rotateSize = Handlers.parseSize(ROTATE_SIZE.resolveModelAttribute(context, model));
            final var suffix = SUFFIX.resolveModelAttribute(context, model).asString();
            try {
                return new PeriodicSizeRotatingFileHandler(file.toFile(), suffix, rotateSize, maxBackupIndex, append);
            } catch (FileNotFoundException e) {
                throw LoggingLogger.ROOT_LOGGER.invalidLogFile(e, file);
            }
        }
    }

    private static class FileHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<PeriodicSizeRotatingFileHandler> {
        private final PathManager pathManager;

        protected FileHandlerWriteStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue,
                                     final ModelNode currentValue, final ContextConfiguration configuration,
                                     final PeriodicSizeRotatingFileHandler handler) throws OperationFailedException {
            if (attributeName.equals(APPEND.getName())) {
                handler.setAppend(resolvedValue.asBoolean());
            } else if (attributeName.equals(AUTOFLUSH.getName())) {
                handler.setAutoFlush(resolvedValue.asBoolean());
            } else if (attributeName.equals(FILE.getName())) {
                final var file = resolveFile(pathManager, context, resolvedValue);
                try {
                    handler.setFile(file.toFile());
                } catch (FileNotFoundException e) {
                    throw LoggingLogger.ROOT_LOGGER.invalidLogFile(e, file);
                }
            } else if (attributeName.equals(SUFFIX.getName())) {
                handler.setSuffix(resolvedValue.asStringOrNull());
            } else if (attributeName.equals(MAX_BACKUP_INDEX.getName())) {
                handler.setMaxBackupIndex(resolvedValue.asInt());
            } else if (attributeName.equals(ROTATE_SIZE.getName())) {
                handler.setRotateSize(Handlers.parseSize(resolvedValue));
            } else if (attributeName.equals(ROTATE_ON_BOOT.getName())) {
                handler.setRotateOnBoot(resolvedValue.asBoolean());
            }
        }
    }

}
