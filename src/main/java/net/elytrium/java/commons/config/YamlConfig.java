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
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

// TODO: Customizable placeholders, more docs
public class YamlConfig {

  private final Yaml yaml = new Yaml();

  private Logger logger = LoggerFactory.getLogger(YamlConfig.class);

  private boolean enablePrefix = true;
  private String oldPrefix = "";
  private String currentPrefix = "";

  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  public LoadResult reload(@NonNull File configFile) {
    return this.reload(configFile, null);
  }

  public LoadResult reload(@NonNull File configFile, @Nullable String prefix) {
    LoadResult result = this.load(configFile, prefix);
    switch (result) {
      case SUCCESS: {
        // TODO: Save only if needed.
        this.save(configFile);
        break;
      }
      case FAIL:
      case CONFIG_NOT_EXISTS: {
        this.save(configFile);
        this.load(configFile, prefix); // Load again, because it now exists.
        break;
      }
      default: {
        throw new AssertionError("Invalid Result.");
      }
    }

    return result;
  }

  public LoadResult load(@NonNull File configFile, @Nullable String prefix) {
    if (prefix == null || this.isBlank(prefix)) {
      this.enablePrefix = false;
    } else {
      this.enablePrefix = true;
      this.oldPrefix = this.currentPrefix.isEmpty() ? prefix : this.currentPrefix;
      this.currentPrefix = prefix;
    }

    if (!configFile.exists()) {
      return LoadResult.CONFIG_NOT_EXISTS;
    }

    Path configPath = configFile.toPath();
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", "_").replace(":", ".");
    now = now.substring(0, now.lastIndexOf("."));
    try (InputStream fileInputStream = Files.newInputStream(configPath)) {
      this.processMap(this.yaml.load(fileInputStream), "", configFile, now);
    } catch (Throwable t) {
      try {
        File configFileCopy = new File(configFile.getParent(), configFile.getName() + "_invalid_" + now);
        Files.copy(configPath, configFileCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);

        this.logger.warn("Unable to load config. File was copied to {}", configFileCopy.getName(), t);
      } catch (Exception e) {
        this.logger.warn("Unable to load config and to make a copy.", e);
      }

      return LoadResult.FAIL;
    }

    return LoadResult.SUCCESS;
  }

