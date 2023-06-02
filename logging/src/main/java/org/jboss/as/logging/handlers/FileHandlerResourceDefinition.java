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

import java.io.FileNotFoundException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.FileHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FileHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "file-handler";
    private static final PathElement FILE_HANDLER_PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, FILE, NAMED_FORMATTER);
    private static final AttributeDefinition[] ALL_ATTRIBUTES = Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES);

    public FileHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler,
                                         final PathManager pathManager, final boolean includeLegacyAttributes) {
        super(createParameters(FILE_HANDLER_PATH, new FileHandlerAddStepHandler(includeLegacyAttributes, pathManager)),
                true, resolvePathHandler, diskUsagePathHandler,
                new FileHandlerWriteStepHandler(includeLegacyAttributes, pathManager), includeLegacyAttributes ? ALL_ATTRIBUTES : ATTRIBUTES);
    }


    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(FILE_HANDLER_PATH);
        }
    }

    private static class FileHandlerAddStepHandler extends AbstractHandlerAddStepHandler<FileHandler> {
        private final PathManager pathManager;

        private FileHandlerAddStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final FileHandler handler, final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final boolean autoFlush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
            handler.setAutoFlush(autoFlush);
        }

        @Override
        FileHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                  final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final var file = resolveFile(pathManager, context, FILE.resolveModelAttribute(context, operation));
            final var append = APPEND.resolveModelAttribute(context, model).asBoolean();
            try {
                return new FileHandler(file.toFile(), append);
            } catch (FileNotFoundException e) {
                throw LoggingLogger.ROOT_LOGGER.invalidLogFile(e, file);
            }
        }
    }

    private static class FileHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<FileHandler> {
        private final PathManager pathManager;

        protected FileHandlerWriteStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue,
                                     final ModelNode currentValue, final ContextConfiguration configuration,
                                     final FileHandler handler) throws OperationFailedException {
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
            }
        }
    }
}
