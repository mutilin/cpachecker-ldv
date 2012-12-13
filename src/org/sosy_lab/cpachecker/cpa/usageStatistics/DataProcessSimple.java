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

import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;

/**
 * This class implements simple analysis, when all lines are compared
 * pairwise only with set of locks, and all different ones are written
 * in statistics
 */

public class DataProcessSimple implements DataProcessing {
  private final Set<String> annotated;

  DataProcessSimple(Set<String> aVariables) {
    annotated = aVariables;
  }

  @Override
  public Collection<VariableIdentifier> process(Map<VariableIdentifier, Set<UsageInfo>> stat) {

    Collection<VariableIdentifier> unsafe = new HashSet<VariableIdentifier>();
    Collection<VariableIdentifier> toDelete = new HashSet<VariableIdentifier>();

nextId:for (VariableIdentifier id : stat.keySet()) {
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
    //now we should check, that all unsafe cases have at least one write access
next:for (VariableIdentifier id : unsafe) {
      if (annotated.contains(id.name)) continue;
      Set<UsageInfo> uset = stat.get(id);
      for (UsageInfo uinfo : uset) {
        if (uinfo.getAccess() == Access.WRITE/* && uinfo.getCallStack().getDepth() > 1*/)
          continue next;
      }
      //no write access
      //we couldn't delete from unsafe here, because we use it in cycle for
      toDelete.add(id);
    }

    //deleting
    for (VariableIdentifier id : toDelete) {
      unsafe.remove(id);
    }
    return unsafe;
  }

  @Override
  public String getDescription() {
    return "All lines with different mutexes were printed";
  }

}
