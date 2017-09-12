/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.logging.AbstractHandlerDefinition.NAMED_FORMATTER;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.FILTER_SPEC;
import static org.jboss.as.logging.CommonAttributes.LEVEL;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.HandlerOperations.HandlerAddOperationStepHandler;
import org.jboss.as.logging.HandlerOperations.LogHandlerWriteAttributeHandler;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.as.logging.validators.Validators;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.jboss.logmanager.handlers.SyslogHandler.Facility;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class SyslogHandlerResourceDefinition extends TransformerResourceDefinition {

    static final String SYSLOG_HANDLER = "syslog-handler";
    static final PathElement SYSLOG_HANDLER_PATH = PathElement.pathElement(SYSLOG_HANDLER);

    static final PropertyAttributeDefinition APP_NAME = PropertyAttributeDefinition.Builder.of("app-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setPropertyName("appName")
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    static final PropertyAttributeDefinition BLOCK_ON_RECONNECT = PropertyAttributeDefinition.Builder.of("block-on-reconnect", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setPropertyName("blockOnReconnect")
            .setDefaultValue(new ModelNode(false))
            .build();

    static final PropertyAttributeDefinition FACILITY = PropertyAttributeDefinition.Builder.of("facility", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(FacilityAttribute.USER_LEVEL.toString()))
            .setResolver(FacilityResolver.INSTANCE)
            .setValidator(EnumValidator.create(FacilityAttribute.class, true, true))
            .build();

    static final PropertyAttributeDefinition HOSTNAME = PropertyAttributeDefinition.Builder.of("hostname", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    static final PropertyAttributeDefinition MAX_LENGTH = PropertyAttributeDefinition.Builder.of("max-length", ModelType.INT, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setPropertyName("maxLength")
            .build();

    static final PropertyAttributeDefinition MESSAGE_DELIMITER = PropertyAttributeDefinition.Builder.of("message-delimiter", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeGroup("delimiter")
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setPropertyName("messageDelimiter")
            .build();

    static final PropertyAttributeDefinition PORT = PropertyAttributeDefinition.Builder.of("port", ModelType.INT, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(514))
            .setValidator(new IntRangeValidator(0, 65535, true, true))
            .build();

    // TODO (jrp) on audit-logging this is defined as a child resource with the names tcp, tls and udp
    // TODO (jrp) on the elytron secyrity audit logging it's called transport
    // TODO (jrp) for SSL (TLS) it would nice to have a ssl-context from Elytron, however this will likely not work with boot logging
    static final PropertyAttributeDefinition PROTOCOL = PropertyAttributeDefinition.Builder.of("protocol", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(SyslogHandler.Protocol.UDP.name()))
            .setValidator(EnumValidator.create(SyslogHandler.Protocol.class, EnumSet.allOf(SyslogHandler.Protocol.class)))
            .build();

    static final PropertyAttributeDefinition SERVER_ADDRESS = PropertyAttributeDefinition.Builder.of("server-address", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("localhost"))
            .setPropertyName("serverHostname")
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    static final PropertyAttributeDefinition SYSLOG_FORMATTER = PropertyAttributeDefinition.Builder.of("syslog-format", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        final String content = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(Attribute.SYSLOG_TYPE.getLocalName(), content);
                        writer.writeEndElement();
                    }
                }
            })
            .setDefaultValue(new ModelNode(SyslogType.RFC5424.name()))
            .setPropertyName("syslogType")
            .setValidator(EnumValidator.create(SyslogType.class, true, true))
            .build();

    static final PropertyAttributeDefinition TRUNCATE = PropertyAttributeDefinition.Builder.of("truncate", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(false))
            .build();

    // TODO (jrp) make sure the names are consistent with what is used in the audit logger
    static final PropertyAttributeDefinition USE_COUNTING_FRAMING = PropertyAttributeDefinition.Builder.of("use-counting-framing", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(false))
            .setPropertyName("useCountingFraming")
            .build();

    // TODO (jrp) this may go away, if it stays validate the default value. One thing to note is if the user wants this
    // TODO (jrp) delimited with null this would be required as you can't set a value in the model to null
    static final PropertyAttributeDefinition USE_MESSAGE_DELIMITER = PropertyAttributeDefinition.Builder.of("use-message-delimiter", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setAttributeGroup("delimiter")
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(false))
            .setPropertyName("useMessageDelimiter")
            .build();

    /*
    * Attributes
    */
    static final AttributeDefinition[] ATTRIBUTES = {
            AUTOFLUSH,
            APP_NAME,
            BLOCK_ON_RECONNECT,
            ENABLED,
            FACILITY,
            FILTER_SPEC,
            HOSTNAME,
            LEVEL,
            NAMED_FORMATTER,
            MAX_LENGTH,
            MESSAGE_DELIMITER,
            PORT,
            PROTOCOL,
            SERVER_ADDRESS,
            SYSLOG_FORMATTER,
            TRUNCATE,
            USE_COUNTING_FRAMING,
            USE_MESSAGE_DELIMITER,
    };

    static final HandlerAddOperationStepHandler ADD_HANDLER = new HandlerAddOperationStepHandler(SyslogHandler.class, ATTRIBUTES);
    static final LogHandlerWriteAttributeHandler WRITE_HANDLER = new LogHandlerWriteAttributeHandler(ATTRIBUTES);

    static final SyslogHandlerResourceDefinition INSTANCE = new SyslogHandlerResourceDefinition();

    public SyslogHandlerResourceDefinition() {
        super(SYSLOG_HANDLER_PATH,
                LoggingExtension.getResourceDescriptionResolver(SYSLOG_HANDLER),
                ADD_HANDLER,
                HandlerOperations.REMOVE_HANDLER);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, WRITE_HANDLER);
        }
    }

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {

        if (modelVersion.hasTransformers()) {
            final PathElement pathElement = getPathElement();
            final ResourceTransformationDescriptionBuilder resourceBuilder = rootResourceBuilder.addChildResource(pathElement);
            final ResourceTransformationDescriptionBuilder loggingProfileResourceBuilder = loggingProfileBuilder.addChildResource(pathElement);
            switch (modelVersion) {
                case VERSION_1_5_0:
                case VERSION_4_0_0: {
                    // TODO (jrp) this is all really ugly. We should find a way to clean this up.
                    resourceBuilder
                            .getAttributeBuilder()
                            .setDiscard(DiscardAttributeChecker.UNDEFINED, FILTER_SPEC, MAX_LENGTH, NAMED_FORMATTER, MESSAGE_DELIMITER)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, new ModelNode(false)),
                                    BLOCK_ON_RECONNECT, TRUNCATE, USE_COUNTING_FRAMING, USE_MESSAGE_DELIMITER)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, new ModelNode(true)),
                                    AUTOFLUSH)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, PROTOCOL.getDefaultValue()), PROTOCOL)
                            .addRejectCheck(RejectAttributeChecker.DEFINED, BLOCK_ON_RECONNECT, FILTER_SPEC, MAX_LENGTH,
                                    NAMED_FORMATTER, MESSAGE_DELIMITER, PROTOCOL, TRUNCATE, USE_COUNTING_FRAMING, USE_MESSAGE_DELIMITER)
                            .end();
                    loggingProfileResourceBuilder
                            .getAttributeBuilder()
                            .setDiscard(DiscardAttributeChecker.UNDEFINED, FILTER_SPEC, MAX_LENGTH, NAMED_FORMATTER, MESSAGE_DELIMITER)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, new ModelNode(false)),
                                    BLOCK_ON_RECONNECT, TRUNCATE, USE_COUNTING_FRAMING, USE_MESSAGE_DELIMITER)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, new ModelNode(true)),
                                    AUTOFLUSH)
                            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(false, true, PROTOCOL.getDefaultValue()), PROTOCOL)
                            .addRejectCheck(RejectAttributeChecker.DEFINED, BLOCK_ON_RECONNECT, FILTER_SPEC, MAX_LENGTH,
                                    NAMED_FORMATTER, MESSAGE_DELIMITER, PROTOCOL, TRUNCATE, USE_COUNTING_FRAMING, USE_MESSAGE_DELIMITER)
                            .end();
                    break;
                }
            }
        }
    }

    static enum FacilityAttribute {
        KERNEL("kernel"),
        USER_LEVEL("user-level"),
        MAIL_SYSTEM("mail-system"),
        SYSTEM_DAEMONS("system-daemons"),
        SECURITY("security"),
        SYSLOGD("syslogd"),
        LINE_PRINTER("line-printer"),
        NETWORK_NEWS("network-news"),
        UUCP("uucp"),
        CLOCK_DAEMON("clock-daemon"),
        SECURITY2("security2"),
        FTP_DAEMON("ftp-daemon"),
        NTP("ntp"),
        LOG_AUDIT("log-audit"),
        LOG_ALERT("log-alert"),
        CLOCK_DAEMON2("clock-daemon2"),
        LOCAL_USE_0("local-use-0"),
        LOCAL_USE_1("local-use-1"),
        LOCAL_USE_2("local-use-2"),
        LOCAL_USE_3("local-use-3"),
        LOCAL_USE_4("local-use-4"),
        LOCAL_USE_5("local-use-5"),
        LOCAL_USE_6("local-use-6"),
        LOCAL_USE_7("local-use-7");

        private static final Map<String, FacilityAttribute> MAP;

        static {
            MAP = new HashMap<String, FacilityAttribute>();
            for (FacilityAttribute facilityAttribute : values()) {
                MAP.put(facilityAttribute.toString(), facilityAttribute);
            }
        }

        private final Facility facility;
        private final String value;

        FacilityAttribute(final String value) {
            this.value = value;
            this.facility = Facility.valueOf(value.replace("-", "_").toUpperCase(Locale.ENGLISH));
        }

        public Facility getFacility() {
            return facility;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        static FacilityAttribute fromString(final String value) {
            return MAP.get(value);
        }
    }

    static class FacilityResolver implements ModelNodeResolver<String> {
        static final FacilityResolver INSTANCE = new FacilityResolver();

        @Override
        public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
            return FacilityAttribute.fromString(value.asString()).getFacility().name();
        }
    }
}
