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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractEntry {
    private static final PrintStream stdout = System.out;

    public static void main(final String[] args) throws Exception {
        String[] serverArgs;
        final AbstractEntry ep;
        if (args == null || args.length == 0) {
            ep = new StandaloneEntry();
            serverArgs = args;
        } else {
            // First argument should indicate the type
            if ("--domain".equals(args[0])) {
                ep = new DomainEntry();
            } else {
                ep = new StandaloneEntry();
            }
            serverArgs = Arrays.copyOfRange(args, 1, args.length);
        }
        ep.launch(serverArgs);
    }

    abstract CommandBuilder configure(String[] args);

    static String[] getJavaOpts(final String key) {
        final String value = SecurityActions.getenv(key);
        if (value == null) {
            return new String[0];
        }
        return value.split("\\s+");
    }

    private void launch(final String[] args) throws IOException, InterruptedException {
        final AbstractCommandBuilder<?> commandBuilder = (AbstractCommandBuilder<?>) configure(args);
        printWelcome(commandBuilder);
        final Launcher launcher = Launcher.of(commandBuilder)
                .inherit();
        final Process process = launcher.launch();
        int status = process.waitFor();
        if (status == 10) {
            launch(args);
        }
    }

    private void printWelcome(final AbstractCommandBuilder commandBuilder) {
        stdout.println("=========================================================================");
        stdout.println();
        stdout.println("  JBoss Bootstrap Environment");
        stdout.println();
        stdout.println("  JBOSS_HOME: " + commandBuilder.getWildFlyHome().toAbsolutePath());
        stdout.println();
        stdout.println("  JAVA: " + commandBuilder.getJavaCommand());
        stdout.println();
        stdout.print("  JAVA_OPTS:");
        final List<String> javaOpts;
        if (commandBuilder instanceof StandaloneCommandBuilder) {
            javaOpts = ((StandaloneCommandBuilder) commandBuilder).getJavaOptions();
        } else if (commandBuilder instanceof DomainCommandBuilder) {
            javaOpts = ((DomainCommandBuilder) commandBuilder).getProcessControllerJavaOptions();
        } else {
            javaOpts = Collections.emptyList();
        }
        for (String javaOpt : javaOpts) {
            stdout.print(" " + javaOpt);
        }
        stdout.println();
        stdout.println();
        stdout.println("=========================================================================");
    }
}
