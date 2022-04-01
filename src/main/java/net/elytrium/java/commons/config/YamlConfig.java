/*
 * Copyright (C) 2022 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.java.commons.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

// TODO: Backups, customizable placeholders, more docs
public class YamlConfig {

  private final Logger logger = LoggerFactory.getLogger(YamlConfig.class);

  private boolean enablePrefix = true;
  private String oldPrefix = "";
  private String currentPrefix = "";

  public boolean reload(@NonNull File configFile) {
    return this.reload(configFile, null);
  }

  public boolean reload(@NonNull File configFile, @Nullable String prefix) {
    if (this.load(configFile, prefix)) {
      this.save(configFile);
      return false;
    } else {
      this.save(configFile);
      this.load(configFile, prefix);
      return true;
    }
  }

  public boolean load(@NonNull File configFile, @Nullable String prefix) {
    if (prefix == null) {
      this.enablePrefix = false;
    }
    if (this.enablePrefix) {
      this.oldPrefix = this.currentPrefix.isEmpty() ? prefix : this.currentPrefix;
      this.currentPrefix = prefix;
    }
    if (!configFile.exists()) {
      return false;
    }

    try {
      this.set(new Yaml().load(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)), "");
    } catch (IOException e) {
      this.logger.warn("Unable to load config.", e);
      return false;
    }

    return true;
  }

  private void set(Map<String, Object> input, String oldPath) {
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String key = oldPath + (oldPath.isEmpty() ? "" : ".") + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String) {
        String stringValue = ((String) value).replace("{NL}", "\n");
        if (this.enablePrefix && key.equalsIgnoreCase("prefix") && !this.currentPrefix.equals(value)) {
          this.currentPrefix = stringValue;
        }

        this.set(key, this.enablePrefix ? stringValue.replace("{PRFX}", this.currentPrefix) : stringValue, this.getClass());
      } else {
        this.set(key, value, this.getClass());
      }
    }
  }

  /**
   * Sets the value of a specific node. Probably throws some error if you supply non-existing keys or invalid values.
   *
   * @param key   The config node.
   * @param value The value.
   */
  @SuppressWarnings("unchecked")
  private void set(String key, Object value, Class<?> root) {
    String[] split = key.split("\\.");
    Object instance = this.getInstance(split, root);
    if (instance != null) {
      Field field = this.getField(split, instance);
      if (field != null) {
        try {
          if (field.getType() != Map.class && value instanceof Map) {
            this.set((Map<String, Object>) value, key);
            return;
          }

          if (field.getAnnotation(Final.class) != null) {
            return;
          }
          if (field.getType() == String.class && !(value instanceof String)) {
            value = value.toString();
          }
          this.setField(field, instance, value);
          return;
        } catch (Throwable t) {
          t.printStackTrace();
        }
      } else if (value instanceof Map) {
        this.set((Map<String, Object>) value, key);
      }
    }

    this.logger.debug("Failed to set config option: " + key + ": " + value + " | " + instance + " | " + root.getSimpleName());
  }

  /**
   * Gets the instance for a specific config node.
   *
   * @param split The node (split by period).
   * @return The instance or null.
   */
  @Nullable
  private Object getInstance(String[] split, Class<?> root) {
    try {
      Class<?> clazz = root == null ? MethodHandles.lookup().lookupClass() : root;
      Object instance = this;
      while (split.length > 0) {
        if (split.length == 1) {
          return instance;
        } else {
          Class<?> found = null;
          if (clazz == null) {
            return null;
          }

          Class<?>[] classes = clazz.getDeclaredClasses();
          for (Class<?> current : classes) {
            if (Objects.equals(current.getSimpleName(), this.toFieldName(split[0]))) {
              found = current;
              break;
            }
          }

          if (found == null) {
            return null;
          }

          try {
            Field instanceField = clazz.getDeclaredField(this.toFieldName(split[0]));
            instanceField.setAccessible(true);
            Object value = instanceField.get(instance);
            if (value == null) {
              value = found.getDeclaredConstructor().newInstance();
              this.setField(instanceField, instance, value);
            }

            clazz = found;
            instance = value;
            split = Arrays.copyOfRange(split, 1, split.length);
            continue;
          } catch (NoSuchFieldException e) {
            // TODO: Make backup
          }

          split = Arrays.copyOfRange(split, 1, split.length);
          clazz = found;
          instance = clazz.getDeclaredConstructor().newInstance();
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }

    return null;
  }

  /**
   * Gets the field for a specific config node and instance.
   *
   * <p>As expiry can have multiple blocks there will be multiple instances.
   *
   * @param split    The node (split by period).
   * @param instance The instance.
   */
  @Nullable
  private Field getField(String[] split, Object instance) {
    try {
      Field field = instance.getClass().getField(this.toFieldName(split[split.length - 1]));
      field.setAccessible(true);
      return field;
    } catch (Throwable t) {
      this.logger.debug("Invalid config field: " + String.join(".", split) + " for " + this.toNodeName(instance.getClass().getSimpleName()));
      return null;
    }
  }

  /**
   * Converts the field name to the config node format.
   */
  private String toFieldName(String node) {
    return node.toUpperCase(Locale.ROOT).replaceAll("-", "_");
  }

  /**
   * Sets all values in the file (load first to avoid overwriting).
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
  public void save(@NonNull File configFile) {
    try {
      if (!configFile.exists()) {
        File parent = configFile.getParentFile();
        if (parent != null) {
          configFile.getParentFile().mkdirs();
        }

        configFile.createNewFile();
      }

      PrintWriter writer = new PrintWriter(configFile, "UTF-8");
      this.save(writer, this.getClass(), this, 0);
      writer.close();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private void save(PrintWriter writer, Class<?> clazz, Object instance, int indent) {
    try {
      String lineSeparator = System.lineSeparator();
      String spacing = String.join("", Collections.nCopies(indent, " "));

      for (Field field : clazz.getFields()) {
        if (field.getAnnotation(Ignore.class) != null) {
          continue;
        }
        Class<?> current = field.getType();
        if (field.getAnnotation(Ignore.class) != null) {
          continue;
        }

        if (field.getAnnotation(NewLine.class) != null) {
          writer.write(lineSeparator);
        }

        Comment comment = field.getAnnotation(Comment.class);
        if (comment != null && comment.at().equals(Comment.At.PREPEND)) {
          for (String commentLine : comment.value()) {
            writer.write(spacing + "# " + commentLine.replace("\n", lineSeparator) + lineSeparator);
          }
        }

        Create create = field.getAnnotation(Create.class);
        if (create != null) {
          Object value = field.get(instance);
          field.setAccessible(true);

          if (current.getAnnotation(NewLine.class) != null) {
            writer.write(lineSeparator);
          }

          if (indent == 0) {
            writer.write(lineSeparator);
          }

          comment = current.getAnnotation(Comment.class);
          if (comment != null && comment.at().equals(Comment.At.PREPEND)) {
            for (String commentLine : comment.value()) {
              writer.write(spacing + "# " + commentLine + lineSeparator);
            }
          }

          writer.write(spacing + this.toNodeName(current.getSimpleName()) + ":");

          this.writeComments(comment, writer, lineSeparator, spacing + "  ");

          if (value == null) {
            this.setField(field, instance, value = current.getDeclaredConstructor().newInstance());
          }

          this.save(writer, current, value, indent + 2);
        } else {
          writer.write(spacing + this.toNodeName(field.getName() + ": ") + this.toYamlString(field.get(instance), spacing, field.getName()));

          this.writeComments(comment, writer, lineSeparator, spacing);
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private void writeComments(@Nullable Comment comment, PrintWriter writer, String lineSeparator, String spacing) {
    if (comment == null) {
      writer.write(lineSeparator);
    } else {
      Comment.At at = comment.at();
      String[] value = comment.value();
      if (at.equals(Comment.At.PREPEND)) {
        writer.write(lineSeparator);
      } else if (at.equals(Comment.At.SAME_LINE)) {
        writer.write(" # " + value[0].replace("\n", lineSeparator));
        writer.write(lineSeparator);
      } else if (at.equals(Comment.At.APPEND)) {
        writer.write(lineSeparator);
        for (String commentLine : value) {
          writer.write(spacing + "# " + commentLine.replace("\n", lineSeparator) + lineSeparator);
        }
      }
    }
  }

  private void setField(Field field, Object owner, Object value) throws IllegalAccessException {
    int modifiers = field.getModifiers();
    if (Modifier.isStatic(modifiers)) {
      throw new IllegalStateException("This field shouldn't be static.");
    } else if (Modifier.isFinal(modifiers)) {
      throw new IllegalStateException("This field shouldn't be final.");
    } else {
      field.set(owner, value);
    }
  }

  /**
   * Translate a field to a config node.
   */
  private String toNodeName(String field) {
    return field.toLowerCase(Locale.ROOT).replace("_", "-");
  }

  @SuppressWarnings("unchecked")
  private String toYamlString(Object value, String spacing, String fieldName) {
    if (value instanceof Map) {
      Map<String, ?> map = (Map<String, ?>) value;
      if (map.isEmpty()) {
        return "{}";
      }

      StringBuilder builder = new StringBuilder();
      map.forEach((key, mapValue) ->
          builder.append(System.lineSeparator()).append(spacing).append("  ").append(key).append(": ").append(this.toYamlString(mapValue, spacing, key))
      );

      return builder.toString();
    } else if (value instanceof List) {
      Collection<?> listValue = (Collection<?>) value;
      if (listValue.isEmpty()) {
        return "[]";
      }

      StringBuilder builder = new StringBuilder();
      listValue.forEach(obj -> builder.append(System.lineSeparator()).append(spacing).append("  - ").append(this.toYamlString(obj, spacing, fieldName)));

      return builder.toString();
    } else if (value instanceof String) {
      String stringValue = (String) value;
      if (stringValue.isEmpty()) {
        return "\"\"";
      }

      String quoted = ("\"" + stringValue + "\"").replace("\n", "{NL}");
      if (!this.enablePrefix || fieldName.equalsIgnoreCase("prefix")) {
        return quoted;
      } else {
        return quoted.replace(this.currentPrefix.equals(this.oldPrefix) ? this.oldPrefix : this.currentPrefix, "{PRFX}");
      }
    }

    return String.valueOf(value);
  }

  /**
   * Indicates that a field should be instantiated / created.
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Create {

  }

  /**
   * Indicates that a field cannot be modified.
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Final {

  }

  /**
   * Creates a new line for better formatting.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Retention(RetentionPolicy.RUNTIME)
  public @interface NewLine {

  }

  /**
   * Creates a comment.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Comment {

    String[] value();

    /**
     * Comment position.
     */
    At at() default At.PREPEND;

    enum At {

      /**
       * The comment will be placed before the field.
       *
       * <pre> {@code
       *   # Line1
       *   # Line2
       *   regular-field: "regular value"
       * } </pre>
       */
      PREPEND,
      /**
       * The comment will be placed on the same line with the field.
       *
       * <p>The comment text shouldn't have more than one line.
       *
       * <pre> {@code
       *   regular-field: "regular value" # Line1
       * } </pre>
       */
      SAME_LINE,
      /**
       * The comment will be placed after the field.
       *
       * <pre> {@code
       *   regular-field: "regular value"
       *   # Line1
       *   # Line2
       * } </pre>
       */
      APPEND
    }
  }

  /**
   * Any field or class with is not part of the config.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Ignore {

  }
}
