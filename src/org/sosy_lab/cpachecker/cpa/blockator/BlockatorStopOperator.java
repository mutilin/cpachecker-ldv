/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.blockator;

import java.util.Collection;
import java.util.stream.Collectors;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class BlockatorStopOperator implements StopOperator {
  private BlockatorCPA parent;
  private StopOperator wrappedStop;

  public BlockatorStopOperator(BlockatorCPA pParent, StopOperator pWrappedStop) {
    parent = pParent;
    wrappedStop = pWrappedStop;
  }

  @Override
  public boolean stop(AbstractState state, Collection<AbstractState> reached, Precision precision)
      throws CPAException, InterruptedException {
    BlockatorState bState = parent.getStateRegistry().get(state);
    Collection<AbstractState> filteredReached = reached.stream()
        .filter(s -> parent.getStateRegistry().get(s).getBlockStack().equals(bState.getBlockStack()))
        .collect(Collectors.toList());

    return wrappedStop.stop(state, filteredReached, precision);
  }
}
