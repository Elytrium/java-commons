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
import net.kyori.adventure.text.serializer.ComponentSerializer;
import org.jetbrains.annotations.NotNull;

// TODO: Tests
public class Serializer implements ComponentSerializer<Component, Component, String> {

  private final ComponentSerializer<Component, Component, String> serializer;

  public Serializer(@NotNull ComponentSerializer<Component, Component, String> serializer) {
    this.serializer = serializer;
  }

  @NotNull
  @Override
  public Component deserialize(@NotNull String input) {
    return this.serializer.deserialize(input);
  }

  @NotNull
  @Override
  public String serialize(@NotNull Component component) {
    return this.serializer.serialize(component);
  }

  @NotNull
  public ComponentSerializer<Component, Component, String> getSerializer() {
    return this.serializer;
  }
}
