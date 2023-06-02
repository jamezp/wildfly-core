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

import java.util.List;
import java.util.logging.Handler;
import java.util.stream.Collectors;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * Date: 13.10.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogHandlerListAttributeDefinition extends SimpleListAttributeDefinition {
    private static final Handler[] EMPTY = new Handler[0];

    private LogHandlerListAttributeDefinition(final Builder builder, final AttributeDefinition valueType) {
        super(builder, valueType);
    }

    public Handler[] getHandlers(final ContextConfiguration configuration, final OperationContext context, final ModelNode model) throws OperationFailedException {
        return getHandlers(configuration, context, model, false);
    }

    public Handler[] getHandlers(final ContextConfiguration configuration, final OperationContext context, final ModelNode model, final boolean resolved) throws OperationFailedException {
        final List<String> handlerNames;
        if (resolved) {
            if (model.isDefined()) {
                handlerNames = model.asList()
                        .stream().map(ModelNode::asString)
                        .collect(Collectors.toList());
            } else {
                return EMPTY;
            }
        } else {
            if (model.hasDefined(getName())) {
                handlerNames = resolveModelAttribute(context, model).asList()
                        .stream().map(ModelNode::asString)
                        .collect(Collectors.toList());
            } else {
                return EMPTY;
            }
        }
        final Handler[] handlers = new Handler[handlerNames.size()];
        for (int i = 0; i < handlers.length; i++) {
            final var handler = configuration.getHandler(handlerNames.get(i));
            if (handler == null) {
                // TODO (jrp) do we want to throw exceptions, or just log errors?
                throw new OperationFailedException(LoggingLogger.ROOT_LOGGER.handlerConfigurationNotFound(handlerNames.get(i)));
            }
            handlers[i] = handler;
        }
        return handlers;
    }

    public static class Builder extends ListAttributeDefinition.Builder<Builder, LogHandlerListAttributeDefinition> {

        private final AttributeDefinition valueType;


        Builder(final AttributeDefinition valueType, final String name) {
            super(name);
            this.valueType = valueType;
            setElementValidator(valueType.getValidator());
        }

        /**
         * Creates a builder for {@link LogHandlerListAttributeDefinition}.
         *
         * @param name the name of the attribute
         *
         * @return the builder
         */
        public static Builder of(final String name, final AttributeDefinition valueType) {
            return new Builder(valueType, name);
        }

        public LogHandlerListAttributeDefinition build() {
            if (getAttributeMarshaller() == null) {
                setAttributeMarshaller(new HandlersAttributeMarshaller(valueType));
            }
            return new LogHandlerListAttributeDefinition(this, valueType);
        }
    }
}
