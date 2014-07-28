/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.wildfly.core.launcher;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.wildfly.core.launcher.Arguments.Argument;

/**
 * Builds a list of commands used to launch a standalone instance of WildFly.
 * <p/>
 * This builder is not thread safe and the same instance should not be used in multiple threads.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("unused")
public class StandaloneCommandBuilder extends AbstractCommandBuilder<StandaloneCommandBuilder> implements CommandBuilder {

    // JPDA remote socket debugging
    static final String DEBUG_FORMAT = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,address=%d";

    private static final String SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SERVER_LOG_DIR = "jboss.server.log.dir";

    private Path baseDir;
    private Path javaHome;
    private String debugArg;
    private String modulesLocklessArg;
    private String modulesMetricsArg;
    private final Map<String, String> securityProperties;

    /**
     * Creates a new command builder for a standalone instance.
     * <p/>
     * Note the {@code wildflyHome} and {@code javaHome} are not validated using the constructor. The static {@link
     * #of(java.nio.file.Path)} is preferred.
     *
     * @param wildflyHome the path to WildFly
     */
    private StandaloneCommandBuilder(final Path wildflyHome) {
        super(wildflyHome);
        securityProperties = new LinkedHashMap<>();
    }

    /**
     * Creates a command builder for a standalone instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final Path wildflyHome) {
        return new StandaloneCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    /**
     * Creates a command builder for a standalone instance of WildFly.
     *
     * @param wildflyHome the path to the WildFly home directory
     *
     * @return a new builder
     */
    public static StandaloneCommandBuilder of(final String wildflyHome) {
        return new StandaloneCommandBuilder(validateWildFlyDir(wildflyHome));
    }

    @Override
    public StandaloneCommandBuilder addJavaOption(final String jvmArg) {
        if (jvmArg != null && !jvmArg.trim().isEmpty()) {
            final Argument argument = Arguments.parse(jvmArg);
            if (argument.getKey().equals(SERVER_BASE_DIR)) {
                if (argument.getValue() != null) {
                    setBaseDirectory(argument.getValue());
                }
            } else if (argument.getKey().equals(SERVER_CONFIG_DIR)) {
                if (argument.getValue() != null) {
                    setConfigurationDirectory(argument.getValue());
                }
            } else if (argument.getKey().equals(SERVER_LOG_DIR)) {
                if (argument.getValue() != null) {
                    setLogDirectory(argument.getValue());
                }
            } else {
                addJavaOption(argument);
            }
        }
        return this;
    }

    /**
     * Sets the debug argument for the JVM with a default port of {@code 8787}.
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug() {
        return setDebug(false, 8787);
    }

    /**
     * Sets the debug argument for the JVM.
     *
     * @param port the port to listen on
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug(final int port) {
        return setDebug(false, port);
    }

    /**
     * Sets the debug JPDA remote socket debugging argument.
     *
     * @param suspend {@code true} to suspend otherwise {@code false}
     * @param port    the port to listen on
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setDebug(final boolean suspend, final int port) {
        debugArg = String.format(DEBUG_FORMAT, (suspend ? "y" : "n"), port);
        return this;
    }

    /**
     * Sets the base directory to use.
     * <p/>
     * The default is {@code $JBOSS_HOME/standalone}.
     *
     * @param baseDir the base directory or {@code null} to resolve the base directory
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setBaseDirectory(final String baseDir) {
        this.baseDir = validateAndNormalizeDir(baseDir, true);
        return this;
    }

    /**
     * Sets the base directory to use.
     * <p/>
     * The default is {@code $JBOSS_HOME/standalone}.
     *
     * @param baseDir the base directory or {@code null} to resolve the base directory
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setBaseDirectory(final Path baseDir) {
        this.baseDir = validateAndNormalizeDir(baseDir, true);
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaHome(final String javaHome) {
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setJavaHome(final Path javaHome) {
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    /**
     * Set to {@code true} to use JBoss Modules lockless mode.
     *
     * @param b {@code true} to use lockless mode
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setModulesLockless(final boolean b) {
        if (b) {
            modulesLocklessArg = "-Djboss.modules.lockless=true";
        } else {
            modulesLocklessArg = null;
        }
        return this;
    }

    /**
     * Set to {@code true} to gather metrics for JBoss Modules.
     *
     * @param b {@code true} to gather metrics for JBoss Modules.
     *
     * @return this builder
     */
    public StandaloneCommandBuilder setModulesMetrics(final boolean b) {
        if (b) {
            modulesMetricsArg = "-Djboss.modules.metrics=true";
        } else {
            modulesMetricsArg = null;
        }
        return this;
    }

    /**
     * Sets the configuration file for the server. The file must be in the {@link #setConfigurationDirectory(String)
     * configuration} directory. A value of {@code null} will remove the configuration file.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     *
     * @param configFile the configuration file name or {@code null} to remove the configuration file
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setServerConfiguration(final String configFile) {
        addServerArg("-c", configFile);
        return this;
    }

    /**
     * Returns the configuration file {@link #setServerConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getServerConfiguration() {
        return getServerArg("-c");
    }

    /**
     * Sets the configuration file for the server. The file must be in the {@link #setConfigurationDirectory(String)
     * configuration} directory. A value of {@code null} will remove the configuration file.
     * <p/>
     * This will override any previous value set via {@link #addServerArgument(String)}.
     *
     * @param configFile the configuration file name or {@code null} to remove the configuration file
     *
     * @return the builder
     */
    public StandaloneCommandBuilder setServerReadOnlyConfiguration(final String configFile) {
        addServerArg("--read-only-server-config", configFile);
        return this;
    }

    /**
     * Returns the configuration file {@link #setServerConfiguration(String) set} or {@code null} if one was not set.
     *
     * @return the configuration file set or {@code null} if not set
     */
    public String getReadOnlyServerConfiguration() {
        return getServerArg("--read-only-server-config");
    }

    /**
     * Adds a security property to be passed to the server with a {@code null} value.
     *
     * @param key the property key
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperty(final String key) {
        securityProperties.put(key, null);
        return this;
    }

    /**
     * Adds a security property to be passed to the server.
     *
     * @param key   the property key
     * @param value the property value
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperty(final String key, final String value) {
        securityProperties.put(key, value);
        return this;
    }

    /**
     * Adds all the security properties to be passed to the server.
     *
     * @param properties a map of the properties to add, {@code null} values are allowed in the map
     *
     * @return the builder
     */
    public StandaloneCommandBuilder addSecurityProperties(final Map<String, String> properties) {
        securityProperties.putAll(properties);
        return this;
    }

    @Override
    public List<String> buildArguments() {
        final List<String> cmd = new ArrayList<>();
        cmd.add("-D[Standalone]");
        cmd.addAll(getJavaOptions());
        cmd.add(getSystemPackages());
        if (modulesLocklessArg != null) {
            cmd.add(modulesLocklessArg);
        }
        if (modulesMetricsArg != null) {
            cmd.add(modulesMetricsArg);
        }
        if (debugArg != null) {
            cmd.add(debugArg);
        }
        cmd.add(getBootLogArgument("server.log"));
        cmd.add(getLoggingPropertiesArgument("logging.properties"));
        cmd.add("-jar");
        cmd.add(getModulesJarName());
        if (useSecurityManager()) {
            cmd.add(SECURITY_MANAGER_ARG);
        }
        cmd.add("-mp");
        cmd.add(getModulePaths());
        // TODO (jrp) Windows has -jaxpmodule "javax.xml.jax-provider", is this needed?
        cmd.add("org.jboss.as.standalone");
        addSystemPropertyArg(cmd, HOME_DIR, getWildFlyHome());
        addSystemPropertyArg(cmd, SERVER_BASE_DIR, getBaseDirectory());
        addSystemPropertyArg(cmd, SERVER_LOG_DIR, getLogDirectory());
        addSystemPropertyArg(cmd, SERVER_CONFIG_DIR, getConfigurationDirectory());

        // Add the security properties
        StringBuilder sb = new StringBuilder(50);
        for (Map.Entry<String, String> entry : securityProperties.entrySet()) {
            sb.append("-S").append(entry.getKey());
            if (entry.getValue() != null) {
                sb.append('=').append(entry.getValue());
            }
            cmd.add(sb.toString());
            sb.setLength(0);
        }

        // TODO (jrp) FreeBSD may require -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.PollSelectorProvider
        cmd.addAll(getServerArguments());
        return cmd;
    }

    @Override
    public List<String> build() {
        final List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.addAll(buildArguments());
        return cmd;
    }

    @Override
    public Path getJavaHome() {
        final Path path;
        if (javaHome == null) {
            path = validateJavaHome(System.getProperty("java.home"));
        } else {
            path = javaHome;
        }
        return path;
    }

    @Override
    public Path getBaseDirectory() {
        if (baseDir == null) {
            return normalizePath("standalone");
        }
        return baseDir;
    }

    @Override
    protected StandaloneCommandBuilder getThis() {
        return this;
    }
}
