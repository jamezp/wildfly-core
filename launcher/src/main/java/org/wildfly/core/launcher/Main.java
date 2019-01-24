/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

import java.util.Arrays;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Main {

    public static void main(final String[] args) throws Exception {
        final Environment environment = Environment.determine();
        String[] serverArgs;
        final AbstractEntry ep;
        if (args == null || args.length == 0) {
            ep = new StandaloneEntry(environment, Configuration.of(environment.resolvePath("bin", "standalone.properties")));
            serverArgs = args;
        } else {
            // First argument should indicate the type
            if ("--domain".equals(args[0])) {
                ep = new DomainEntry(environment, Configuration.of(environment.resolvePath("bin", "domain.properties")));
            } else {
                ep = new StandaloneEntry(environment, Configuration.of(environment.resolvePath("bin", "standalone.properties")));
            }
            serverArgs = Arrays.copyOfRange(args, 1, args.length);
        }
        Process process = null;
        try {
            process = ep.launch(serverArgs);
            final int status = process.waitFor();
            System.out.println(status);
        } finally {
            if (process != null) {
                final int status = process.destroyForcibly().waitFor();
                System.out.printf("******** Exit status: %d *********%n", status);
                System.exit(status);
            }
        }
    }
}
