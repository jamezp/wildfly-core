/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.jboss.logmanager.LogContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Reflection {

    public static <T> T createInstance(final Class<T> type, final String moduleName, final String className) throws ModuleLoadException,
            ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        ModuleLoader moduleLoader = ModuleLoader.forClass(Reflection.class);
        if (moduleLoader == null) {
            moduleLoader = Module.getBootModuleLoader();
        }
        final ClassLoader cl = moduleLoader.loadModule(moduleName).getClassLoader();
        final Class<? extends T> toCreate = Class.forName(className, true, cl).asSubclass(type);
        final Constructor<? extends T> constructor = toCreate.getConstructor();
        return constructor.newInstance();
    }

    public static void setProperty(final Object object, final String propertyName, final String value) {
        final Method setter = getPropertySetter(object.getClass(), propertyName);
        if (setter != null) {
            try {
                final Class<?> propertyType = getPropertyType(object.getClass(), propertyName);
                setter.invoke(object, getValue(object.getClass(), propertyName, propertyType, value));
            } catch (IllegalAccessException | InvocationTargetException e) {
                // TODO (jrp) what do we do?
                e.printStackTrace();
            }
        }
    }

    public static void removeProperty(final Object object, final String propertyName) {
        final Method setter = getPropertySetter(object.getClass(), propertyName);
        if (setter != null) {
            try {
                final Class<?> propertyType = getPropertyType(object.getClass(), propertyName);
                setter.invoke(object, getDefaultValue(propertyType));
            } catch (IllegalAccessException | InvocationTargetException e) {
                // TODO (jrp) what do we do?
                e.printStackTrace();
            }
        }
    }

    private static Object getValue(final Class<?> type, final String propertyName, final Class<?> paramType, final String value) {
        if (value == null) {
            return getDefaultValue(paramType);
        }
        final String trimmedValue = value.trim();
        if (paramType == String.class) {
            // Don't use the trimmed value for strings
            return value;
        } else if (paramType == java.util.logging.Level.class) {
            return LogContext.getLogContext().getLevelForName(trimmedValue);
        } else if (paramType == java.util.logging.Logger.class) {
            return LogContext.getLogContext().getLogger(trimmedValue);
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return Boolean.valueOf(trimmedValue);
        } else if (paramType == byte.class || paramType == Byte.class) {
            return Byte.valueOf(trimmedValue);
        } else if (paramType == short.class || paramType == Short.class) {
            return Short.valueOf(trimmedValue);
        } else if (paramType == int.class || paramType == Integer.class) {
            return Integer.valueOf(trimmedValue);
        } else if (paramType == long.class || paramType == Long.class) {
            return Long.valueOf(trimmedValue);
        } else if (paramType == float.class || paramType == Float.class) {
            return Float.valueOf(trimmedValue);
        } else if (paramType == double.class || paramType == Double.class) {
            return Double.valueOf(trimmedValue);
        } else if (paramType == char.class || paramType == Character.class) {
            return !trimmedValue.isEmpty() ? trimmedValue.charAt(0) : 0;
        } else if (paramType == TimeZone.class) {
            return TimeZone.getTimeZone(trimmedValue);
        } else if (paramType == Charset.class) {
            return Charset.forName(trimmedValue);
        } else if (paramType.isEnum()) {
            return Enum.valueOf(paramType.asSubclass(Enum.class), trimmedValue);
        } else {
            throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + type);
        }
    }

    private static Class<?> getPropertyType(Class<?> clazz, String propertyName) {
        final Method setter = getPropertySetter(clazz, propertyName);
        return setter != null ? setter.getParameterTypes()[0] : null;
    }

    private static Class<?> getConstructorPropertyType(Class<?> clazz, String propertyName) {
        final Method getter = getPropertyGetter(clazz, propertyName);
        return getter != null ? getter.getReturnType() : getPropertyType(clazz, propertyName);
    }

    private static Method getPropertySetter(Class<?> clazz, String propertyName) {
        final String upperPropertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        final String set = "set" + upperPropertyName;
        for (Method method : clazz.getMethods()) {
            if ((method.getName()
                    .equals(set) && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    private static Method getPropertyGetter(Class<?> clazz, String propertyName) {
        final String upperPropertyName = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        final Pattern pattern = Pattern.compile("(get|has|is)(" + Pattern.quote(upperPropertyName) + ")");
        for (Method method : clazz.getMethods()) {
            if ((pattern.matcher(method.getName())
                    .matches() && Modifier.isPublic(method.getModifiers())) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static Object getDefaultValue(final Class<?> paramType) {
        if (paramType == boolean.class) {
            return Boolean.FALSE;
        } else if (paramType == byte.class) {
            return (byte) 0x00;
        } else if (paramType == short.class) {
            return (short) 0;
        } else if (paramType == int.class) {
            return 0;
        } else if (paramType == long.class) {
            return 0L;
        } else if (paramType == float.class) {
            return 0.0f;
        } else if (paramType == double.class) {
            return 0.0d;
        } else if (paramType == char.class) {
            return (char) 0x00;
        } else {
            return null;
        }
    }
}
