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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
interface Configuration {

    static Configuration of(final Path path) throws IOException {
        if (Files.notExists(path)) {
            return new Configuration() {
                @Override
                public Map<String, String> get(final String prefix, final boolean ordered) {
                    return Collections.emptyMap();
                }

                @Override
                public String getSingleValue(final String key) {
                    return null;
                }
            };
        }
        final Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new Configuration() {
            @Override
            public Map<String, String> get(final String prefix, final boolean ordered) {
                final String p = prefix + ".";
                final Supplier<Map<String, String>> supplier = (ordered ? TreeMap::new : LinkedHashMap::new);
                return properties.stringPropertyNames().stream()
                        .filter(key -> key.startsWith(p))
                        .collect(Collectors.toMap(key -> key.substring(p.length()), properties::getProperty, (key1, key2) -> key1, supplier));
            }

            @Override
            public String getSingleValue(final String key) {
                return properties.getProperty(key);
            }
        };
    }

    default Map<String, String> get(String prefix) {
        return get(prefix, false);
    }

    Map<String, String> get(String prefix, boolean ordered);

    String getSingleValue(final String key);
}
