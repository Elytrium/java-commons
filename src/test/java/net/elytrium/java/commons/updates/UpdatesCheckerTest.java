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

package net.elytrium.java.commons.updates;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UpdatesCheckerTest {

  @Test
  public void updatesCheckerTest() {
    Assertions.assertTrue(UpdatesChecker.checkVersion("1.1.5", "1.10.0"));
    Assertions.assertFalse(UpdatesChecker.checkVersion("1.0.1", "0.9.9"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("1.009.9", "1.10.0-SNAPSHOT"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("0.9.9", "1.0.0-SNAPSHOT"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("9.9.9", "09.009.09"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("0.9", "1"));
    Assertions.assertFalse(UpdatesChecker.checkVersion("1.0.4", "1.0.4-SNAPSHOT"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("1.0.4", "1.0.5-SNAPSHOT"));
    Assertions.assertTrue(UpdatesChecker.checkVersion("1.0.4-SNAPSHOT", "1.0.4"));
  }

}
