/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;

import com.google.common.base.Preconditions;

/**
 * Information about special functions like sdlFirst() and sdlNext();
 */
public class BinderFunctionInfo {
  String name;
  int parameters;
  List<Access> pInfo;
  /*
   * 0 - before equal,
   * 1 - first parameter, etc..
   */
  Pair<Integer, Integer> linkInfo;

  BinderFunctionInfo(String nm, Configuration pConfig) throws InvalidConfigurationException {
    name = nm;
    try {
      parameters = Integer.parseInt(pConfig.getProperty(name + ".parameters"));
      String line = pConfig.getProperty(name + ".pInfo");
      Preconditions.checkNotNull(line);
      String[] options = line.split(", *");
      pInfo = new LinkedList<>();
      for (String option : options) {
        pInfo.add(Access.getValue(option));
      }
      line = pConfig.getProperty(name + ".linkInfo");
      if (line != null) {
        options = line.split(", *");
        assert options.length == 2;
        linkInfo = Pair.of(Integer.parseInt(options[0]), Integer.parseInt(options[1]));
      } else {
        linkInfo = null;
      }
    } catch (NumberFormatException e) {
      System.err.println("No information about parameters in " + name + " function");
      throw e;
    }
  }
}
