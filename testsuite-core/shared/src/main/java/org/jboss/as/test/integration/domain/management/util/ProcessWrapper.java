/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.test.integration.domain.management.util;

import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;

import java.nio.file.Paths;
import java.util.Map;

/**
 * Basic process test wrapper.
 *
 * @author Emanuel Muckenhuber
 */
class ProcessWrapper {

    private final String processName;
    private final CommandBuilder commandBuilder;
    private final Map<String, String> env;
    private final String workingDirectory;

    private Process process;
    private volatile boolean stopped;

    ProcessWrapper(final String processName, final CommandBuilder commandBuilder, final Map<String, String> env, final String workingDirectory) {
        assert processName != null;
        assert commandBuilder != null;
        assert env != null;
        assert workingDirectory != null;
        this.processName = processName;
        this.commandBuilder = commandBuilder;
        this.env = env;
        this.workingDirectory = workingDirectory;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                ProcessWrapper.this.stop();
            }
        });
    }

    int getExitValue() {
        synchronized (this) {
            try {
                return process.exitValue();
            } catch (IllegalThreadStateException e) {
                return -1;
            }
        }
    }

    void start() throws Exception {
        synchronized (this) {
            if(stopped) {
                throw new IllegalStateException();
            }
            process = Launcher.of(commandBuilder)
                    .setRedirectErrorStream(true)
                    .redirectOutput(Paths.get(workingDirectory, processName + "-process.log"))
                    .addEnvironmentVariables(env)
                    .setDirectory(workingDirectory)
                    .launch();
        }
    }

    int waitFor() throws InterruptedException {
        final Process process;
        synchronized (this) {
            process = this.process;
        }
        if(process != null) {
            return process.waitFor();
        }
        return 0;
    }

    void stop() {
        synchronized (this) {
            boolean stopped = this.stopped;
            if(! stopped) {
                this.stopped = true;
                final Process process = this.process;
                if(process != null) {
                    process.destroy();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "ProcessWrapper [processName=" + processName + ", command=" + commandBuilder + ", env=" + env + ", workingDirectory="
                + workingDirectory + ", stopped=" + stopped + "]";
    }

}
