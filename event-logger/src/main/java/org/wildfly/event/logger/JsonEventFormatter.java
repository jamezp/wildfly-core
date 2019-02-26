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

package org.wildfly.event.logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * A formatter which transforms the event into a JSON string.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonEventFormatter implements EventFormatter {

    private final JsonBuilderFactory factory;
    private final Map<String, Object> constantValues;
    private final String timestampKey;
    private final DateTimeFormatter formatter;
    private final boolean includeTimestamp;

    private JsonEventFormatter(final Map<String, Object> constantValues, final String timestampKey,
                               final DateTimeFormatter formatter, final boolean includeTimestamp) {
        this.constantValues = constantValues;
        this.timestampKey = timestampKey;
        this.formatter = formatter;
        this.includeTimestamp = includeTimestamp;
        factory = Json.createBuilderFactory(Collections.emptyMap());
    }

    /**
     * Creates a new builder to build a {@link JsonEventFormatter}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String format(final Event event) {
        final JsonObjectBuilder builder = factory.createObjectBuilder();
        builder.add("eventSource", event.getSource());
        if (includeTimestamp) {
            builder.add(timestampKey, formatter.format(event.getInstant()));
        }
        add(builder, constantValues);
        add(builder, event.getData());
        return builder.build().toString();
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    private static void add(final JsonObjectBuilder builder, final Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if (value instanceof Boolean) {
                builder.add(key, (Boolean) value);
            } else if (value instanceof Double) {
                builder.add(key, (Double) value);
            } else if (value instanceof Integer) {
                builder.add(key, (Integer) value);
            } else if (value instanceof Long) {
                builder.add(key, (Long) value);
            } else if (value instanceof String) {
                builder.add(key, (String) value);
            } else if (value instanceof BigDecimal) {
                builder.add(key, (BigDecimal) value);
            } else if (value instanceof BigInteger) {
                builder.add(key, (BigInteger) value);
            } else if (value instanceof JsonArrayBuilder) {
                builder.add(key, (JsonArrayBuilder) value);
            } else if (value instanceof JsonObjectBuilder) {
                builder.add(key, (JsonObjectBuilder) value);
            } else if (value instanceof JsonValue) {
                builder.add(key, (JsonValue) value);
            } else {
                if (value == null) {
                    builder.addNull(key);
                } else {
                    builder.add(key, String.valueOf(value));
                }
            }
        }
    }

    /**
     * Builder used to create the {@link JsonEventFormatter}.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Builder {
        private Map<String, Object> constantValues;
        private String timestampKey;
        private DateTimeFormatter formatter;
        private ZoneId zoneId;
        private boolean includeTimestamp = true;

        private Builder() {
            constantValues = new LinkedHashMap<>();
        }

        /**
         * Adds a constant value to the final output.
         *
         * @param key   the key to add
         * @param value the value for the key
         *
         * @return this builder
         */
        public Builder addConstant(final String key, final Object value) {
            if (constantValues == null) {
                constantValues = new LinkedHashMap<>();
            }
            constantValues.put(key, value);
            return this;
        }

        /**
         * Sets the key for the timestamp for the event. The default is {@code timestamp}.
         *
         * @param timestampKey the key name or {@code null} to revert to the default
         *
         * @return this builder
         */
        public Builder setTimestampKey(final String timestampKey) {
            this.timestampKey = timestampKey;
            return this;
        }

        /**
         * Set the formatter used to format the timestamp on the event. The default is
         * {@linkplain DateTimeFormatter#ISO_OFFSET_DATE_TIME ISO-8601}.
         * <p>
         * Note the {@linkplain #setZoneId(ZoneId) zone id} is {@linkplain DateTimeFormatter#withZone(ZoneId) zone id}
         * on the formatter.
         * </p>
         *
         * @param formatter the formatter to use or {@code null} to revert to the default.
         *
         * @return this builder
         */
        public Builder setFormatter(final DateTimeFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * Set the zone id for the timestamp. The default is {@link ZoneId#systemDefault()}.
         *
         * @param zoneId the zone id to use or {@code null} to revert to the default
         *
         * @return this builder
         */
        public Builder setZoneId(final ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        /**
         * Sets whether or not the timestamp should be added to the output. The default is {@code true}.
         *
         * @param includeTimestamp {@code true} to include the timestamp or {@code false} to leave the timestamp off
         *
         * @return this builder
         */
        public Builder setIncludeTimestamp(final boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
            return this;
        }

        /**
         * Creates the {@link JsonEventFormatter}.
         *
         * @return the newly created formatter
         */
        public JsonEventFormatter build() {
            final Map<String, Object> constantValues = (this.constantValues == null ? Collections.emptyMap() : new LinkedHashMap<>(this.constantValues));
            final String timestampKey = (this.timestampKey == null ? "timestamp" : this.timestampKey);
            final DateTimeFormatter formatter = (this.formatter == null ? DateTimeFormatter.ISO_OFFSET_DATE_TIME : this.formatter);
            final ZoneId zoneId = (this.zoneId == null ? ZoneId.systemDefault() : this.zoneId);
            return new JsonEventFormatter(constantValues, timestampKey, formatter.withZone(zoneId), includeTimestamp);
        }
    }
}
