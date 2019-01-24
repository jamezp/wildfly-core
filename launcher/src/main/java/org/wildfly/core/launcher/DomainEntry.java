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

package org.wildfly.core.launcher;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainEntry extends AbstractEntry {
    private final Environment environment;

    DomainEntry(final Environment environment, final Configuration configuration) {
        super(configuration);
        this.environment = environment;
    }

    public static void main(final String[] args) throws Exception {
        // TODO (jrp) we need to process the arguments and print some help/error messages
        final Environment environment = Environment.determine();
        new DomainEntry(environment, Configuration.of(environment.resolvePath("bin", "domain.properties"))).monitor(args);
    }

    @Override
    AbstractCommandBuilder<?> createCommandBuilder(final String[] args) {
        final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(environment)
                .addProcessControllerJavaOption("-D[Process Controller]");

        for (String arg : args) {
            if ("-secmgr".equals(arg)) {
                commandBuilder.setUseSecurityManager(true);
            } else {
                commandBuilder.addServerArgument(arg);
            }
        }
        // Add the PROCESS_CONTROLLER_JAVA_OPTS and HOST_CONTROLLER_JAVA_OPTS environment variables
        commandBuilder.addProcessControllerJavaOptions(getenv("PROCESS_CONTROLLER_JAVA_OPTS"))
                .addHostControllerJavaOptions(getenv("HOST_CONTROLLER_JAVA_OPTS"));
        return commandBuilder;
    }
}
