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

/**
 * Represents a command line argument in a possible key/value pair.
 */
abstract class Argument {
    private final String key;
    private final String value;

    Argument(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Attempts to parse the argument into a key value pair. The separator is assumed to be {@code =}. If the value
     * starts with a {@code -D} it's assumed to be a system property.
     * <p/>
     * If the argument is not a traditional key/value pair separated by an {@code =} the arguments key will be the full
     * argument passed in and the arguments value will be {@code null}.
     *
     * @param arg the argument to parse
     *
     * @return the parsed argument
     */
    static Argument parse(final String arg) {
        if (arg.startsWith("-D")) {
            final String key;
            final String value;
            // Check for an =
            final int index = arg.indexOf('=');
            if (index == -1) {
                key = arg.substring(2);
                value = null;
            } else {
                key = arg.substring(2, index);
                if (arg.length() < (index + 1)) {
                    value = null;
                } else {
                    value = arg.substring(index + 1);
                }
            }
            return new SystemPropertyArgument(key, value);
        }
        final String key;
        final String value;
        // Check for an =
        final int index = arg.indexOf('=');
        if (index == -1) {
            key = arg;
            value = null;
        } else {
            key = arg.substring(0, index);
            if (arg.length() < (index + 1)) {
                value = null;
            } else {
                value = arg.substring(index + 1);
            }
        }
        return new DefaultArgument(key, value);
    }

    static Argument create(final String arg) {
        return new DefaultArgument(arg, null);
    }

    static Argument create(final String key, final String value) {
        return new DefaultArgument(key, value);
    }

    static Argument createSystemProperty(final String key, final String value) {
        return new SystemPropertyArgument(key, value);
    }

    /**
     * They key to the command line argument which may be the full argument.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * The optional value for the command line argument.
     *
     * @return the value or {@code null}
     */
    public String getValue() {
        return value;
    }

    /**
     * Indicates whether or not multiple values are allowed for the argument. In the case of system properties only
     * one value should be set for the property.
     *
     * @return {@code true} if multiple values should be allowed for an argument, otherwise {@code false}
     */
    public boolean multipleValuesAllowed() {
        return true;
    }

    /**
     * The argument formatted for the command line.
     *
     * @return the command line argument
     */
    public abstract String asCommandLineArgument();

    @Override
    public String toString() {
        return asCommandLineArgument();
    }

    private static final class SystemPropertyArgument extends Argument {

        SystemPropertyArgument(final String key, final String value) {
            super((key.startsWith("-D") ? key.substring(2) : key), value);
        }

        @Override
        public boolean multipleValuesAllowed() {
            return false;
        }

        @Override
        public String asCommandLineArgument() {
            return "-D" + getKey() + "=" + getValue();
        }
    }

    private static final class DefaultArgument extends Argument {
        private final String cliArg;

        DefaultArgument(final String key, final String value) {
            super(key, value);
            if (value != null) {
                cliArg = key + "=" + value;
            } else {
                cliArg = key;
            }
        }

        @Override
        public String asCommandLineArgument() {
            return cliArg;
        }
    }
}
