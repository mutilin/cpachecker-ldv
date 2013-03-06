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

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.Identifier;

/**
 * This class implements simple analysis, when all lines are compared
 * pairwise only with set of locks, and all different ones are written
 * in statistics
 */

@Options(prefix="cpa.usagestatistics.unsafedetector")
public class PairwiseUnsafeDetector implements UnsafeDetector {
  @Option(description = "variables, which will be unsafes even only with read access (they can be changed invisibly)")
  private Set<String> detectByReadAccess;

  public PairwiseUnsafeDetector(Configuration config) throws InvalidConfigurationException {
	    config.inject(this);
  }

  @Override
  public Collection<Identifier> getUnsafes(Map<Identifier, Set<UsageInfo>> stat) {

    Collection<Identifier> unsafe = new HashSet<Identifier>();
    Collection<Identifier> toDelete = new HashSet<Identifier>();

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
    //now we should check, that all unsafe cases have at least one write access
next:for (Identifier id : unsafe) {
      if (detectByReadAccess != null && detectByReadAccess.contains(id.getName())) continue;
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
    for (Identifier id : toDelete) {
      unsafe.remove(id);
    }
    return unsafe;
  }

  @Override
  public Pair<UsageInfo, UsageInfo> getSomeUnsafePair(Set<UsageInfo> uinfo) throws HandleCodeException {
	    for (UsageInfo info1 : uinfo) {
	      for (UsageInfo info2 : uinfo) {
	        if ((info1.getAccess() == Access.WRITE || info2.getAccess() == Access.WRITE) && !info1.intersect(info2)) {
	          return Pair.of(info1, info2);
	        }
	      }
	    }
	    throw new HandleCodeException("Can't find example of unsafe cases");
  }

  @Override
  public String getDescription() {
    return "All lines with different mutexes were printed";
  }

}
