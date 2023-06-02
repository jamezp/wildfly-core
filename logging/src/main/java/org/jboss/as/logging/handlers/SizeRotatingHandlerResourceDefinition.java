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
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.validators.SizeValidator;
import org.jboss.as.logging.validators.SuffixValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.SizeRotatingFileHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SizeRotatingHandlerResourceDefinition extends AbstractFileHandlerDefinition {

    public static final String NAME = "size-rotating-file-handler";
    private static final PathElement SIZE_ROTATING_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition MAX_BACKUP_INDEX = SimpleAttributeDefinitionBuilder.create("max-backup-index", ModelType.INT, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setRequired(false)
            .setDefaultValue(new ModelNode(1))
            .setValidator(new IntRangeValidator(1, true))
            .build();

    public static final SimpleAttributeDefinition ROTATE_ON_BOOT = SimpleAttributeDefinitionBuilder.create("rotate-on-boot", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition ROTATE_SIZE = SimpleAttributeDefinitionBuilder.create("rotate-size", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("2m"))
            .setValidator(new SizeValidator())
            .build();

    public static final SimpleAttributeDefinition SUFFIX = SimpleAttributeDefinitionBuilder.create("suffix", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(new SuffixValidator(true, false))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, APPEND, MAX_BACKUP_INDEX, ROTATE_SIZE, ROTATE_ON_BOOT, NAMED_FORMATTER, FILE, SUFFIX);
    private static final AttributeDefinition[] ALL_ATTRIBUTES = Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES);

    public SizeRotatingHandlerResourceDefinition(final ResolvePathHandler resolvePathHandler, final PathInfoHandler diskUsagePathHandler,
                                                 final PathManager pathManager, final boolean includeLegacyAttributes) {
        super(createParameters(SIZE_ROTATING_HANDLER_PATH, new FileHandlerAddStepHandler(includeLegacyAttributes, pathManager)),
                true, resolvePathHandler, diskUsagePathHandler,
                new FileHandlerWriteStepHandler(includeLegacyAttributes, pathManager), includeLegacyAttributes ? ALL_ATTRIBUTES : ATTRIBUTES);
    }

    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(SIZE_ROTATING_HANDLER_PATH);
        }

        @Override
        void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_2_0_0: {
                    resourceBuilder
                            .getAttributeBuilder()
                            .setDiscard(DiscardAttributeChecker.UNDEFINED, SUFFIX)
                            .addRejectCheck(RejectAttributeChecker.DEFINED, SUFFIX)
                            .end();
                    if (loggingProfileBuilder != null) {
                        loggingProfileBuilder
                                .getAttributeBuilder()
                                .setDiscard(DiscardAttributeChecker.UNDEFINED, SUFFIX)
                                .addRejectCheck(RejectAttributeChecker.DEFINED, SUFFIX)
                                .end();
                    }
                    break;
                }
            }

        }
    }

    private static class FileHandlerAddStepHandler extends AbstractHandlerAddStepHandler<SizeRotatingFileHandler> {
        private final PathManager pathManager;

        private FileHandlerAddStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model,
                            final SizeRotatingFileHandler handler, final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final boolean autoFlush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
            handler.setAutoFlush(autoFlush);
            handler.setRotateOnBoot(ROTATE_ON_BOOT.resolveModelAttribute(context, model).asBoolean());
            final var suffix = SUFFIX.resolveModelAttribute(context, model);
            if (suffix.isDefined()) {
                handler.setSuffix(suffix.asString());
            }
        }

        @Override
        SizeRotatingFileHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                              final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final var file = resolveFile(pathManager, context, FILE.resolveModelAttribute(context, operation));
            final var append = APPEND.resolveModelAttribute(context, model).asBoolean();
            final var maxBackupIndex = MAX_BACKUP_INDEX.resolveModelAttribute(context, model).asInt();
            final var rotateSize = Handlers.parseSize(ROTATE_SIZE.resolveModelAttribute(context, model));
            try {
                return new SizeRotatingFileHandler(file.toFile(), append, rotateSize, maxBackupIndex);
            } catch (FileNotFoundException e) {
                throw LoggingLogger.ROOT_LOGGER.invalidLogFile(e, file);
            }
        }
    }

    private static class FileHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<SizeRotatingFileHandler> {
        private final PathManager pathManager;

        protected FileHandlerWriteStepHandler(final boolean includeLegacyAttributes, final PathManager pathManager) {
            super(includeLegacyAttributes, ATTRIBUTES);
            this.pathManager = pathManager;
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue,
                                     final ModelNode currentValue, final ContextConfiguration configuration,
                                     final SizeRotatingFileHandler handler) throws OperationFailedException {
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
