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

package net.elytrium.commons.kyori.serialization;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.InvocationTargetException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.ComponentSerializer;

public enum Serializers {

  LEGACY_AMPERSAND("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer", "legacyAmpersand"),
  LEGACY_SECTION("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer", "legacySection"),
  MINIMESSAGE("net.kyori.adventure.text.minimessage.MiniMessage", "miniMessage"),
  GSON("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer", "gson"),
  GSON_COLOR_DOWNSAMPLING("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer", "colorDownsamplingGson"),
  PLAIN("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", "plainText");

  @Nullable
  private final ComponentSerializer<Component, Component, String> serializer;

  Serializers(String className, String methodName) {
    this.serializer = this.findSerializer(className, methodName);
  }

  /**
   * Used to prevent NoClassDefFoundError exception.
   *
   * @param className  The class name of the serializer holder.
   * @param methodName The method name that returns the serializer.
   * @return The {@link ComponentSerializer}, may be null.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  private ComponentSerializer<Component, Component, String> findSerializer(String className, String methodName) {
    try {
      return (ComponentSerializer<Component, Component, String>) Class.forName(className).getDeclaredMethod(methodName).invoke(null);
    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      return null;
    }
  }

  @Nullable
  public ComponentSerializer<Component, Component, String> getSerializer() {
    return this.serializer;
  }
}
