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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Placeholders {

  private static final Pattern EXACTLY_MATCHES = Pattern.compile("^\\{(?!_)[A-Z\\d_]+(?<!_)}$");
  private static final Pattern LOWERCASE = Pattern.compile("^(?!-)[a-z\\d-]+(?<!-)$");
  private static final Pattern UPPERCASE = Pattern.compile("^(?!_)[A-Z\\d_]+(?<!_)$");

  static final Map<Integer, String[]> placeholders = new HashMap<>();

  public static String[] getPlaceholders(Object value) {
    int hashCode = System.identityHashCode(value);
    if (!placeholders.containsKey(hashCode)) {
      throw new IllegalStateException("Invalid input");
    }
    return placeholders.get(hashCode);
  }

  public static int addPlaceholders(Object value, String... placeholders) {
    int hashCode = System.identityHashCode(value);
    Placeholders.placeholders.put(hashCode, Stream.of(placeholders).map(Placeholders::toPlaceholderName).toArray(String[]::new));
    return hashCode;
  }

  public static void removePlaceholders(Object value) {
    removePlaceholders(System.identityHashCode(value));
  }

  public static void removePlaceholders(int hash) {
    placeholders.remove(hash);
  }

  public static boolean hasPlaceholders(Object value) {
    return placeholders.containsKey(System.identityHashCode(value));
  }

  public static String replace(String value, Object... values) {
    String[] placeholders = getPlaceholders(value);
    String stringValue = value;
    for (int i = 0; i < Math.min(placeholders.length, values.length); i++) {
      stringValue = stringValue.replace(toPlaceholderName(placeholders[i]), String.valueOf(values[i]));
    }
    return stringValue;
  }

  private static String toPlaceholderName(String name) {
    if (EXACTLY_MATCHES.matcher(name).matches()) {
      return name;
    } else if (LOWERCASE.matcher(name).matches()) {
      return '{' + name.toUpperCase(Locale.ROOT).replace('-', '_') + '}';
    } else if (UPPERCASE.matcher(name).matches()) {
      return '{' + name + '}';
    } else {
      throw new IllegalStateException("Invalid placeholder: " + name);
    }
  }

}
