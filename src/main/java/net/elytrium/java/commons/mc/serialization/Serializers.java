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

package net.elytrium.java.commons.mc.serialization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.ComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public enum Serializers {

  LEGACY_AMPERSAND(LegacyComponentSerializer.legacyAmpersand()),
  LEGACY_SECTION(LegacyComponentSerializer.legacyAmpersand()),
  MINIMESSAGE(MiniMessage.miniMessage()),
  GSON(GsonComponentSerializer.gson()),
  GSON_COLOR_DOWNSAMPLING(GsonComponentSerializer.colorDownsamplingGson()),
  PLAIN(PlainTextComponentSerializer.plainText());

  private final ComponentSerializer<Component, Component, String> serializer;

  @SuppressWarnings("unchecked")
  Serializers(ComponentSerializer<Component, ?, String> serializer) {
    this.serializer = (ComponentSerializer<Component, Component, String>) serializer;
  }

  public ComponentSerializer<Component, Component, String> getSerializer() {
    return this.serializer;
  }
}
