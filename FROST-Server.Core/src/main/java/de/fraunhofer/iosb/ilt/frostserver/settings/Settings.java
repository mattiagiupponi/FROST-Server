/*
 * Copyright (C) 2024 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.settings;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A settings-holder.
 */
public class Settings {

    private static final Logger LOGGER = LoggerFactory.getLogger(Settings.class);
    private static final String NOT_SET_USING_DEFAULT_VALUE = "Not set {}{}, using default value '{}'.";
    private static final String NOT_SET_NO_DEFAULT_VALUE = "Not set {}, and no default value!";
    private static final String ERROR_GETTING_SETTINGS_VALUE = "error getting settings value";
    private static final String SETTING_HAS_VALUE = "Setting {}{} has value '{}'.";
    private static final String HIDDEN_VALUE = "*****";

    private final Properties properties;
    private boolean logSensitiveData;
    private String prefix;

    private static Properties addEnvironment(Properties wrapped) {
        Map<String, String> environment = System.getenv();
        Properties wrapper = new Properties(wrapped);

        Map<String, String> sortedEnv = new TreeMap<>(environment);
        for (Map.Entry<String, String> entry : sortedEnv.entrySet()) {
            String key = entry.getKey().replace('_', '.');
            LOGGER.debug("Added environment variable: {}", key);
            wrapper.setProperty(key, entry.getValue());
        }
        return wrapper;
    }

    /**
     * Creates a new settings, containing only environment variables.
     */
    public Settings() {
        this(new Properties(), "", true, false);
    }

    /**
     * Creates a new settings, containing the given properties, and environment
     * variables, with no prefix.
     *
     * @param properties The properties to use. These can be overridden by
     * environment variables.
     */
    public Settings(Properties properties) {
        this(properties, "", true, false);
    }

