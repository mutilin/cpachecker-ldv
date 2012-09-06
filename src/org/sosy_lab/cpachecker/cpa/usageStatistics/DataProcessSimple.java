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

/**
 * This class implements simple analysis, when all lines are compared
 * pairwise only with set of mutexes, and all different ones are written
 * in statistics
 */

public class DataProcessSimple implements DataProcessing{

  @Override
  public Collection<Identifier> process(Map<Identifier, Set<UsageInfo>> stat) {
    Collection<Identifier> unsafe = new HashSet<Identifier>();

nextId:for (Identifier id : stat.keySet()) {

      Set<UsageInfo> uset = stat.get(id);
      for (UsageInfo uinfo : uset) {
        for (UsageInfo uinfo2 : uset) {
          if (!uinfo.intersect(uinfo2) && !unsafe.contains(id)) {
            unsafe.add(id);
            continue nextId;
          }
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
