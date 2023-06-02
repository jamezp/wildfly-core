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
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.LEVEL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;
import java.util.logging.Handler;
import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.handlers.ClientSocketFactory;
import org.jboss.logmanager.handlers.DelayedHandler;
import org.jboss.logmanager.handlers.SocketHandler;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.wildfly.common.function.Functions;

/**
 * Represents a {@link SocketHandler}. The handler will be wrapped with a {@link DelayedHandler} for booting from the
 * {@code logging.properties} file.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Convert2Lambda")
public class SocketHandlerResourceDefinition extends AbstractHandlerDefinition {
    public static final String NAME = "socket-handler";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition BLOCK_ON_RECONNECT = SimpleAttributeDefinitionBuilder.create("block-on-reconnect", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition FILTER_SPEC = SimpleAttributeDefinitionBuilder.create(AbstractHandlerDefinition.FILTER_SPEC)
            .setAlternatives(new String[0])
            .build();

    public static final SimpleAttributeDefinition NAMED_FORMATTER = SimpleAttributeDefinitionBuilder.create(AbstractHandlerDefinition.NAMED_FORMATTER)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setAlternatives(new String[0])
            .setRequired(true)
            .build();

    public static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = SimpleAttributeDefinitionBuilder.create("outbound-socket-binding-ref", ModelType.STRING, false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setAllowExpression(true)
            .setCapabilityReference(Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = SimpleAttributeDefinitionBuilder.create("protocol", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(SocketHandler.Protocol.TCP.name()))
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(new EnumValidator<>(SocketHandler.Protocol.class, SocketHandler.Protocol.values()))
            .build();

    public static final SimpleAttributeDefinition SSL_CONTEXT = SimpleAttributeDefinitionBuilder.create("ssl-context", ModelType.STRING, true)
            .addFlag(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SSL_REF)
            .setAllowExpression(true)
            .setCapabilityReference(Capabilities.SSL_CONTEXT_CAPABILITY)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
            AUTOFLUSH,
            BLOCK_ON_RECONNECT,
            LEVEL,
            ENABLED,
            ENCODING,
            NAMED_FORMATTER,
            FILTER_SPEC,
            PROTOCOL,
            OUTBOUND_SOCKET_BINDING_REF,
            SSL_CONTEXT,
    };

    public static final SocketHandlerResourceDefinition INSTANCE = new SocketHandlerResourceDefinition();

    private SocketHandlerResourceDefinition() {
        super(createParameters(PATH, SocketHandlerAddStepHandler.INSTANCE, SocketHandlerRemoveStepHandler.INSTANCE),
                false,
                SocketWriteStepHandler.INSTANCE,
                null,
                ATTRIBUTES
        );

    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, SocketWriteStepHandler.INSTANCE);
        }
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                         final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_6_0_0:
                    rootResourceBuilder.rejectChildResource(getPathElement());
                    loggingProfileBuilder.rejectChildResource(getPathElement());
                    break;
            }
        }
    }

    private static class SocketHandlerAddStepHandler extends AbstractHandlerAddStepHandler<DelayedHandler> {
        private static final SocketHandlerAddStepHandler INSTANCE = new SocketHandlerAddStepHandler();

        SocketHandlerAddStepHandler() {
            super(false, ATTRIBUTES);
        }

        @Override
        DelayedHandler createHandler(final OperationContext context, final ModelNode operation, final ModelNode model,
                                     final ContextConfiguration configuration, final String name) throws OperationFailedException {
            final SocketHandler.Protocol protocol = SocketHandler.Protocol.valueOf(PROTOCOL.resolveModelAttribute(context, model)
                    .asString());
            final boolean autoFlush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean();
            final boolean blockOnReconnect = BLOCK_ON_RECONNECT.resolveModelAttribute(context, model).asBoolean();
            final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();

            final String socketBindingName = OUTBOUND_SOCKET_BINDING_REF.resolveModelAttribute(context, model)
                    .asString();
            final ModelNode sslContextRef = SSL_CONTEXT.resolveModelAttribute(context, model);

            final ServiceName serviceName = Capabilities.HANDLER_CAPABILITY.getCapabilityServiceName(
                    Capabilities.HANDLER_CAPABILITY.getDynamicName(context.getCurrentAddress()));
            final ServiceBuilder<?> serviceBuilder = context.getServiceTarget().addService(serviceName);

            final Supplier<OutboundSocketBinding> outboundSocketBinding = serviceBuilder.requires(
                    context.getCapabilityServiceName(Capabilities.OUTBOUND_SOCKET_BINDING_CAPABILITY, socketBindingName,
                            OutboundSocketBinding.class)
            );
            final Supplier<SocketBindingManager> socketBindingManager = serviceBuilder.requires(
                    context.getCapabilityServiceName(Capabilities.SOCKET_BINDING_MANAGER_CAPABILITY, SocketBindingManager.class)
            );
            final Supplier<SSLContext> sslContext;
            if (sslContextRef.isDefined()) {
                sslContext = serviceBuilder.requires(
                        context.getCapabilityServiceName(Capabilities.SSL_CONTEXT_CAPABILITY, sslContextRef.asString(), SSLContext.class));
            } else {
                if (protocol == SocketHandler.Protocol.SSL_TCP) {
                    // Attempt to use the default SSL context if a context reference was not set, but we're
                    // using the SSL_TCP protocol
                    try {
                        sslContext = Functions.constantSupplier(SSLContext.getDefault());
                    } catch (NoSuchAlgorithmException e) {
                        throw LoggingLogger.ROOT_LOGGER.failedToConfigureSslContext(e, NAME, context.getCurrentAddressValue());
                    }
                } else {
                    // Not using SSL_TCP use a null value to be ignored in the WildFlyClientSocketFactory
                    sslContext = Functions.constantSupplier(null);
                }
            }
            final DelayedHandler delayedHandler;
            if (configuration.hasHandler(name)) {
                delayedHandler = (DelayedHandler) configuration.getHandler(name);
                // TODO (jrp) should we just close the handler? Really we want it to start queueing again.
            } else {
                delayedHandler = new DelayedHandler(configuration.getContext());
            }

            // A service needs to be used to ensure the dependent services are installed
            serviceBuilder.setInstance(new Service() {

                @Override
                public void start(final StartContext context) {
                    final ClientSocketFactory clientSocketFactory = new WildFlyClientSocketFactory(socketBindingManager.get(),
                            outboundSocketBinding.get(), sslContext.get(), name);
                    delayedHandler.setCloseChildren(true);
                    final SocketHandler socketHandler = new SocketHandler(clientSocketFactory, protocol);
                    socketHandler.setAutoFlush(autoFlush);
                    socketHandler.setBlockOnReconnect(blockOnReconnect);
                    socketHandler.setEnabled(enabled);
                    // Get the filter, formatter and level from the DelayedHandler.
                    socketHandler.setFilter(delayedHandler.getFilter());
                    socketHandler.setFormatter(delayedHandler.getFormatter());
                    socketHandler.setLevel(delayedHandler.getLevel());
                    // Clear any previous handlers and close them, then add the new handler
                    final Handler[] current = delayedHandler.setHandlers(new Handler[] {socketHandler});
                    if (current != null) {
                        for (Handler handler : current) {
                            handler.close();
                        }
                    }
                }

                @Override
                public void stop(final StopContext context) {
                    // Nothing to do on stop
                }
            }).install();
            return delayedHandler;
        }
    }

    private static class SocketHandlerRemoveStepHandler extends LogHandlerRemoveStepHandler {
        private static final SocketHandlerRemoveStepHandler INSTANCE = new SocketHandlerRemoveStepHandler();

        SocketHandlerRemoveStepHandler() {
            super(SocketHandlerAddStepHandler.INSTANCE);
        }

        @Override
        protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) {
                    final ServiceName serviceName = Capabilities.HANDLER_CAPABILITY.getCapabilityServiceName(
                            Capabilities.HANDLER_CAPABILITY.getDynamicName(context.getCurrentAddress()));
                    context.removeService(serviceName);
                }
            }, Stage.RUNTIME);
        }
    }

    private static class SocketWriteStepHandler extends AbstractHandlerWriteStepHandler<DelayedHandler> {
        static final SocketWriteStepHandler INSTANCE = new SocketWriteStepHandler();

        SocketWriteStepHandler() {
            super(false, ATTRIBUTES);
        }

        @Override
        void applyUpdateToRuntime(final OperationContext context, final String attributeName, final ModelNode resolvedValue,
                                  final ModelNode currentValue, final ContextConfiguration configuration,
                                  final DelayedHandler handler) throws OperationFailedException {
            // Should only contain a single handler which should be a socket handler
            final Handler[] children = handler.getHandlers();
            if (children == null || children.length == 0) {
                throw LoggingLogger.ROOT_LOGGER.invalidType(SocketHandler.class, null);
            }
            final var childHandler = children[0];
            if (!(childHandler instanceof SocketHandler)) {
                throw LoggingLogger.ROOT_LOGGER.invalidType(SocketHandler.class, handler.getClass());
            }
            final SocketHandler socketHandler = (SocketHandler) childHandler;
            // Handle writing the attribute
            if (LEVEL.getName().equals(attributeName)) {
                socketHandler.setLevel(handler.getLevel());
            } else if (NAMED_FORMATTER.getName().equals(attributeName)) {
                socketHandler.setFormatter(handler.getFormatter());
            } else if (FILTER_SPEC.getName().equals(attributeName)) {
                socketHandler.setFilter(handler.getFilter());
            } else if (AUTOFLUSH.getName().equals(attributeName)) {
                socketHandler.setAutoFlush(resolvedValue.asBoolean());
            } else if (BLOCK_ON_RECONNECT.getName().equals(attributeName)) {
                socketHandler.setBlockOnReconnect(resolvedValue.asBoolean());
            } else if (ENCODING.getName().equals(attributeName)) {
                try {
                    socketHandler.setEncoding(resolvedValue.asStringOrNull());
                } catch (UnsupportedEncodingException e) {
                    // TODO (jrp) this should happen, but should we log or throw this?
                    throw new RuntimeException(e);
                }
            } else if (ENABLED.getName().equals(attributeName)) {
                socketHandler.setEnabled(resolvedValue.asBoolean());
            } else if (PROTOCOL.getName().equals(attributeName)) {
                socketHandler.setProtocol(SocketHandler.Protocol.valueOf(resolvedValue.asString()));
            }
        }

        @Override
        boolean requiresReload(final String attributeName) {
            return Logging.requiresReload(getAttributeDefinition(attributeName).getFlags());
        }
    }

    private static class WildFlyClientSocketFactory implements ClientSocketFactory {
        private final SocketBindingManager socketBinding;
        private final OutboundSocketBinding outboundSocketBinding;
        private final String name;
        private final SSLContext sslContext;

        private WildFlyClientSocketFactory(final SocketBindingManager socketBinding,
                                           final OutboundSocketBinding outboundSocketBinding, final SSLContext sslContext,
                                           final String name) {
            this.socketBinding = socketBinding;
            this.outboundSocketBinding = outboundSocketBinding;
            this.sslContext = sslContext;
            this.name = name;
        }

        @Override
        public DatagramSocket createDatagramSocket() throws SocketException {
            return socketBinding.createDatagramSocket(name);
        }

        @Override
        public Socket createSocket() throws IOException {
            if (sslContext != null) {
                return sslContext.getSocketFactory().createSocket(getAddress(), getPort());
            }
            return outboundSocketBinding.connect();
        }

        @Override
        public InetAddress getAddress() {
            try {
                return outboundSocketBinding.getResolvedDestinationAddress();
            } catch (UnknownHostException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public int getPort() {
            return outboundSocketBinding.getDestinationPort();
        }
    }
}
