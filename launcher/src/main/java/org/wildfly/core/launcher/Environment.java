/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.core.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.wildfly.core.launcher.logger.LauncherMessages;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("UnusedReturnValue")
class Environment {
    private static final String JAVA_EXE;
    private static final Path JAVA_HOME;
    private static final boolean MAC;
    private static final boolean WINDOWS;

    static final String HOME_DIR = "jboss.home.dir";
    static final String MODULES_JAR_NAME = "jboss-modules.jar";
    private static final String JMODS_DIR = "jmods";

    static {
        final String os = SecurityActions.getSystemProperty("os.name").toLowerCase(Locale.ROOT);
        MAC = os.startsWith("mac");
        WINDOWS = os.contains("win");
        String exe = "java";
        if (WINDOWS) {
            exe = "java.exe";
        }
        JAVA_EXE = exe;
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }
        JAVA_HOME = Paths.get(javaHome);
    }

    private final Path wildflyHome;
    private Path javaHome;
    private final List<String> modulesDirs;
    private boolean addDefaultModuleDir;

    Environment(final String wildflyHome) {
        this(validateWildFlyDir(wildflyHome));
    }

    Environment(final Path wildflyHome) {
        this.wildflyHome = validateWildFlyDir(wildflyHome);
        modulesDirs = new ArrayList<>();
        addDefaultModuleDir = true;
    }

    static Environment determine() {
        // First check the system property
        String path = getFirstValue("jboss.home", "jboss.home.dir");
        if (path == null) {
            path = SecurityActions.getenv("JBOSS_HOME");
            if (path == null) {
                path = SecurityActions.getSystemProperty("user.dir");
            }
        }
        // TODO (jrp) this likely needs to be done a bit differently where the Environment is better used
        return new Environment(validateWildFlyDir(path));
    }

    /**
     * Returns the WildFly Home directory.
     *
     * @return the WildFly home directory
     */
    Path getWildflyHome() {
        return wildflyHome;
    }

    /**
     * Returns the full path to the {@code jboss-modules.jar}.
     *
     * @return the path to {@code jboss-modules.jar}
     */
    Path getModuleJar() {
        return resolvePath(MODULES_JAR_NAME);
    }

    /**
     * Adds a directory to the collection of module paths.
     *
     * @param moduleDir the module directory to add
     *
     * @throws java.lang.IllegalArgumentException if the path is {@code null}
     */
    Environment addModuleDir(final String moduleDir) {
        if (moduleDir == null) {
            throw LauncherMessages.MESSAGES.nullParam("moduleDir");
        }
        // Validate the path
        final Path path = Paths.get(moduleDir).normalize();
        modulesDirs.add(path.toString());
        return this;
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs an array of module paths to add
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    Environment addModuleDirs(final String... moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        return this;
    }

    /**
     * Adds all the module directories to the collection of module paths.
     *
     * @param moduleDirs a collection of module paths to add
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    Environment addModuleDirs(final Iterable<String> moduleDirs) {
        // Validate and add each path
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        return this;
    }

    /**
     * Replaces any previously set module directories with the collection of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the collection of module directories to use
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    Environment setModuleDirs(final Iterable<String> moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
        return this;
    }

    /**
     * Replaces any previously set module directories with the array of module directories.
     * <p/>
     * The default module directory will <i>NOT</i> be used if this method is invoked.
     *
     * @param moduleDirs the array of module directories to use
     *
     * @throws java.lang.IllegalArgumentException if any of the module paths are invalid or {@code null}
     */
    Environment setModuleDirs(final String... moduleDirs) {
        this.modulesDirs.clear();
        // Process each module directory
        for (String path : moduleDirs) {
            addModuleDir(path);
        }
        addDefaultModuleDir = false;
        return this;
    }

    /**
     * Returns the modules paths used on the command line.
     *
     * @return the paths separated by the {@link File#pathSeparator path separator}
     */
    String getModulePaths() {
        final StringBuilder result = new StringBuilder();
        if (addDefaultModuleDir) {
            result.append(wildflyHome.resolve("modules").toString());
        }
        if (!modulesDirs.isEmpty()) {
            if (addDefaultModuleDir) result.append(File.pathSeparator);
            for (Iterator<String> iterator = modulesDirs.iterator(); iterator.hasNext(); ) {
                result.append(iterator.next());
                if (iterator.hasNext()) {
                    result.append(File.pathSeparator);
                }
            }
        }
        return result.toString();
    }

    /**
     * Sets the Java home where the Java executable can be found.
     *
     * @param javaHome the Java home or {@code null} to use te system property {@code java.home}
     */
    Environment setJavaHome(final String javaHome) {
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
     */
    Environment setJavaHome(final Path javaHome) {
        if (javaHome == null) {
            this.javaHome = null;
        } else {
            this.javaHome = validateJavaHome(javaHome);
        }
        return this;
    }

    /**
     * Returns the Java home directory where the java executable command can be found.
     * <p/>
     * If the directory was not set the system property value, {@code java.home}, should be used.
     *
     * @return the path to the Java home directory
     */
    Path getJavaHome() {
        final Path path;
        if (javaHome == null) {
            path = JAVA_HOME;
        } else {
            path = javaHome;
        }
        return path;
    }

    /**
     * Returns the Java executable command.
     *
     * @return the java command to use
     */
    String getJavaCommand() {
        return getJavaCommand(javaHome);
    }

    /**
     * Returns the Java executable command.
     *
     * @param javaHome the java home directory or {@code null} to use the default
     *
     * @return the java command to use
     */
    String getJavaCommand(final Path javaHome) {
        final Path dir;
        if (javaHome == null) {
            dir = getJavaHome();
        } else {
            dir = javaHome;
        }
        final String exe;
        if (dir == null) {
            exe = "java";
        } else {
            exe = dir.resolve("bin").resolve("java").toString();
        }
        if (exe.contains(" ")) {
            return "\"" + exe + "\"";
        }
        return exe;
    }

    /**
     * Resolves a path relative to the WildFly home directory.
     * <p>
     * Note this does not validate whether or not the path is valid or exists.
     * </p>
     *
     * @param paths the paths to resolve
     *
     * @return the path
     */
    Path resolvePath(final String... paths) {
        Path result = wildflyHome;
        for (String path : paths) {
            result = result.resolve(path);
        }
        return result;
    }

    boolean isMac() {
        return MAC;
    }

    boolean isWindows() {
        return WINDOWS;
    }

    static Path getDefaultJavaHome() {
        return JAVA_HOME;
    }

    static Path validateWildFlyDir(final String wildflyHome) {
        if (wildflyHome == null) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateWildFlyDir(Paths.get(wildflyHome));
    }

    static Path validateWildFlyDir(final Path wildflyHome) {
        if (wildflyHome == null || Files.notExists(wildflyHome)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(wildflyHome);
        }
        if (!Files.isDirectory(wildflyHome)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(wildflyHome);
        }
        final Path result = wildflyHome.toAbsolutePath().normalize();
        if (Files.notExists(result.resolve(MODULES_JAR_NAME))) {
            throw LauncherMessages.MESSAGES.invalidDirectory(MODULES_JAR_NAME, wildflyHome);
        }
        return result;
    }

    static Path validateJavaHome(final String javaHome) {
        if (javaHome == null) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(null);
        }
        return validateJavaHome(Paths.get(javaHome));
    }

    static Path validateJavaHome(final Path javaHome) {
        if (javaHome == null || Files.notExists(javaHome)) {
            throw LauncherMessages.MESSAGES.pathDoesNotExist(javaHome);
        }
        if (!Files.isDirectory(javaHome)) {
            throw LauncherMessages.MESSAGES.invalidDirectory(javaHome);
        }
        final Path result = javaHome.toAbsolutePath().normalize();
        final Path exe = result.resolve("bin").resolve(JAVA_EXE);
        if (Files.notExists(exe)) {
            final int count = exe.getNameCount();
            throw LauncherMessages.MESSAGES.invalidDirectory(exe.subpath(count - 2, count).toString(), javaHome);
        }
        return result;
    }

    private static String getFirstValue(final String... keys) {
        for (String key : keys) {
            final String value = SecurityActions.getSystemProperty(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static boolean isModularJavaHome(final String javaHome) {
        final Path validatedJavaHome = validateJavaHome(javaHome);
        final Path jmodsDir = validatedJavaHome.resolve(JMODS_DIR);
        return Files.isDirectory(jmodsDir);
    }

    static boolean isModularJavaHome(final Path javaHome) {
        final Path validatedJavaHome = validateJavaHome(javaHome);
        final Path jmodsDir = validatedJavaHome.resolve(JMODS_DIR);
        return Files.isDirectory(jmodsDir);
    }
}