  private void processMap(Map<String, Object> input, String oldPath, File configFile, String now) {
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String key = oldPath + (oldPath.isEmpty() ? oldPath : ".") + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String) {
        String stringValue = ((String) value).replace("{NL}", "\n");
        if (key.equalsIgnoreCase("prefix")) {
          if (this.isBlank(stringValue)) {
            this.enablePrefix = false;
          } else if (!this.currentPrefix.equals(value)) {
            this.enablePrefix = true;
            this.currentPrefix = stringValue;
          }
        }

        this.setFieldByKey(key, this.enablePrefix ? stringValue.replace("{PRFX}", this.currentPrefix) : stringValue, configFile, now);
      } else {
        this.setFieldByKey(key, value, configFile, now);
      }
    }
  }

  private boolean isBlank(String value) {
    return value.trim().isEmpty();
  }

  /**
   * Sets the value of a specific node. Probably throws some error if you supply non-existing keys or invalid values.
   *
   * @param key   The config node.
   * @param value The value.
   */
  @SuppressWarnings("unchecked")
  private void setFieldByKey(String key, Object value, File configFile, String now) {
    String[] split = key.split("\\.");
    Object instance = this.getInstance(split, this.getClass());
    if (instance != null) {
      Field field = this.getField(split, instance);
      if (field != null) {
        try {
          if (field.getType() != Map.class && value instanceof Map) {
            this.processMap((Map<String, Object>) value, key, configFile, now);
          } else if (field.getAnnotation(Final.class) == null) {
            if (field.getType() == String.class && !(value instanceof String)) {
              value = String.valueOf(value);
            }

            this.setField(field, instance, value);
          }

          return;
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    }

    this.logger.debug("Failed to set config option: " + key + ": " + value + " | " + instance);
    File configFileBackup = new File(configFile.getParent(), configFile.getName() + "_backup_" + now);
    if (!configFileBackup.exists()) {
      try {
        Files.copy(configFile.toPath(), configFileBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        this.logger.warn("Unable to load some of the config options. File was copied to {}", configFileBackup.getName());
      } catch (Throwable t) {
        this.logger.warn("Unable to load some of the config options and to make a copy.", t);
      }
    }
  }

  /**
   * Gets the instance for a specific config node.
   *
   * @param split The node. (split by period)
   * @return The instance or null.
   */
  @Nullable
  // TODO: Rewrite
  private Object getInstance(String[] split, Class<?> clazz) {
    try {
      Object instance = this;
      while (split.length > 0) {
        if (split.length == 1) {
          return instance;
        } else {
          Class<?> found = null;

          for (Class<?> current : clazz.getDeclaredClasses()) {
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
            //
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
      Path parent = configFile.toPath().getParent();
      if (!configFile.exists() && parent != null) {
        Files.createDirectories(parent);

        configFile.createNewFile();
      }

      PrintWriter writer = new PrintWriter(configFile, "UTF-8");
      this.writeConfigKeyValue(writer, this.getClass(), this, 0);
      writer.close();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private void writeConfigKeyValue(PrintWriter writer, Class<?> clazz, Object instance, int indent) {
    try {
      String lineSeparator = System.lineSeparator();
      String spacing = this.getSpacing(indent);

      for (Field field : clazz.getFields()) {
        if (field.getAnnotation(Ignore.class) != null) {
          continue;
        }

        Class<?> current = field.getType();
        if (current.getAnnotation(Ignore.class) != null) {
          continue;
        }

        this.writeNewLines(field.getAnnotation(NewLine.class), writer, lineSeparator);

        Comment[] comments = field.getAnnotationsByType(Comment.class);
        this.writePrependComments(comments, writer, spacing, lineSeparator);

        if (field.getAnnotation(Create.class) != null) {
          this.writeNewLines(current.getAnnotation(NewLine.class), writer, lineSeparator);

          if (indent == 0) {
            writer.write(lineSeparator);
          }

          comments = current.getAnnotationsByType(Comment.class);
          this.writePrependComments(comments, writer, spacing, lineSeparator);

          writer.write(spacing + this.toNodeName(current.getSimpleName()) + ":");

          this.writeComments(comments, writer, lineSeparator, spacing + "  ");

          field.setAccessible(true);
          Object value = field.get(instance);

          if (value == null) {
            value = current.getDeclaredConstructor().newInstance();
            this.setField(field, instance, value);
          }

          this.writeConfigKeyValue(writer, current, value, indent + 2);
        } else {
          String fieldName = field.getName();
          String fieldValue = this.toYamlString(field.get(instance), fieldName, lineSeparator, spacing);
          writer.write(spacing + this.toNodeName(fieldName) + (fieldValue.contains(lineSeparator) ? ":" : ": ") + fieldValue);

          this.writeComments(comments, writer, lineSeparator, spacing);
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private String getSpacing(int indent) {
    if (indent == 0) {
      return "";
    } else if (indent == 1) {
      return " ";
    } else {
      byte[] spacing = new byte[indent];
      Arrays.fill(spacing, (byte) 0x20); // 0x20 == ' '
      return new String(spacing, StandardCharsets.UTF_8);
    }
  }

  private void writeNewLines(@Nullable NewLine newLine, PrintWriter writer, String lineSeparator) {
    if (newLine != null) {
      for (int i = 0; i < newLine.amount(); ++i) {
        writer.write(lineSeparator);
      }
    }
  }

  private void writePrependComments(Comment[] comments, PrintWriter writer, String spacing, String lineSeparator) {
    for (Comment comment : comments) {
      if (comment.at().equals(Comment.At.PREPEND)) {
        for (String commentLine : comment.value()) {
          writer.write(spacing + "# " + commentLine.replace("\n", lineSeparator) + lineSeparator);
        }
      }
    }
  }

  private void writeComments(Comment[] comments, PrintWriter writer, String lineSeparator, String spacing) {
    int commentsAmount = comments.length;
    if (commentsAmount == 0) {
      writer.write(lineSeparator);
    } else {
      boolean sameLineHasWritten = false;
      boolean separatorHasWritten = false;
      for (Comment comment : comments) {
        Comment.At at = comment.at();
        String[] value = comment.value();
        if (value == null || value.length == 0 || at.equals(Comment.At.PREPEND)) {
          if (!separatorHasWritten) {
            writer.write(lineSeparator);
            separatorHasWritten = true;
          }
        } else if (at.equals(Comment.At.SAME_LINE)) {
          if (!sameLineHasWritten) {
            writer.write(" # " + value[0].replace("\n", lineSeparator));
            writer.write(lineSeparator);
            sameLineHasWritten = true;
            separatorHasWritten = true;
          }
        } else if (at.equals(Comment.At.APPEND)) {
          if (!separatorHasWritten) {
            writer.write(lineSeparator);
            separatorHasWritten = true;
          }
          for (String commentLine : value) {
            writer.write(spacing + "# " + commentLine.replace("\n", lineSeparator) + lineSeparator);
          }
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
  private String toNodeName(String fieldName) {
    return fieldName.toLowerCase(Locale.ROOT).replace("_", "-");
  }

  @SuppressWarnings("unchecked")
  private String toYamlString(Object value, String fieldName, String lineSeparator, String spacing) {
    if (value instanceof Map) {
      Map<String, ?> map = (Map<String, ?>) value;
      if (map.isEmpty()) {
        return "{}";
      }

      StringBuilder builder = new StringBuilder();
      map.forEach((key, mapValue) ->
          builder
              .append(lineSeparator)
              .append(spacing).append("  ")
              .append(key).append(": ")
              .append(this.toYamlString(mapValue, key, lineSeparator, spacing))
      );

      return builder.toString();
    } else if (value instanceof List) {
      List<?> listValue = (List<?>) value;
      if (listValue.isEmpty()) {
        return "[]";
      }

      StringBuilder builder = new StringBuilder();
      listValue.forEach(obj ->
          builder.append(lineSeparator).append(spacing).append("  - ").append(this.toYamlString(obj, fieldName, lineSeparator, spacing))
      );

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
    } else {
      return String.valueOf(value);
    }
  }

  public enum LoadResult {

    SUCCESS,
    FAIL,
    CONFIG_NOT_EXISTS
  }

  /**
   * Indicates that a field should be instantiated / created.
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface Create {

  }

  /**
   * Indicates that a field cannot be modified.
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface Final {

  }

  /**
   * Creates a new line for better formatting.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface NewLine {

    int amount() default 1;
  }

  /**
   * Comments holder.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface CommentsHolder {

    Comment[] value();
  }

  /**
   * Creates a comment.
   */
  @Target({
      ElementType.FIELD,
      ElementType.TYPE
  })
  @Repeatable(CommentsHolder.class)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface Comment {

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
  protected @interface Ignore {

  }
}
