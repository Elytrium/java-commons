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

package net.elytrium.java.commons.mc.velocity.commands;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import java.util.List;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.common.value.qual.IntRange;

// TODO: Tests
public class SuggestUtils {

  /**
   * Suggests online players with arguments check.
   *
   * @param server    The proxy server.
   * @param args      The current arguments.
   * @param offset    The argument position.
   * @param additions The additional values.
   * @return The list of online players.
   */
  @NonNull
  public static List<String> suggestPlayers(@NonNull ProxyServer server, @NonNull String @NonNull [] args,
      @IntRange(from = 0) int offset, @NonNull String @NonNull ... additions) {
    List<String> initialList = getAllPlayers(server);
    initialList.addAll(ImmutableList.copyOf(additions));
    return checkArgs(args, offset, initialList);
  }

  /**
   * Suggests registered servers with arguments check.
   *
   * @param server    The proxy server.
   * @param args      The current arguments.
   * @param offset    The argument position.
   * @param additions The additional values.
   * @return The list of registered servers.
   */
  @NonNull
  public static List<String> suggestServers(@NonNull ProxyServer server, @NonNull String @NonNull [] args,
      @IntRange(from = 0) int offset, @NonNull String @NonNull ... additions) {
    List<String> initialList = getRegisteredServers(server);
    initialList.addAll(ImmutableList.copyOf(additions));
    return checkArgs(args, offset, initialList);
  }

  /**
   * Suggests online players and registered servers.
   *
   * @param server    The proxy server.
   * @param args      The current arguments.
   * @param offset    The argument position.
   * @param additions The additional values.
   * @return The list of online players and registered servers.
   */
  @NonNull
  public static List<String> suggestServersAndPlayers(@NonNull ProxyServer server, @NonNull String @NonNull [] args,
      @IntRange(from = 0) int offset, @NonNull String @NonNull ... additions) {
    List<String> suggestions = getAllPlayers(server);
    suggestions.addAll(getRegisteredServers(server));
    suggestions.addAll(ImmutableList.copyOf(additions));
    return checkArgs(args, offset, suggestions);
  }

  /**
   * Returns online players.
   *
   * @param server The proxy server.
   * @return The list of online players.
   */
  @NonNull
  public static List<String> getAllPlayers(@NonNull ProxyServer server) {
    return server.getAllPlayers().stream()
        .map(Player::getUsername)
        .collect(Collectors.toList());
  }

  /**
   * Returns registered servers.
   *
   * @param server The proxy server.
   * @return The list of registered servers.
   */
  @NonNull
  public static List<String> getRegisteredServers(@NonNull ProxyServer server) {
    return server.getAllServers().stream()
        .map(RegisteredServer::getServerInfo)
        .map(ServerInfo::getName)
        .collect(Collectors.toList());
  }

  @NonNull
  private static List<String> checkArgs(@NonNull String @NonNull [] args, int offset, @NonNull List<String> suggestions) {
    if (args.length == offset) {
      return suggestions;
    } else if (args.length == offset + 1) {
      return suggestions.stream()
          .filter(str -> str.regionMatches(true, 0, args[offset], 0, args[offset].length()))
          .collect(Collectors.toList());
    }

    return ImmutableList.of();
  }
}
