package org.jboss.as.logging;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.logging.filters.Filters;
import org.jboss.as.logging.loggers.LoggerAttributes;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ReadFilterOperationStepHandler implements OperationStepHandler {

    public static final ReadFilterOperationStepHandler INSTANCE = new ReadFilterOperationStepHandler();

    private ReadFilterOperationStepHandler() {

    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final ModelNode filter = LoggerAttributes.FILTER_SPEC.resolveModelAttribute(context, model);
        if (filter.isDefined()) {
            context.getResult().set(Filters.filterSpecToFilter(filter.asString()));
        }
    }
}
