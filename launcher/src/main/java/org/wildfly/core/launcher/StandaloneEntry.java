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

    @Override
    CommandBuilder configure(final String[] args) {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(Environment.determine())
                .addJavaOption("-D[Standalone]");

        for (String arg : args) {
            if ("--debug".equals(arg)) {
                // TODO (jrp) we need to get the port
                commandBuilder.setDebug();
            } else if ("-secmgr".equals(arg)) {
                commandBuilder.setUseSecurityManager(true);
            } else {
                commandBuilder.addServerArgument(arg);
            }
        }
        commandBuilder.addJavaOptions(getJavaOpts("JAVA_OPTS"));
        return commandBuilder;
    }
}
