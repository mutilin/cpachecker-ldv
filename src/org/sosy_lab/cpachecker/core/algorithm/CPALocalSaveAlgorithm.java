/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa")
public class CPALocalSaveAlgorithm extends CPAAlgorithm {

  private static Map<CFANode, LocalState> reachedStatistics;

  public CPALocalSaveAlgorithm(ConfigurableProgramAnalysis pCpa, LogManager pLogger, ShutdownNotifier pShutdownNotifier)  throws InvalidConfigurationException {
    super(pCpa, pLogger, pShutdownNotifier,
        null);
    if (reachedStatistics == null) {
      reachedStatistics = new HashMap<>();
    }
  }

  private void addReachedSet(final ReachedSet reachedSet) throws HandleCodeException {
    if (reachedSet instanceof PartitionedReachedSet) {
      Set<AbstractState> ReachedSet = ((PartitionedReachedSet)reachedSet).getReached();
      for (AbstractState state : ReachedSet) {
        //we should have LocationCPA and our LocalCPA
        CFANode node = AbstractStates.extractLocation(state);
        LocalState lState = AbstractStates.extractStateByType(state, LocalState.class);
        if (!reachedStatistics.containsKey(node)) {
          reachedStatistics.put(node, lState);
        } else {
          LocalState previousState = reachedStatistics.get(node);
          reachedStatistics.put(node, previousState.join(lState));
        }
      }
    } else if (reachedSet instanceof ForwardingReachedSet) {
      addReachedSet(((ForwardingReachedSet)reachedSet).getDelegate());
    } else {
      throw new HandleCodeException("Can't save this strange reached set of class " + reachedSet.getClass().toString());
    }

  }

  @Override
  public boolean run(final ReachedSet reachedSet) throws CPAException, InterruptedException {

    stats.totalTimer.start();
    try {
      boolean b = run0(reachedSet);
      addReachedSet(reachedSet);
      return b;

    } finally {
      stats.totalTimer.stopIfRunning();
      stats.chooseTimer.stopIfRunning();
      stats.precisionTimer.stopIfRunning();
      stats.transferTimer.stopIfRunning();
      stats.mergeTimer.stopIfRunning();
      stats.stopTimer.stopIfRunning();
      stats.addTimer.stopIfRunning();
      stats.forcedCoveringTimer.stopIfRunning();
    }
  }

  public Map<CFANode, LocalState> getStatistics() {
    return reachedStatistics;
  }
}
