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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores arguments to be passed to the command line.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Arguments {

    private final Map<String, Collection<Argument>> map;

    Arguments() {
        this.map = new LinkedHashMap<>();
    }

    /**
     * Clears any arguments currently set.
     */
    public void clear() {
        map.clear();
    }

    /**
     * {@link Argument#parse(String) Parses} the argument and adds it to the collection of arguments ignoring {@code null}
     * arguments.
     *
     * @param arg the argument to add
     */
    public void add(final String arg) {
        if (arg != null) {
            final Argument argument = Argument.parse(arg);
            add(argument);
        }
    }

    /**
     * Adds a key/value pair to the collection of arguments.
     * <p/>
     * If the key starts with {@code -D} it's assumed it's a system property argument and the prefix will be stripped
     * from the key when checking for uniqueness.
     *
     * @param key   the key for the argument
     * @param value the value of the argument which may be {@code null}
     */
    public void add(final String key, final String value) {
        if (key != null) {
            final Argument argument;
            if (key.startsWith("-D")) {
                argument = Argument.createSystemProperty(key, value);
            } else {
                argument = Argument.create(key, value);
            }
            add(argument);
        }
    }

    /**
     * Sets a key/value pair to the collection of arguments. This guarantees only one value will be assigned to the
     * argument key. A {@code null} value indicates the key should be removed.
     * <p/>
     * If the key starts with {@code -D} it's assumed it's a system property argument and the prefix will be stripped
     * from the key when checking for uniqueness.
     *
     * @param key   the key for the argument
     * @param value the value of the argument which may be {@code null}
     */
    public void set(final String key, final String value) {
        if (key != null) {
            if (value == null) {
                map.remove(key);
            } else {
                final Argument argument;
                if (key.startsWith("-D")) {
                    argument = Argument.createSystemProperty(key, value);
                } else {
                    argument = Argument.create(key, value);
                }
                set(argument);
            }
        }
    }

    /**
     * Sets an argument to the collection of arguments. This guarantees only one value will be assigned to the
     * argument key.
     *
     * @param argument the argument to add
     */
    public void set(final Argument argument) {
        if (argument != null) {
            map.put(argument.getKey(), Collections.singleton(argument));
        }
    }

    /**
     * Parses each argument and adds them to the collection of arguments ignoring any {@code null} values.
     *
     * @param args the arguments to add
     *
     * @see #add(String)
     */
    public void addAll(final String... args) {
        if (args != null) {
            for (String arg : args) {
                add(arg);
            }
        }
    }

    /**
     * Gets the first value for the key.
     *
     * @param key the key to check for the value
     *
     * @return the value or {@code null} if the key is not found or the value was {@code null}
     */
    public String get(final String key) {
        final Collection<Argument> args = map.get(key);
        if (args != null) {
            return args.iterator().hasNext() ? args.iterator().next().getValue() : null;
        }
        return null;
    }

    /**
     * Gets the value for the key.
     *
     * @param key the key to check for the value
     *
     * @return the value or an empty collection if no values were set
     */
    Collection<Argument> getArguments(final String key) {
        final Collection<Argument> args = map.get(key);
        if (args != null) {
            return new ArrayList<>(args);
        }
        return Collections.emptyList();
    }

    /**
     * Removes the argument from the collection of arguments.
     *
     * @param key they key of the argument to remove
     *
     * @return the arguments or {@code null} if the argument was not found
     */
    public Collection<Argument> remove(final String key) {
        return map.remove(key);
    }

    /**
     * Returns the arguments as a list in their command line form.
     *
     * @return the arguments for the command line
     */
    public List<String> asList() {
        final List<String> result = new ArrayList<>();
        for (Collection<Argument> args : map.values()) {
            for (Argument arg : args) {
                result.add(arg.asCommandLineArgument());
            }
        }
        return result;
    }

    /**
     * Adds the argument to the collection of arguments ignoring {@code null} values.
     *
     * @param argument the argument to add
     */
    void add(final Argument argument) {
        if (argument != null) {
            if (argument.multipleValuesAllowed()) {
                Collection<Argument> arguments = map.get(argument.getKey());
                if (arguments == null) {
                    arguments = new ArrayList<>();
                    map.put(argument.getKey(), arguments);
                }
                arguments.add(argument);
            } else {
                set(argument);
            }
        }
    }


}
