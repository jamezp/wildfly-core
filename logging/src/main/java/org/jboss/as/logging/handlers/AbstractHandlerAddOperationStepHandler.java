/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.FILTER_SPEC;
import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.FORMATTER;
import static org.jboss.as.logging.handlers.AbstractHandlerDefinition.NAMED_FORMATTER;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Formatter;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractHandlerAddOperationStepHandler extends AbstractAddStepHandler {

    AbstractHandlerAddOperationStepHandler(final AttributeDefinition... attributes) {
        super(attributes);
    }

    @Override
    public void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attribute : attributes) {
            // Filter attribute needs to be converted to filter spec
            if (CommonAttributes.FILTER.equals(attribute)) {
                final ModelNode filter = CommonAttributes.FILTER.validateOperation(operation);
                if (filter.isDefined()) {
                    final String value = Filters.filterToFilterSpec(filter);
                    model.get(FILTER_SPEC.getName()).set(value.isEmpty() ? new ModelNode() : new ModelNode(value));
                }
            } else {
                attribute.validateAndSet(operation, model);
            }
        }
    }

    @Override
    protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
        // TODO (jrp) what do we do here?
        super.rollbackRuntime(context, operation, resource);
    }

    protected Supplier<Formatter> getOrCreateFormatter(final ServiceBuilder<?> serviceBuilder, final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String formatterName;
        if (model.hasDefined(NAMED_FORMATTER.getName())) {
            formatterName = NAMED_FORMATTER.resolveModelAttribute(context, model).asString();
        } else {
            // TODO (jrp) I think we need to create the formatter here based on the pattern
            formatterName = PatternFormatterResourceDefinition.getDefaultFormatterName(context.getCurrentAddressValue());
            final String pattern = FORMATTER.resolveModelAttribute(context, model).asString();
            final CapabilityServiceTarget capabilityServiceTarget = context.getCapabilityServiceTarget();
            final CapabilityServiceBuilder<?> capabilityServiceBuilder = capabilityServiceTarget.addCapability(Capabilities.FORMATTER_CAPABILITY.fromBaseCapability(formatterName));
            final Consumer<Formatter> provides = capabilityServiceBuilder.provides(Capabilities.FORMATTER_CAPABILITY);
            capabilityServiceBuilder.setInstance(new Service() {
                        @Override
                        public void start(final StartContext context) {
                            final PatternFormatter formatter = new PatternFormatter(pattern);
                            provides.accept(formatter);
                        }

                        @Override
                        public void stop(final StopContext context) {

                        }
                    })
                    .install();
        }

        return serviceBuilder.requires(Capabilities.FORMATTER_CAPABILITY.getCapabilityServiceName(
                formatterName, Formatter.class
        ));
    }
}
