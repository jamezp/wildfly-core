/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import static org.jboss.as.controller.parsing.ParseUtils.duplicateNamedElement;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.logging.AbstractHandlerDefinition.NAMED_FORMATTER;
import static org.jboss.as.logging.CommonAttributes.AUTOFLUSH;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.FILTER_SPEC;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.APP_NAME;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.BLOCK_ON_RECONNECT;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.FACILITY;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.HOSTNAME;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.MAX_LENGTH;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.MESSAGE_DELIMITER;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.PORT;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.PROTOCOL;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SERVER_ADDRESS;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SYSLOG_FORMATTER;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.SYSLOG_HANDLER;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.TRUNCATE;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.USE_COUNTING_FRAMING;
import static org.jboss.as.logging.SyslogHandlerResourceDefinition.USE_MESSAGE_DELIMITER;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Subsystem parser for 4.0 of the logging subsystem.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class LoggingSubsystemParser_4_0 extends LoggingSubsystemParser_3_0 {

    LoggingSubsystemParser_4_0() {
        //
    }

    @Override
    void parseSyslogHandler(final XMLExtendedStreamReader reader, final PathAddress address, final List<ModelNode> operations, final Set<String> names) throws XMLStreamException {
        final ModelNode operation = Util.createAddOperation();
        // Attributes
        String name = null;
        final EnumSet<Attribute> required = EnumSet.of(Attribute.NAME);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case AUTOFLUSH: {
                    AUTOFLUSH.parseAndSetParameter(value, operation, reader);
                    break;
                }
                case NAME: {
                    name = value;
                    break;
                }
                case ENABLED:
                    ENABLED.parseAndSetParameter(value, operation, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }
        if (!names.add(name)) {
            throw duplicateNamedElement(reader, name);
        }

        // Setup the operation address
        addOperationAddress(operation, address, SYSLOG_HANDLER, name);

        final EnumSet<Element> encountered = EnumSet.noneOf(Element.class);
        while (reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            if (!encountered.add(element)) {
                throw unexpectedElement(reader);
            }
            switch (element) {
                case APP_NAME: {
                    APP_NAME.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FACILITY: {
                    FACILITY.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case HOSTNAME: {
                    HOSTNAME.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case LEVEL: {
                    LEVEL.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                    break;
                }
                case FORMATTER: {
                    while (reader.nextTag() != END_ELEMENT) {
                        switch (Element.forName(reader.getLocalName())) {
                            case SYSLOG_FORMATTER: {
                                SYSLOG_FORMATTER.parseAndSetParameter(readStringAttributeElement(reader, Attribute.SYSLOG_TYPE.getLocalName()), operation, reader);
                                break;
                            }
                            case NAMED_FORMATTER: {
                                NAMED_FORMATTER.parseAndSetParameter(readNameAttribute(reader), operation, reader);
                                break;
                            }
                            default: {
                                throw unexpectedElement(reader);
                            }
                        }
                    }
                    break;
                }
                case PORT: {
                    PORT.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case SERVER_ADDRESS: {
                    SERVER_ADDRESS.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case BLOCK_ON_RECONNECT: {
                    BLOCK_ON_RECONNECT.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case FILTER_SPEC: {
                    FILTER_SPEC.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case MAX_LENGTH: {
                    MAX_LENGTH.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case MESSAGE_DELIMITER: {
                    MESSAGE_DELIMITER.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case PROTOCOL: {
                    PROTOCOL.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case TRUNCATE: {
                    TRUNCATE.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case USE_COUNTING_FRAMING: {
                    USE_COUNTING_FRAMING.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                case USE_MESSAGE_DELIMITER: {
                    USE_MESSAGE_DELIMITER.parseAndSetParameter(readValueAttribute(reader), operation, reader);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        operations.add(operation);
    }
}
