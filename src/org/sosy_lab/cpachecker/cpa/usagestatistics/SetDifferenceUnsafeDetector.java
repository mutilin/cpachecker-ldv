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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class SetDifferenceUnsafeDetector implements UnsafeDetector {

  public SetDifferenceUnsafeDetector(Configuration config) {
		// TODO Auto-generated constructor stub
	}

@Override
  public Collection<SingleIdentifier> getUnsafes(Map<SingleIdentifier, List<UsageInfo>> variables) {
    Collection<SingleIdentifier> unsafe = new HashSet<>();
    //Map<Integer, Set<Set<LockStatisticsLock>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsLock>>>();
/*
    for (String name : variables.keySet()) {
      Set<VariableInfo> vars = variables.get(name);

      for (VariableInfo var : vars) {
        VariableInfo UnsafeTypes = new VariableInfo(name, var.getFunctionName());
        for (String type : var.keySet()) {
          Set<LineInfo> UsageList = var.get(type);

          Cases.clear();
          Set<Set<LockStatisticsLock>> DifferentLocks;

          for (LineInfo line : UsageList) {
            if (Cases.containsKey(line.getLine())) {
              DifferentLocks = Cases.get(line.getLine());
              if (!DifferentLocks.contains(line.getLocks())) {
                DifferentLocks.add(line.getLocks());
              }
            }
            else {
              DifferentLocks = new HashSet<Set<LockStatisticsLock>>();
              DifferentLocks.add(line.getLocks());
              Cases.put(line.getLine(), DifferentLocks);
            }
          }

          Set<Integer> LinesToSave = new HashSet<Integer>();

          for (Integer line : Cases.keySet()) {
            for (Integer line2 : Cases.keySet()) {
              if (!Cases.get(line).equals(Cases.get(line2)) && !line.equals(line2)) {
                if (!LinesToSave.contains(line))
                  LinesToSave.add(line);
                if (!LinesToSave.contains(line2))
                  LinesToSave.add(line2);
              }
            }
          }
          Set<LineInfo> UnsafeLines = new HashSet<LineInfo>();

          for (LineInfo line : UsageList){
            if (LinesToSave.contains(line.getLine())) {
              UnsafeLines.add(line);
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
*/
    return unsafe;
  }

  @Override
  public String getDescription() {
    return "All lines with different sets of mutexes were printed";
  }

  @Override
  public Pair<UsageInfo, UsageInfo> getUnsafePair(List<UsageInfo> uinfo)
		throws HandleCodeException {
	// TODO Auto-generated method stub
	return null;
  }

}
