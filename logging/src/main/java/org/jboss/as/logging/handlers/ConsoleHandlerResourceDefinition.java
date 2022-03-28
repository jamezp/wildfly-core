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
import static org.jboss.as.logging.CommonAttributes.ENABLED;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Formatter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker.SimpleRejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.filters.FilterExpressions;
import org.jboss.as.logging.resolvers.TargetResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.handlers.ConsoleHandler;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ConsoleHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String NAME = "console-handler";
    private static final PathElement CONSOLE_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition TARGET = PropertyAttributeDefinition.Builder.of("target", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(Target.SYSTEM_OUT.toString()))
            .setResolver(TargetResolver.INSTANCE)
            .setValidator(EnumValidator.create(Target.class))
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, AUTOFLUSH, TARGET, NAMED_FORMATTER);


    public ConsoleHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(new Parameters(CONSOLE_HANDLER_PATH, LoggingExtension.getResourceDescriptionResolver(CONSOLE_HANDLER_PATH.getKey()))
                        .setAddHandler(new ConsoleHandlerAddOperationStepHandler(includeLegacyAttributes ? Logging.join(LEGACY_ATTRIBUTES, ATTRIBUTES) : ATTRIBUTES))
                        .setRemoveHandler(HandlerOperations.REMOVE_HANDLER)
                        .setCapabilities(Capabilities.HANDLER_CAPABILITY),
                includeLegacyAttributes,
                new ConsoleHandlerWriterOperationStepHandler(includeLegacyAttributes ? Logging.join(LEGACY_ATTRIBUTES, ATTRIBUTES) : ATTRIBUTES),
                null);
    }

    @Override
    protected void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
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

    private static class ConsoleHandlerAddOperationStepHandler extends AbstractHandlerAddOperationStepHandler {
        ConsoleHandlerAddOperationStepHandler(final AttributeDefinition... attributes) {
            super(attributes);
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            final CapabilityServiceBuilder<?> serviceBuilder = context.getCapabilityServiceTarget()
                    .addCapability(Capabilities.HANDLER_CAPABILITY);
            final Consumer<ConsoleHandler> handlerConsumer = serviceBuilder.provides(Capabilities.HANDLER_CAPABILITY);
            final Supplier<Formatter> formatter = getOrCreateFormatter(serviceBuilder, context, model);
            final String encoding;
            if (model.hasDefined(CommonAttributes.ENCODING.getName())) {
                encoding = CommonAttributes.ENCODING.resolveModelAttribute(context, model).asString();
            } else {
                encoding = null;
            }
            final String filter;
            if (model.hasDefined(FILTER_SPEC.getName())) {
                filter = FILTER_SPEC.resolveModelAttribute(context, model).asString();
            } else {
                filter = null;
            }
            final String level;
            if (model.hasDefined(CommonAttributes.LEVEL.getName())) {
                level = CommonAttributes.LEVEL.resolveModelAttribute(context, model).asString();
            } else {
                level = null;
            }
            final Target target = Target.fromString(TARGET.resolveModelAttribute(context, model).asString());
            final boolean autoFlush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
            final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();

            final Supplier<ConsoleHandler> handlerCreator = () -> {
                final ConsoleHandler handler = new ConsoleHandler();
                // TODO (jrp) we actually need to ensure the correct LogContext here
                final LogContext logContext = LogContext.getLogContext();
                switch (target) {
                    case CONSOLE:
                        handler.setTarget(ConsoleHandler.Target.CONSOLE);
                        break;
                    case SYSTEM_ERR:
                        handler.setTarget(ConsoleHandler.Target.SYSTEM_ERR);
                        break;
                    case SYSTEM_OUT:
                        handler.setTarget(ConsoleHandler.Target.SYSTEM_OUT);
                        break;
                }
                if (level != null) {
                    handler.setLevel(logContext.getLevelForName(level.toUpperCase(Locale.ROOT)));
                }
                if (encoding != null) {
                    try {
                        handler.setEncoding(encoding);
                    } catch (UnsupportedEncodingException e) {
                        // TODO (jrp) i18n
                        throw new RuntimeException(e);
                    }
                }
                if (filter != null) {
                    handler.setFilter(FilterExpressions.parse(logContext, filter));
                }
                handler.setAutoFlush(autoFlush);
                handler.setEnabled(enabled);
                return handler;
            };

            serviceBuilder.setInstance(new HandlerService<>(handlerConsumer, formatter, handlerCreator))
                    .install();
        }
    }

    ;

    private static class ConsoleHandlerWriterOperationStepHandler extends AbstractHandlerWriteOperationStepHandler<ConsoleHandler> {
        protected ConsoleHandlerWriterOperationStepHandler(final AttributeDefinition... definitions) {
            super(definitions);
        }

        @Override
        protected boolean handleAttribute(final OperationContext context, final ModelNode operation, final String attributeName, final ModelNode value, final LogContext logContext, final ConsoleHandler handler) {
            if (TARGET.getName().equals(attributeName)) {
                final Target target = Target.fromString(value.asString());
                switch (target) {
                    case CONSOLE:
                        handler.setTarget(ConsoleHandler.Target.CONSOLE);
                        break;
                    case SYSTEM_ERR:
                        handler.setTarget(ConsoleHandler.Target.SYSTEM_ERR);
                        break;
                    case SYSTEM_OUT:
                        handler.setTarget(ConsoleHandler.Target.SYSTEM_OUT);
                        break;
                }
            }
            return false;
        }
    }

    ;
}
