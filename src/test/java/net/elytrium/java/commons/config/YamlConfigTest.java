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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO: Tests with loading changed config from resources
class YamlConfigTest {

  @Test
  void testConfigWithPrefix() throws IOException {
    Path configWithPrefixPath = Files.createTempFile("ConfigWithPrefix", ".yml");
    File configWithPrefixFile = this.processTempFile(configWithPrefixPath);
    for (int i = 0; i < 4; ++i) {
      if (SettingsWithPrefix.IMP.reload(configWithPrefixFile, SettingsWithPrefix.IMP.PREFIX) == YamlConfig.LoadResult.CONFIG_NOT_EXISTS) {
        Assertions.assertEquals(0, i);
      }
    }

    Assertions.assertNotEquals("prefix value >> final value", SettingsWithPrefix.IMP.FINAL_FIELD); // Final fields shouldn't be changed.
    Assertions.assertEquals("prefix value >>", SettingsWithPrefix.IMP.PREFIX);
    Assertions.assertEquals("prefix value >> regular value", SettingsWithPrefix.IMP.REGULAR_FIELD);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.STRING_FIELD);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.FIELD_WITH_COMMENT_AT_SAME_LINE);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.STRING_FIELD);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.FIELD_WITH_COMMENT_AT_SAME_LINE);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.SAME_LINE.APPEND.FIELD1);
    Assertions.assertEquals("prefix value >> string value", SettingsWithPrefix.IMP.PREPEND.SAME_LINE.APPEND.FIELD2);

    this.compareFiles("ConfigWithPrefix.yml", configWithPrefixPath);
  }

  @Test
  void testConfigWithoutPrefix() throws IOException {
    Path configWithoutPrefixPath = Files.createTempFile("ConfigWithoutPrefix", ".yml");
    File configWithoutPrefixFile = this.processTempFile(configWithoutPrefixPath);
    for (int i = 0; i < 4; ++i) {
      if (SettingsWithoutPrefix.IMP.reload(configWithoutPrefixFile) == YamlConfig.LoadResult.CONFIG_NOT_EXISTS) {
        Assertions.assertEquals(0, i);
      }
    }

    Assertions.assertEquals("{PRFX} regular value", SettingsWithoutPrefix.IMP.REGULAR_FIELD);

    this.compareFiles("ConfigWithoutPrefix.yml", configWithoutPrefixPath);
  }

  private File processTempFile(Path path) {
    File file = path.toFile();
    if (!file.delete()) { // We don't need an empty temp file, we need only path.
      throw new IllegalStateException("File must be deleted.");
    }
    file.deleteOnExit();

    return file;
  }

  private void compareFiles(String finalFileName, Path currentFilePath) throws IOException {
    try (InputStream finalConfig = YamlConfigTest.class.getResourceAsStream("/" + finalFileName)) {
      if (finalConfig == null) {
        throw new IllegalStateException("Stream cannot be null.");
      } else {
        Assertions.assertEquals(
            new String(this.readAllBytes(finalConfig), StandardCharsets.UTF_8),
            new String(Files.readAllBytes(currentFilePath), StandardCharsets.UTF_8)
        );
      }
    }
  }

  // From JDK 11
  private byte[] readAllBytes(InputStream in) throws IOException {
    List<byte[]> bufs = null;
    byte[] result = null;
    int remaining = Integer.MAX_VALUE;
    int defaultBufferSize = 8192;
    int n;
    int total = 0;
    int maxBufferSize = Integer.MAX_VALUE - 8;
    do {
      byte[] buf = new byte[Math.min(remaining, defaultBufferSize)];
      int nread = 0;

      // Read to EOF which may read more or less than buffer size.
      while ((n = in.read(buf, nread, Math.min(buf.length - nread, remaining))) > 0) {
        nread += n;
        remaining -= n;
      }

      if (nread > 0) {
        if (maxBufferSize - total < nread) {
          throw new OutOfMemoryError("Required array size too large.");
        }
        if (nread < buf.length) {
          buf = Arrays.copyOfRange(buf, 0, nread);
        }
        total += nread;
        if (result == null) {
          result = buf;
        } else {
          if (bufs == null) {
            bufs = new ArrayList<>();
            bufs.add(result);
          }
          bufs.add(buf);
        }
      }
      // If the last call to read returned -1 or the number of bytes requested have been read then break.
    } while (n >= 0 && remaining > 0);

    if (bufs == null) {
      if (result == null) {
        return new byte[0];
      }

      return result.length == total ? result : Arrays.copyOf(result, total);
    }

    result = new byte[total];
    int offset = 0;
    remaining = total;
    for (byte[] b : bufs) {
      int count = Math.min(b.length, remaining);
      System.arraycopy(b, 0, result, offset, count);
      offset += count;
      remaining -= count;
    }

    return result;
  }

  static class SettingsWithPrefix extends YamlConfig {

    @Ignore
    public static final SettingsWithPrefix IMP = new SettingsWithPrefix();

    @Final
    public String FINAL_FIELD = "{PRFX} final value";

    public String PREFIX = "prefix value >>";

    public String REGULAR_FIELD = "{PRFX} regular value";

    @Create
    public PREPEND PREPEND;

    @Comment({
        "PREPEND comment Line 1",
        "PREPEND comment Line 2"
    })
    public static class PREPEND {

      public String STRING_FIELD = "{PRFX} string value";

      @NewLine
      @Comment(
          value = {
              "FIELD_WITH_COMMENT_AT_SAME_LINE comment",
              "Invisible line"
          },
          at = Comment.At.SAME_LINE
      )
      public String FIELD_WITH_COMMENT_AT_SAME_LINE = "{PRFX} string value";

      @Create
      public SAME_LINE SAME_LINE;

      @NewLine(amount = 2)
      @Comment(
          value = {
              "SAME_LINE comment Line 1",
              "SAME_LINE comment Invisible line"
          },
          at = Comment.At.SAME_LINE
      )
      public static class SAME_LINE {

        @Create
        public APPEND APPEND;

        public static class APPEND {

          @Comment(
              value = {
                  "FIELD1 APPEND comment",
                  "Visible line"
              },
              at = Comment.At.APPEND
          )
          public String FIELD1 = "{PRFX} string value";

          @NewLine
          @Comment(
              value = {
                  "FIELD2 PREPEND comment",
                  "Line 2"
              },
              at = Comment.At.PREPEND
          )
          public String FIELD2 = "{PRFX} string value";
        }
      }
    }
  }

  static class SettingsWithoutPrefix extends YamlConfig {

    @Ignore
    public static final SettingsWithoutPrefix IMP = new SettingsWithoutPrefix();

    public String REGULAR_FIELD = "{PRFX} regular value";
  }
}