    /**
     * Creates a new settings, containing the given properties, and environment
     * variables, with the given prefix.
     *
     * @param properties The properties to use.
     * @param prefix The prefix to use.
     * @param wrapInEnvironment Flag indicating if environment variables can
     * override the given properties.
     * @param logSensitiveData Flag indicating things like passwords should be
     * logged completely, not hidden.
     */
    public Settings(Properties properties, String prefix, boolean wrapInEnvironment, boolean logSensitiveData) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must be non-null");
        }
        if (wrapInEnvironment) {
            this.properties = addEnvironment(properties);
        } else {
            this.properties = properties;
        }
        this.prefix = (prefix == null ? "" : prefix);
        this.logSensitiveData = logSensitiveData;
    }

    /**
     * Get the prefix used in this Settings.
     *
     * @return The prefix used in this Settings.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Get the properties used in this Settings. This is the properties
     * configured when creating this Settings, optionally wrapped in a
     * properties containing all environment variables.
     *
     * @return The properties used in this Settings.
     */
    public Properties getProperties() {
        return properties;
    }

    public Settings getSubSettings(String prefix) {
        return new CachedSettings(this, prefix);
    }

    public boolean getLogSensitiveData() {
        return logSensitiveData;
    }

    public void setLogSensitiveData(boolean logSensitiveData) {
        this.logSensitiveData = logSensitiveData;
    }

    /**
     * Get the key as it is used in the config file or environment variables,
     * for the property with the given name. The key is the name, with the
     * prefix prepended to it.
     *
     * @param propertyName The name to get the key for.
     * @return prefix + propertyName
     */
    private String getPropertyKey(String propertyName) {
        return prefix + propertyName.replace('_', '.');
    }

    /**
     * Check if there is a property with the given name. The prefix is prepended
     * to the name before lookup.
     *
     * @param name The name to look up.
     * @return True if there is a property with the given name.
     */
    public boolean containsName(String name) {
        // properties.containsKey ignores properties defaults
        String val = properties.getProperty(getPropertyKey(name));
        return val != null;
    }

    /**
     * Check if the given key is present. Throws a PropertyMissingException if
     * not.
     *
     * @param key The key to look up.
     */
    private void checkExists(String key) {
        if (properties.getProperty(key) != null) {
            return;
        }
        LOGGER.error(NOT_SET_NO_DEFAULT_VALUE, key);
        throw new PropertyMissingException(key);
    }

    public void set(String name, String value) {
        properties.put(getPropertyKey(name), value);
    }

    public void set(String name, boolean value) {
        properties.put(getPropertyKey(name), Boolean.toString(value));
    }

    /**
     * Get the property with the given name, prefixed with the prefix of this
     * properties. The value of the property will be logged. Use {@link #getSensitive(String)
     * } to fetch a sensitive value.
     *
     * @param name The name of the property to get. The prefix will be prepended
     * to this name.
     * @return The value of the requested property. Throws a
     * PropertyMissingException if the property is not found.
     */
    public String get(String name) {
        return get(name, false);
    }

    /**
     * Get the property with the given name, prefixed with the prefix of this
     * properties. The value of the property will NOT be logged.
     *
     * @param name The name of the property to get. The prefix will be prepended
     * to this name.
     * @return The value of the requested property. Throws a
     * PropertyMissingException if the property is not found.
     */
    public String getSensitive(String name) {
        return get(name, true);
    }

    private String get(String name, boolean sensitiveValue) {
        String key = getPropertyKey(name);
        checkExists(key);
        String value = properties.getProperty(key);
        logHasValue(name, value, sensitiveValue);
        return value;
    }

    /**
     * Get the property with the given name, prefixed with the prefix of this
     * properties. The value of the property will be logged. Use {@link #getSensitive(String)
     * } to fetch a sensitive value.
     *
     * @param name The name of the property to get. The prefix will be prepended
     * to this name.
     * @param defaultValue The default value to use when the property is not
     * set.
     * @return The value of the requested property.
     */
    public String get(String name, String defaultValue) {
        return get(name, defaultValue, false);
    }

    /**
     * Get the property with the given name, prefixed with the prefix of this
     * properties. The value of the property will NOT be logged.
     *
     * @param name The name of the property to get. The prefix will be prepended
     * to this name.
     * @param defaultValue The default value to use when the property is not
     * set.
     * @return The value of the requested property.
     */
    public String getSensitive(String name, String defaultValue) {
        return get(name, defaultValue, true);
    }

    private String get(String name, String defaultValue, boolean sensitive) {
        String key = getPropertyKey(name);
        String value = properties.getProperty(key);
        if (value == null) {
            logDefaultValue(name, defaultValue, sensitive);
            return defaultValue;
        }
        logHasValue(name, value, sensitive);
        return value;
    }

    /**
     * Get the property with the given name, using the defaultsProvider to
     * provide a default value and the sensitivity status of the property. For
     * properties tagged as sensitive, the value will not be logged.
     *
     * @param name The name of the property to get. The prefix will be prepended
     * to this name.
     * @param defaultsProvider The provider for default values and sensitivity
     * information.
     * @return The configured value, or a default value.
     */
    public String get(String name, Class<? extends ConfigDefaults> defaultsProvider) {
        final String key = getPropertyKey(name);
        final String value = properties.getProperty(key);
        final boolean sensitive = ConfigUtils.isSensitive(defaultsProvider, name);
        if (value == null) {
            final String defaultValue = ConfigUtils.getDefaultValue(defaultsProvider, name);
            logDefaultValue(name, defaultValue, sensitive);
            return defaultValue;
        }
        logHasValue(name, value, sensitive);
        return value;
    }

    public int getInt(String name) {
        try {
            return Integer.parseInt(get(name));
        } catch (NumberFormatException ex) {
            throw new PropertyTypeException(name, Integer.class, ex);
        }
    }

    public int getInt(String name, int defaultValue) {
        if (containsName(name)) {
            try {
                return getInt(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    public int getInt(String name, Class<? extends ConfigDefaults> defaultsProvider) {
        if (containsName(name)) {
            try {
                return getInt(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        int defaultValue = ConfigUtils.getDefaultValueInt(defaultsProvider, name);
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    public long getLong(String name) {
        try {
            return Long.parseLong(get(name));
        } catch (NumberFormatException ex) {
            throw new PropertyTypeException(name, Long.class, ex);
        }
    }

    public long getLong(String name, long defaultValue) {
        if (containsName(name)) {
            try {
                return getLong(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    public long getLong(String name, Class<? extends ConfigDefaults> defaultsProvider) {
        if (containsName(name)) {
            try {
                return getLong(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        int defaultValue = ConfigUtils.getDefaultValueInt(defaultsProvider, name);
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;

    }

    public double getDouble(String name) {
        try {
            return Double.parseDouble(get(name));
        } catch (NumberFormatException ex) {
            throw new PropertyTypeException(name, Double.class, ex);
        }
    }

    public double getDouble(String name, double defaultValue) {
        if (containsName(name)) {
            try {
                return getDouble(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    public double getDouble(String name, Class<? extends ConfigDefaults> defaultsProvider) {
        if (containsName(name)) {
            try {
                return getDouble(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        double defaultValue = ConfigUtils.getDefaultValueDouble(defaultsProvider, name);
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;

    }

    public boolean getBoolean(String name) {
        try {
            return Boolean.parseBoolean(get(name));
        } catch (PropertyMissingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PropertyTypeException(name, Boolean.class, ex);
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        if (containsName(name)) {
            try {
                return getBoolean(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    public boolean getBoolean(String name, Class<? extends ConfigDefaults> defaultsProvider) {
        if (containsName(name)) {
            try {
                return getBoolean(name);
            } catch (Exception ex) {
                LOGGER.trace(ERROR_GETTING_SETTINGS_VALUE, ex);
            }
        }
        boolean defaultValue = ConfigUtils.getDefaultValueBoolean(defaultsProvider, name);
        LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        return defaultValue;
    }

    private void logHasValue(String name, String value, boolean sensitive) {
        if (!sensitive || logSensitiveData) {
            LOGGER.info(SETTING_HAS_VALUE, prefix, name, value);
        } else {
            LOGGER.info(SETTING_HAS_VALUE, prefix, name, HIDDEN_VALUE);
        }
    }

    private void logDefaultValue(String name, String defaultValue, boolean sensitive) {
        if (!sensitive || logSensitiveData) {
            LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, defaultValue);
        } else {
            LOGGER.info(NOT_SET_USING_DEFAULT_VALUE, prefix, name, HIDDEN_VALUE);
        }
    }

}
