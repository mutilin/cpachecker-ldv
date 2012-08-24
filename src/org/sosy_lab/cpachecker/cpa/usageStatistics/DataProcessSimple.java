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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cpa.usageStatistics.VariableInfo.LineInfo;

/**
 * This class implements simple analysis, when all lines are compared
 * pairwise only with set of mutexes, and all different ones are written
 * in statistics
 */

public class DataProcessSimple implements DataProcessing{

  @Override
  public Collection<VariableInfo> process(Map<String, Set<VariableInfo>> variables) {
    Collection<VariableInfo> unsafe = new HashSet<VariableInfo>();

    for (String name : variables.keySet()) {

      Set<VariableInfo> vars = variables.get(name);
      for (VariableInfo var : vars) {
        VariableInfo UnsafeTypes = new VariableInfo(name, var.getFunctionName());
        for (String type : var.keySet()) {
          Set<LineInfo> UsageList = var.get(type);

          Set<LineInfo> UnsafeLines = new HashSet<LineInfo>();
          if (UsageList.size() < 2)
            continue;
          for (LineInfo line : UsageList) {
            for (LineInfo line2 : UsageList) {
              if (/*line.getLine() != line2.getLine() &&*/
                !line.intersect(line2) /*&&
                line.getCallStack().equals(line2.getCallStack())*/) {
                if (!UnsafeLines.contains(line))
                  UnsafeLines.add(line);
                if (!UnsafeLines.contains(line2))
                  UnsafeLines.add(line2);
              }
            }
          }
          if (UnsafeLines.size() > 0) {
            UnsafeTypes.add(type, UnsafeLines);
          }
        }

        if (UnsafeTypes.size() > 0) {
          unsafe.add(UnsafeTypes);
        }
      }
    }

    return unsafe;
  }

  @Override
  public String getDescription() {
    return "All lines with different mutexes were printed";
  }

}
