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

import com.google.common.base.Function;
import java.util.Optional;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class BlockatorPrecisionAdjustment implements PrecisionAdjustment {
  private BlockatorCPA parent;
  private PrecisionAdjustment wrappedPrecisionAdjustment;

  public BlockatorPrecisionAdjustment(
      BlockatorCPA pParent,
      PrecisionAdjustment pWrappedPrecisionAdjustment) {
    parent = pParent;
    wrappedPrecisionAdjustment = pWrappedPrecisionAdjustment;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState state,
      Precision precision,
      UnmodifiableReachedSet states,
      Function<AbstractState, AbstractState> stateProjection,
      AbstractState fullState) throws CPAException, InterruptedException {
    // precision might be outdated, if comes from a block-start and the inner part was refined.
    // so lets use the (expanded) inner precision.
    Precision modifiedPrecision = parent.getStateRegistry().get(state).getModifiedPrecision();
    if (modifiedPrecision != null) {
      precision = modifiedPrecision;
    }

    Optional<PrecisionAdjustmentResult> resultOpt = wrappedPrecisionAdjustment.prec(state,
        precision, states, stateProjection, fullState);

    if (!resultOpt.isPresent()) {
      return resultOpt;
    }

    PrecisionAdjustmentResult result = resultOpt.get();
    parent.getStateRegistry().copy(state, result.abstractState());
    parent.getCacheManager().replace(state, result.abstractState());
    return resultOpt;
  }
}
