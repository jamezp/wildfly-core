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
public class StandaloneEntry extends AbstractEntry {
    private final Environment environment;

    StandaloneEntry(final Environment environment, final Configuration configuration) {
        super(configuration);
        this.environment = environment;
    }

    public static void main(final String[] args) throws Exception {
        final Environment environment = Environment.determine();
        // TODO (jrp) we need to process the arguments and print some help/error messages
        final StandaloneEntry ep = new StandaloneEntry(environment, Configuration.of(environment.resolvePath("bin", "standalone.properties")));
        ep.monitor(args);
    }

    @Override
    AbstractCommandBuilder<?> createCommandBuilder(final String[] args) {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(environment, false)
                .addJavaOption("-D[Standalone]");
        // First check the JAVA_OPTS and use those if set
        final String[] javaOpts = getenv("JAVA_OPTS");
        if (javaOpts.length > 0) {
            commandBuilder.setJavaOptions(javaOpts);
            print("JAVA_OPTS already set in environment; overriding default settings with values: %s%n", String.join(" ", javaOpts));
        } else {
            configuration.get("sys").forEach((key, value) -> commandBuilder.addJavaOption(Argument.createSystemProperty(key, value)));
            configuration.get("jvm.option").forEach((key, value) -> commandBuilder.addJavaOption(Argument.create(value)));
            final String javaHome = configuration.getSingleValue("env.JAVA_HOME");
            if (javaHome != null) {
                commandBuilder.setJavaHome(javaHome);
            }
        }

        configuration.get("arg").forEach(commandBuilder::addServerArg);

        for (String arg : args) {
            if ("--debug".equals(arg)) {
                // TODO (jrp) we need to get the port
                commandBuilder.setDebug();
            } else if ("-secmgr".equals(arg)) {
                commandBuilder.setUseSecurityManager(true);
            } else if (arg.startsWith("-D")) {
                // Add all system properties as Java options bootstrapping can see them
                commandBuilder.addJavaOption(arg);
            } else {
                commandBuilder.addServerArgument(arg);
            }
        }
        return commandBuilder;
    }
}
