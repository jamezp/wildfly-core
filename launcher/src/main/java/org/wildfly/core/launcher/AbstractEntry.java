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
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractEntry {
    private static final PrintStream stdout = System.out;
    /*
     * TODO (jrp) Known environment variables that need to be checked:
     * - SECMGR
     * - GC_LOG
     *
     */
    final Configuration configuration;

    protected AbstractEntry(final Configuration configuration) {
        this.configuration = configuration;
    }

    abstract AbstractCommandBuilder<?> createCommandBuilder(String[] args);

    static String[] getenv(final String key) {
        // TODO (jrp) this needs to be a real parser, not just split on whitespace characters
        final String value = SecurityActions.getenv(key);
        if (value == null) {
            return new String[0];
        }
        return value.split("\\s+");
    }

    Process launch(final String[] args) throws IOException {
        final AbstractCommandBuilder<?> commandBuilder = createCommandBuilder(args);
        printWelcome(commandBuilder);
        final Launcher launcher = Launcher.of(commandBuilder)
                .inherit();
        launcher.addEnvironmentVariables(configuration.get("env"));
        return launcher.launch();
    }

    void monitor(final String[] args) throws IOException, InterruptedException {
        final Process process = launch(args);
        final int status = process.waitFor();
        if (status == 10) {
            monitor(args);
        } else {
            System.exit(status);
        }
    }

    static void print(final String message) {
        stdout.println(message);
    }

    static void print(final String fmt, final Object... args) {
        stdout.printf(fmt, args);
    }

    static void printWelcome(final AbstractCommandBuilder<?> commandBuilder) {
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
