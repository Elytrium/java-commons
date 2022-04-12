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

package net.elytrium.java.commons.files;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FileSearcher {

  /**
   * Searches for a file with the specified filename in the desired directories.
   *
   * @param filename      Filename to search for.
   * @param pathsToSearch Paths to the desired directories.
   * @return Desired file path.
   */
  @Nullable
  public static Path findByName(@NonNull String filename, @NonNull Path @NonNull ... pathsToSearch) throws IOException {
    for (Path pathToSearch : pathsToSearch) {
      if (pathToSearch.toFile().isDirectory()) {
        for (Path path : Files.walk(pathToSearch, FileVisitOption.FOLLOW_LINKS).collect(Collectors.toList())) {
          if (path.toFile().isFile() && String.valueOf(path.getFileName()).equals(filename)) {
            return path;
          }
        }
      }
    }

    return null;
  }
}
