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

package org.jboss.as.logging.formatters;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.logging.Logging;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.formatters.XmlFormatter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class XmlFormatterResourceDefinition extends StructuredFormatterResourceDefinition {
    public static final String NAME = "xml-formatter";
    private static final PathElement PATH = PathElement.pathElement(NAME);

    public static final SimpleAttributeDefinition PRINT_NAMESPACE = SimpleAttributeDefinitionBuilder.create("print-namespace", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final SimpleAttributeDefinition NAMESPACE_URI = SimpleAttributeDefinitionBuilder.create("namespace-uri", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = Logging.join(DEFAULT_ATTRIBUTES, PRINT_NAMESPACE, NAMESPACE_URI);

    private static final AddStructuredFormatterStepHandler<XmlFormatter> ADD_HANDLER = new AddStructuredFormatterStepHandler<>(XmlFormatter::new, ATTRIBUTES) {
        @Override
        void applyAdditionalAttributes(final OperationContext context, final ModelNode operation, final ModelNode model, final XmlFormatter formatter) throws OperationFailedException {
            formatter.setPrintNamespace(PRINT_NAMESPACE.resolveModelAttribute(context, model).asBoolean());
            final var namespaceUri = NAMESPACE_URI.resolveModelAttribute(context, model);
            if (namespaceUri.isDefined()) {
                formatter.setNamespaceUri(namespaceUri.asString());
            }
        }
    };
    private static final WriteStructuredFormatterStepHandler<XmlFormatter> WRITE_HANDLER = new WriteStructuredFormatterStepHandler<>(ATTRIBUTES) {
        @Override
        boolean applyAdditionalAttributes(final OperationContext context, final String attributeName, final ModelNode resolvedValue, final XmlFormatter formatter) {
            if (attributeName.equals(PRINT_NAMESPACE.getName())) {
                formatter.setPrintNamespace(resolvedValue.asBoolean());
            } else if (attributeName.equals(NAMESPACE_URI.getName())) {
                formatter.setNamespaceUri(resolvedValue.asStringOrNull());
            }
            return false;
        }
    };

    public static final XmlFormatterResourceDefinition INSTANCE = new XmlFormatterResourceDefinition();

    private XmlFormatterResourceDefinition() {
        super(PATH, NAME, ADD_HANDLER, WRITE_HANDLER);
    }

    public static final class TransformerDefinition extends StructuredFormatterTransformerDefinition {

        public TransformerDefinition() {
            super(PATH);
        }
    }
}
