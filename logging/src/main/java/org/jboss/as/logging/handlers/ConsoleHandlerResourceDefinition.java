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

import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker.SimpleRejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ConsoleHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String NAME = "console-handler";
    private static final PathElement CONSOLE_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition TARGET = SimpleAttributeDefinitionBuilder.create("target", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(Target.SYSTEM_OUT.toString()))
            .setValidator(EnumValidator.create(Target.class))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, TARGET, NAMED_FORMATTER);
    private static final AttributeDefinition[] ALL_ATTRIBUTES = Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES);

    public ConsoleHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(createParameters(CONSOLE_HANDLER_PATH, new AddHandlerOperationStepHandler(includeLegacyAttributes)), true,
                new ConsoleHandlerWriteStepHandler(includeLegacyAttributes), (includeLegacyAttributes ? ALL_ATTRIBUTES : ATTRIBUTES));
    }


    public static final class TransformerDefinition extends AbstractHandlerTransformerDefinition {

        public TransformerDefinition() {
            super(CONSOLE_HANDLER_PATH);
        }

        @Override
        void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_1_5_0:
                case VERSION_2_0_0: {
                    resourceBuilder
                            .getAttributeBuilder()
                            .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, TARGET)
                            .addRejectCheck(new SimpleRejectAttributeChecker(new ModelNode(Target.CONSOLE.toString())), TARGET)
                            .end();
                    loggingProfileBuilder
                            .getAttributeBuilder()
                            .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, TARGET)
                            .addRejectCheck(new SimpleRejectAttributeChecker(new ModelNode(Target.CONSOLE.toString())), TARGET)
                            .end();
                    break;
                }
            }
        }
    }

    private static class AddHandlerOperationStepHandler extends AbstractHandlerAddStepHandler<ConsoleHandler> {

        public AddHandlerOperationStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final ConsoleHandler handler, final ContextConfiguration configuration, final String name) throws OperationFailedException {
            handler.setAutoFlush(AUTOFLUSH.resolveModelAttribute(context, model).asBoolean());
        }

        @Override
        ConsoleHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                     final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final ConsoleHandler.Target target;
            switch (Target.fromString(TARGET.resolveModelAttribute(context, model).asString())) {
                case SYSTEM_ERR: {
                    target = ConsoleHandler.Target.SYSTEM_ERR;
                    break;
                }
                case SYSTEM_OUT: {
                    target = ConsoleHandler.Target.SYSTEM_OUT;
                    break;
                }
                case CONSOLE: {
                    target = ConsoleHandler.Target.CONSOLE;
                    break;
                }
                default:
                    // We should never get here, but lets me safe
                    target = ConsoleHandler.Target.SYSTEM_OUT;
                    break;
            }

            return new ConsoleHandler(target);
        }
    }

    private static class ConsoleHandlerWriteStepHandler extends AbstractHandlerWriteStepHandler<ConsoleHandler> {
        protected ConsoleHandlerWriteStepHandler(final boolean includeLegacyAttributes) {
            super(includeLegacyAttributes, ATTRIBUTES);
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final ModelNode currentValue, final ContextConfiguration configuration, final ConsoleHandler handler) throws OperationFailedException {

            if (attributeName.equals(TARGET.getName())) {
                final ConsoleHandler.Target target;
                switch (Target.fromString(resolvedValue.asString())) {
                    case SYSTEM_ERR: {
                        target = ConsoleHandler.Target.SYSTEM_ERR;
                        break;
                    }
                    case SYSTEM_OUT: {
                        target = ConsoleHandler.Target.SYSTEM_OUT;
                        break;
                    }
                    case CONSOLE: {
                        target = ConsoleHandler.Target.CONSOLE;
                        break;
                    }
                    default:
                        // We should never get here, but lets me safe
                        target = ConsoleHandler.Target.SYSTEM_OUT;
                        break;
                }
                handler.setTarget(target);
            } else if (attributeName.equals(AUTOFLUSH.getName())) {
                handler.setAutoFlush(resolvedValue.asBoolean());
            }
        }
    }
}
