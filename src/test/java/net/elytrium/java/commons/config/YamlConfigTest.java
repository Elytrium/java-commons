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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO: Tests with loading changed config from resources
class YamlConfigTest {

  @Test
  void testConfigWithPrefix() throws IOException {
    Path configWithPrefixPath = Files.createTempFile("ConfigWithPrefix", ".yml");
    File configWithPrefixFile = this.processTempFile(configWithPrefixPath);

      {
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
        Assertions.assertEquals("This is string with placeholders",
                Placeholders.replace(SettingsWithPrefix.IMP.STRING_WITH_PLACEHOLDERS, "string", "placeholders"));
        Assertions.assertEquals("This is string with placeholders",
                Placeholders.replace(SettingsWithPrefix.IMP.STRING_WITH_PLACEHOLDERS2, "placeholders", "string"));
        Assertions.assertEquals("value 1 value 2", Placeholders.replace(SettingsWithPrefix.IMP.ANOTHER_STRING_WITH_PLACEHOLDERS, "value 1", "value 2"));
        Assertions.assertEquals("{PLACEHOLDER} {ANOTHER_PLACEHOLDER}", SettingsWithPrefix.IMP.ANOTHER_STRING_WITH_PLACEHOLDERS);

        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.get("1"), "prefix value >> some value", 1234, "prefix value >> value", 10);
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.get("b"), "2nd string", 1234, "prefix value >> value", 10);
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.get("c"), "3rd string", 4321, "prefix value >> value", 10);
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_LIST.get(0), "prefix value >> first", 100, "prefix value >> value", 10);
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_LIST.get(1), "second", 200, "prefix value >> value", 10);

        this.compareFiles("ConfigWithPrefix.yml", configWithPrefixPath);
      }

      {
        Files.delete(configWithPrefixPath);
        Files.copy(Objects.requireNonNull(YamlConfigTest.class.getResourceAsStream("/ChangedConfigWithPrefix.yml")), configWithPrefixPath);
        Assertions.assertEquals(YamlConfig.LoadResult.SUCCESS, SettingsWithPrefix.IMP.reload(configWithPrefixFile, SettingsWithPrefix.IMP.PREFIX));

        Assertions.assertNotEquals("a final value", SettingsWithPrefix.IMP.FINAL_FIELD);
        Assertions.assertEquals("a", SettingsWithPrefix.IMP.PREFIX);
        Assertions.assertEquals("a other regular value", SettingsWithPrefix.IMP.REGULAR_FIELD);
        Assertions.assertEquals("a other string value", SettingsWithPrefix.IMP.PREPEND.STRING_FIELD);
        Assertions.assertEquals("a other value", SettingsWithPrefix.IMP.PREPEND.FIELD_WITH_COMMENT_AT_SAME_LINE);
        Assertions.assertEquals("a value", SettingsWithPrefix.IMP.PREPEND.SAME_LINE.APPEND.FIELD1);
        Assertions.assertEquals("a changed value", SettingsWithPrefix.IMP.PREPEND.SAME_LINE.APPEND.FIELD2);
        Assertions.assertEquals("placeholders test", Placeholders.replace(SettingsWithPrefix.IMP.STRING_WITH_PLACEHOLDERS, "test", "placeholders"));
        Assertions.assertEquals("value 2 value 1", Placeholders.replace(SettingsWithPrefix.IMP.ANOTHER_STRING_WITH_PLACEHOLDERS, "value 1", "value 2"));
        Assertions.assertEquals("{ANOTHER_PLACEHOLDER} {PLACEHOLDER}", SettingsWithPrefix.IMP.ANOTHER_STRING_WITH_PLACEHOLDERS);

        System.out.println(System.identityHashCode(SettingsWithPrefix.IMP.STRING_WITH_PLACEHOLDERS)
                + " " + System.identityHashCode(SettingsWithPrefix.IMP.STRING_WITH_PLACEHOLDERS2));

        Assertions.assertNotNull(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP);
        Assertions.assertEquals(2, SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.size());
        Assertions.assertTrue(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.containsKey("other"));
        Assertions.assertTrue(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.containsKey("2"));
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.get("other"), "string", 128, "a some value", 256);
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_MAP.get("2"), "a another string", 512, "a another value", 1024);

        Assertions.assertNotNull(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_LIST);
        Assertions.assertEquals(1, SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_LIST.size());
        this.assertNodeSequence(SettingsWithPrefix.IMP.NODE_TEST.NODE_SEQ_LIST.get(0), "a yet another string", 8, "a yet another value", 2);

        this.compareFiles("ChangedConfigWithPrefix.yml", configWithPrefixPath);
      }

    Assertions.assertEquals(3, Placeholders.placeholders.size());
    SettingsWithPrefix.IMP.dispose();
    Assertions.assertEquals(0, Placeholders.placeholders.size());
  }


  @Test
  void placeholdersTest() {
    String stringWithPlaceholders = "{PLACEHOLDER1} {PLACEHOLDER2} {PLACEHOLDER3}";
    Placeholders.addPlaceholders(stringWithPlaceholders, "placeholder3", "PLACEHOLDER1", "{PLACEHOLDER2}");
    Assertions.assertEquals("2 3 1", Placeholders.replace(stringWithPlaceholders, "1", "2", "3"));
    Assertions.assertEquals(1, Placeholders.placeholders.size());

    Placeholders.removePlaceholders(stringWithPlaceholders);
    Assertions.assertEquals(0, Placeholders.placeholders.size());
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

  private void assertNodeSequence(SettingsWithPrefix.NODE_TEST.TestNodeSequence node, String expectedString, int expectedInteger, String a, int b) {
    Assertions.assertEquals(0, node.IGNORED);
    Assertions.assertEquals("{PRFX} final", node.FINAL_FIELD);
    Assertions.assertEquals(expectedString, node.SOME_STRING);
    Assertions.assertEquals(expectedInteger, node.SOME_INTEGER);
    Assertions.assertEquals(a, node.OTHER_NODE_SEQ.A);
    Assertions.assertEquals(b, node.OTHER_NODE_SEQ.B);
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

    @Placeholders({ "{TEST}", "test2" })
    public String STRING_WITH_PLACEHOLDERS = "This is {TEST} with {TEST2}";

    @Placeholders({ "test2", "test" })
    public String STRING_WITH_PLACEHOLDERS2 = "This is {TEST} with {TEST2}";

    @Placeholders({ "PLACEHOLDER", "another-placeholder" })
    public String ANOTHER_STRING_WITH_PLACEHOLDERS = "{PLACEHOLDER} {ANOTHER_PLACEHOLDER}";

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
      @Comment(
          value = {
              "SAME_LINE APPEND second comment Line 1",
              "SAME_LINE APPEND second comment Line 2"
          },
          at = Comment.At.APPEND
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

    @Create
    public NODE_TEST NODE_TEST;

    public static class NODE_TEST {

      public Map<String, TestNodeSequence> NODE_SEQ_MAP;

      {
        this.NODE_SEQ_MAP = new HashMap<>();
        this.NODE_SEQ_MAP.put("1", createNodeSequence(TestNodeSequence.class));
        this.NODE_SEQ_MAP.put("b", createNodeSequence(TestNodeSequence.class, "2nd string"));
        this.NODE_SEQ_MAP.put("c", createNodeSequence(TestNodeSequence.class, "3rd string", 4321));
      }

      @NewLine
      public List<TestNodeSequence> NODE_SEQ_LIST;

      {
        this.NODE_SEQ_LIST = new LinkedList<>();
        this.NODE_SEQ_LIST.add(createNodeSequence(TestNodeSequence.class, "{PRFX} first", 100));
        this.NODE_SEQ_LIST.add(createNodeSequence(TestNodeSequence.class, "second", 200));
      }

      @NodeSequence
      public static class TestNodeSequence {

        @Final
        public String FINAL_FIELD = "{PRFX} final";

        @Ignore
        public int IGNORED = 0;

        public String SOME_STRING = "{PRFX} some value";

        public int SOME_INTEGER = 1234;

        @Create
        public OTHER_NODE_SEQ OTHER_NODE_SEQ;

        public static class OTHER_NODE_SEQ {
          public String A = "{PRFX} value";
          public int B = 10;
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
