/*
 * Copyright (C) 2022 - 2023 Elytrium
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

package net.elytrium.commons.config;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public class YamlConfig {

  private final Yaml yaml = new Yaml();
  private YamlConfig original;
  private String prefix = null;
  private final List<Integer> placeholders = new LinkedList<>();
  private final Map<Class<? extends ConfigSerializer<?, ?>>, ConfigSerializer<?, ?>> cachedSerializers = new HashMap<>();
  private final Map<Class<?>, ConfigSerializer<?, ?>> registeredSerializers = new HashMap<>();

  private Logger logger = LoggerFactory.getLogger(YamlConfig.class);

  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  public <T, F> void registerSerializer(ConfigSerializer<T, F> configSerializer) {
    this.registeredSerializers.put(configSerializer.getToClass(), configSerializer);
  }

  public void registerSerializers(ConfigSerializerCollection configSerializerCollection) {
    for (ConfigSerializer<?, ?> configSerializer : configSerializerCollection.serializers()) {
      this.registerSerializer(configSerializer);
    }
  }

  public LoadResult reload(@NonNull File configFile) {
    return this.reload(configFile.toPath(), null);
  }

  public LoadResult reload(@NonNull Path configFile) {
    return this.reload(configFile, null);
  }

  public LoadResult reload(@NonNull File configFile, @Nullable String prefix) {
    return this.reload(configFile.toPath(), prefix);
  }

  public LoadResult reload(@NonNull Path configFile, @Nullable String prefix) {
    LoadResult result = this.load(configFile, prefix);
    switch (result) {
      case SUCCESS: {
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

  public LoadResult load(@NonNull File configFile) {
    return this.load(configFile.toPath(), null);
  }

  public LoadResult load(@NonNull File configFile, @Nullable String prefix) {
    return this.load(configFile.toPath(), prefix);
  }

  public LoadResult load(@NonNull Path configFile) {
    return this.load(configFile, null);
  }

  public LoadResult load(@NonNull Path configFile, @Nullable String prefix) {
    try {
      this.original = this.getClass().getDeclaredConstructor().newInstance();
    } catch (InstantiationException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Unable to create new instance of " + this.getClass().getName());
    }
    if (!Files.exists(configFile)) {
      return LoadResult.CONFIG_NOT_EXISTS;
    }

    this.dispose();

    this.prefix = prefix;

    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", "_").replace(":", ".");
    now = now.substring(0, now.lastIndexOf("."));
    try (InputStream fileInputStream = Files.newInputStream(configFile)) {
      Map<String, Object> data = this.yaml.load(fileInputStream);

      if (data != null && !data.isEmpty()) {
        this.processMap(data, this.original, "", null, now, false);
        this.processMap(data, this, "", configFile, now, true);
      }
    } catch (Throwable t) {
      try {
        Path parent = configFile.getParent();
        if (parent == null) {
          throw new NullPointerException("Config parent path is null for " + configFile);
        }

        String newFileName = configFile.getFileName() + "_invalid_" + now;
        Path configFileCopy = parent.resolve(newFileName);
        Files.copy(configFile, configFileCopy, StandardCopyOption.REPLACE_EXISTING);

        this.logger.warn("Unable to load config. File was copied to {}", newFileName, t);
      } catch (Exception e) {
        this.logger.warn("Unable to load config and to make a copy.", e);
      }

      return LoadResult.FAIL;
    }

    return LoadResult.SUCCESS;
  }

  private void processMap(Map<String, Object> input, Object instance, String oldPath, @Nullable Path configFile, String now, boolean usePrefix) {
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      String key = oldPath + (oldPath.isEmpty() ? oldPath : ".") + entry.getKey();
      Object value = entry.getValue();

      if (value instanceof String) {
        String stringValue = ((String) value).replace("{NL}", "\n");
        if (usePrefix) {
          if (this.prefix != null) {
            stringValue = stringValue.replace("{PRFX}", this.prefix);
          }

          value = stringValue;

          if (key.equals("prefix")) {
            this.prefix = stringValue;
          }
        }
      }

      this.setFieldByKey(key, instance, value, configFile, now, usePrefix);
    }
  }

  /**
   * Sets the value of a specific node. Probably throws some error if you supply non-existing keys or invalid values.
   *
   * @param key   The config node.
   * @param value The value.
   */
  @SuppressWarnings("unchecked")
  private void setFieldByKey(String key, Object dest, Object value, @Nullable Path configFile, String now, boolean usePrefix) {
    String[] split = key.split("\\.");
    Object instance = this.getInstance(dest, split);
    if (instance != null) {
      Field field = this.getField(split, instance);
      if (field != null) {
        try {
          if (field.getType() != Map.class && value instanceof Map) {
            this.processMap((Map<String, Object>) value, dest, key, configFile, now, usePrefix);
          } else if (field.getAnnotation(Final.class) == null) {
            if (field.getType() == String.class && !(value instanceof String)) {
              value = String.valueOf(value);
            } else if (usePrefix && field.getAnnotation(Placeholders.class) != null) {
              if (field.getType() != String.class) {
                throw new IllegalAccessException(field.getType() + " is incompatible with placeholders");
              }
              Placeholders placeholders = field.getAnnotation(Placeholders.class);
              int hash = net.elytrium.commons.config.Placeholders.addPlaceholders(value, placeholders.value());
              this.placeholders.add(hash);
            } else if (field.getGenericType() instanceof ParameterizedType) {
              if (field.getType() == Map.class && value instanceof Map) {
                Type parameterType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1];
                if (parameterType instanceof Class<?>) {
                  Class<?> parameter = (Class<?>) parameterType;
                  if (this.isNodeMapping(parameter)) {
                    value = ((Map<String, ?>) value).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> this.createNodeSequence(parameter, e.getValue(), usePrefix)));
                  }
                }
              } else if (field.getType() == List.class && value instanceof List) {
                Type parameterType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                if (parameterType instanceof Class<?>) {
                  Class<?> parameter = (Class<?>) parameterType;
                  if (this.isNodeMapping(parameter)) {
                    value = ((List<?>) value).stream()
                        .map(obj -> this.createNodeSequence(parameter, obj, usePrefix))
                        .collect(Collectors.toList());
                  }
                }
              }
            }

            this.setField(field, instance, value);
          }

          return;
        } catch (Throwable t) {
          throw new ConfigLoadException(t);
        }
      }
    }

    this.logger.debug("Failed to set config option: " + key + ": " + value + " | " + instance);
    if (configFile != null) {
      Path parent = configFile.getParent();
      if (parent == null) {
        throw new NullPointerException("Config parent path is null for " + configFile);
      }

      Path configFileBackup = parent.resolve(configFile.getFileName() + "_backup_" + now);
      if (!Files.exists(configFileBackup)) {
        try {
          Files.copy(configFile, configFileBackup, StandardCopyOption.REPLACE_EXISTING);
          this.logger.warn("Unable to load some of the config options. File was copied to {}", configFileBackup.getFileName());
        } catch (Throwable t) {
          this.logger.warn("Unable to load some of the config options and to make a copy.", t);
        }
      }
    }
  }

  /**
   * Gets the instance for a specific config node.
   *
   * @param split The node. (split by period)
   * @return The instance.
   */
  private Object getInstance(@NonNull Object instance, String[] split) {
    try {
      for (int i = 0; i < split.length - 1; i++) {
        String name = this.toFieldName(split[i]);
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(instance);
        if (value == null) {
          value = field.getType().getDeclaredConstructor().newInstance();
          this.setField(field, instance, value);
        }
        instance = value;
      }
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("Unable to find field " + e.getMessage() + " in " + instance.getClass().getName());
    } catch (InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchMethodException e) {
      throw new IllegalStateException("Unable to create new instance: " + e.getMessage());
    }

    return instance;
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

  private boolean isNodeMapping(Class<?> cls) {
    return cls.getAnnotation(NodeSequence.class) != null
        || (!cls.isPrimitive() && !cls.isEnum() && !Number.class.isAssignableFrom(cls)
        && !Map.class.isAssignableFrom(cls) && !List.class.isAssignableFrom(cls)
        && !String.class.isAssignableFrom(cls));
  }

  public void save(@NonNull File configFile) {
    this.save(configFile.toPath());
  }

  /**
   * Sets all values in the file (load first to avoid overwriting).
   */
  public void save(@NonNull Path configFile) {
    try {
      Path parent = configFile.getParent();
      if (!Files.exists(configFile) && parent != null) {
        Files.createDirectories(parent);
        Files.createFile(configFile);
      }

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(configFile)), StandardCharsets.UTF_8));
      this.writeConfigKeyValue(writer, this.getClass(), this, this.original, 0, true);
      writer.close();
    } catch (Throwable t) {
      throw new ConfigSaveException(t);
    }
  }

  private void writeConfigKeyValue(PrintWriter writer, Class<?> clazz, Object instance, Object original, int indent, boolean usePrefix) {
    try {
      String lineSeparator = System.lineSeparator();
      String spacing = this.getSpacing(indent);

      for (Field field : clazz.getFields()) {
        if (field.getAnnotation(Ignore.class) != null || Modifier.isTransient(field.getModifiers())) {
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

          writer.write(spacing);
          writer.write(this.toNodeName(current.getSimpleName()));
          writer.write(':');

          this.writeComments(comments, writer, lineSeparator, spacing + "  ");

          field.setAccessible(true);
          Object value = field.get(instance);

          if (value == null) {
            value = current.getDeclaredConstructor().newInstance();
            this.setField(field, instance, value);
          }

          Object originalValue = field.get(original);

          if (originalValue == null) {
            originalValue = current.getDeclaredConstructor().newInstance();
            this.setField(field, original, originalValue);
          }

          this.writeConfigKeyValue(writer, current, value, originalValue, indent + 2, usePrefix);
        } else {
          String fieldName = field.getName();

          String fieldValue = this.toYamlString(field, field.get(instance), lineSeparator, spacing, usePrefix);
          String originalFieldValue = this.toYamlString(field, field.get(original), lineSeparator, spacing, usePrefix);
          String valueToWrite = fieldValue;

          if (this.prefix != null) {
            if (fieldValue.startsWith("\"") && fieldValue.endsWith("\"")) { // String
              if (fieldValue.replace("{PRFX}", this.prefix).equals(originalFieldValue.replace("{PRFX}", this.prefix))) {
                valueToWrite = originalFieldValue;
              }
            } else if (fieldValue.contains(lineSeparator)) { // Map/List
              StringBuilder builder = new StringBuilder();
              String[] lines = fieldValue.split(lineSeparator);
              String[] originalLines = originalFieldValue.split(lineSeparator);
              for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String toAppend = line;
                if (i < originalLines.length) {
                  String originalLine = originalLines[i];
                  if (line.replace("{PRFX}", this.prefix).equals(originalLine.replace("{PRFX}", this.prefix))) {
                    toAppend = originalLine;
                  }
                }
                builder.append(toAppend).append(lineSeparator);
              }
              builder.setLength(builder.length() - lineSeparator.length());
              valueToWrite = builder.toString();
            }
          }

          writer.write(spacing);
          writer.write(this.toNodeName(fieldName));
          writer.write((valueToWrite.contains(lineSeparator) ? ":" : ": "));
          writer.write(valueToWrite);

          this.writeComments(comments, writer, lineSeparator, spacing);
        }
      }
    } catch (Throwable t) {
      throw new ConfigSaveException(t);
    }
  }

  private String getSpacing(int indent) {
    return new String(new char[indent]).replace('\0', ' ');
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
          writer.write(spacing);
          writer.write("# ");
          writer.write(commentLine.replace("\n", lineSeparator));
          writer.write(lineSeparator);
        }
      }
    }
  }

  private void writeComments(Comment[] comments, PrintWriter writer, String lineSeparator, String spacing) {
    Map<Comment.At, List<Comment>> groups = Arrays.stream(comments).collect(Collectors.groupingBy(Comment::at));
    if (groups.containsKey(Comment.At.SAME_LINE)) {
      writer.write(" # ");
      writer.write(groups.get(Comment.At.SAME_LINE).get(0).value()[0]);
    }

    writer.write(lineSeparator);
    for (Comment comment : groups.getOrDefault(Comment.At.APPEND, Collections.emptyList())) {
      for (String commentLine : comment.value()) {
        writer.write(spacing + "# " + commentLine.replace("\n", lineSeparator) + lineSeparator);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void setField(Field field, Object owner, Object value)
      throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
    int modifiers = field.getModifiers();
    if (Modifier.isStatic(modifiers)) {
      throw new IllegalStateException("This field shouldn't be static.");
    } else if (Modifier.isFinal(modifiers)) {
      throw new IllegalStateException("This field shouldn't be final.");
    } else {
      if (field.getType() == Map.class && value instanceof Map) {
        if (((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0] != String.class) {
          throw new IllegalStateException("Key type of this map should be " + String.class);
        }
        value = ((Map<?, ?>) value).entrySet().stream()
            .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
      } else if (field.getType().isEnum()) {
        String stringValue = String.valueOf(value);
        if (stringValue.isEmpty() || stringValue.equals("null")) {
          value = null;
        } else {
          //noinspection rawtypes
          value = Enum.valueOf((Class<? extends Enum>) field.getType(), stringValue.toUpperCase(Locale.ROOT));
        }
      }

      ConfigSerializer<?, ?> configSerializer = this.registeredSerializers.get(field.getType());
      if (configSerializer != null) {
        value = configSerializer.deserializeRaw(value);
      }

      CustomSerializer customSerializer = field.getAnnotation(CustomSerializer.class);
      if (customSerializer != null) {
        value = this.getAndCacheSerializer(customSerializer).deserializeRaw(value);
      }

      field.set(owner, value);
    }
  }

  /**
   * Creates a new node sequence instance with unchanged fields.
   *
   * @param nodeSequenceClass Node class.
   */
  protected static <T> T createNodeSequence(Class<T> nodeSequenceClass) {
    try {
      Constructor<T> constructor = nodeSequenceClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor.newInstance();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Method not found: " + e.getMessage());
    } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
      throw new RuntimeException("Unable to create instance of " + nodeSequenceClass.getName());
    }
  }

  /**
   * Creates a new node sequence instance with specified field values.
   *
   * @param nodeSequenceClass Node class.
   * @param objects           Values.
   */
  @SuppressWarnings("unchecked")
  private <T> T createNodeSequence(Class<T> nodeSequenceClass, Object objects, boolean usePrefix) {
    if (!(objects instanceof Map)) {
      return (T) objects;
    }

    T instance = createNodeSequence(nodeSequenceClass);
    this.processMap((Map<String, Object>) objects, instance, "", null, null, usePrefix);
    return instance;
  }

  /**
   * Creates a new node sequence instance with specified field values.
   *
   * @param nodeSequenceClass Node class.
   * @param values            The values to be set for the fields, not including fields with {@link Final} and {@link Ignore} annotations.
   */
  protected static <T> T createNodeSequence(Class<T> nodeSequenceClass, Object... values) {
    try {
      T instance = createNodeSequence(nodeSequenceClass);
      Field[] fields = nodeSequenceClass.getDeclaredFields();
      int idx = 0;
      for (Field field : fields) {
        if (field.getAnnotation(Final.class) != null
            || field.getAnnotation(Ignore.class) != null
            || field.getType().getAnnotation(Ignore.class) != null
            || Modifier.isTransient(field.getModifiers())) {
          continue;
        }
        int modifiers = field.getModifiers();
        if (Modifier.isFinal(modifiers)) {
          throw new IllegalStateException("Field " + field.getName() + " can't be final");
        } else if (Modifier.isStatic(modifiers)) {
          throw new IllegalStateException("Field " + field.getName() + " can't be static");
        }
        field.setAccessible(true);
        Object value = idx >= values.length ? null : values[idx];
        if (field.getAnnotation(Create.class) != null && !field.getType().isInstance(value)) {
          field.set(instance, field.getType().getDeclaredConstructor().newInstance());
          continue;
        } else if (value == null) {
          continue;
        }
        field.set(instance, value);
        ++idx;
      }
      return instance;
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to set field: " + e.getMessage());
    } catch (InvocationTargetException | InstantiationException | NoSuchMethodException e) {
      throw new IllegalStateException("Unable to create new instance: " + e.getMessage());
    }
  }

  /**
   * Translate a field to a config node.
   */
  private String toNodeName(String fieldName) {
    if (fieldName.matches("^\\d+$")) {
      return '"' + fieldName + '"';
    }
    return fieldName.toLowerCase(Locale.ROOT).replace("_", "-");
  }

  private String toYamlString(Field field, Object value, String lineSeparator, String spacing, boolean usePrefix)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    return this.toYamlString(field, value, lineSeparator, spacing, false, false, 0, usePrefix);
  }

  private String toYamlString(Field field, Object value, String lineSeparator,
      String spacing, boolean isCollection, boolean isMap, int nested, boolean usePrefix)
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    CustomSerializer customSerializer = field.getAnnotation(CustomSerializer.class);
    if (customSerializer != null) {
      value = this.getAndCacheSerializer(customSerializer).serializeRaw(value);
    }

    ConfigSerializer<?, ?> configSerializer = this.registeredSerializers.get(field.getType());
    if (configSerializer != null) {
      value = configSerializer.serializeRaw(value);
    }

    if (value instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) value;
      if (map.isEmpty()) {
        return "{}";
      }

      StringBuilder builder = new StringBuilder();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        Object mapValue = entry.getValue();
        String data = this.toYamlString(field, mapValue, lineSeparator, spacing, true, true, 0, usePrefix);
        builder.append(lineSeparator)
            .append(spacing).append("  ")
            .append(this.toNodeName(String.valueOf(key))).append(data.startsWith(lineSeparator) ? ":" : ": ")
            .append(data);
      }

      return builder.toString();
    } else if (value instanceof List) {
      List<?> listValue = (List<?>) value;
      if (listValue.isEmpty()) {
        return "[]";
      }

      StringBuilder builder = new StringBuilder();
      boolean newLine = nested == 0;
      for (Object obj : listValue) {
        if (newLine) {
          builder.append(lineSeparator).append(spacing).append(this.getSpacing(2 + nested * 2));
        } else {
          newLine = true;
        }

        builder.append("- ").append(
            this.toYamlString(field, obj, lineSeparator, spacing, true, false, nested + 1, usePrefix));
      }

      return builder.toString();
    } else if (value instanceof String) {
      String stringValue = (String) value;
      if (stringValue.isEmpty()) {
        return "\"\"";
      }

      return ('"' + stringValue.replace("\\", "\\\\").replace("\"", "\\\"") + '"').replace("\n", "{NL}");
    } else if (value != null && isCollection && this.isNodeMapping(value.getClass())) {
      try (
          StringWriter stringWriter = new StringWriter();
          PrintWriter writer = new PrintWriter(stringWriter)
      ) {
        if (isMap) {
          writer.write(lineSeparator);
        }
        int indent = spacing.length() + 4;
        this.writeConfigKeyValue(writer, value.getClass(), value, value, indent, usePrefix);
        writer.flush();
        String data = stringWriter.toString();
        return data.substring(isMap ? 0 : indent, data.length() - lineSeparator.length());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      return String.valueOf(value);
    }
  }

  private ConfigSerializer<?, ?> getAndCacheSerializer(CustomSerializer serializer)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    Class<? extends ConfigSerializer<?, ?>> serializerClass = serializer.serializerClass();
    ConfigSerializer<?, ?> configSerializer = this.cachedSerializers.get(serializerClass);
    if (configSerializer == null) {
      configSerializer = serializerClass.getDeclaredConstructor().newInstance();
      this.cachedSerializers.put(serializerClass, configSerializer);
    }

    return configSerializer;
  }

  public void dispose() {
    this.placeholders.forEach(net.elytrium.commons.config.Placeholders.placeholders::remove);
    this.placeholders.clear();
    this.cachedSerializers.clear();
    this.prefix = null;
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

  /**
   * Indicates that a class is a node sequence.
   */
  @Deprecated
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface NodeSequence {

  }


  /**
   * Allows to use {@link net.elytrium.commons.config.Placeholders#replace(String, Object...)}
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface Placeholders {

    String[] value();

  }

  /**
   * Allows to (de-)serialize custom types
   */
  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  protected @interface CustomSerializer {

    /**
     * @return A serializer class with a no args constructor available
     */
    Class<? extends ConfigSerializer<?, ?>> serializerClass();

  }

}
