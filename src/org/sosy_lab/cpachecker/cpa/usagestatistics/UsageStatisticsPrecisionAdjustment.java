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

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSetView;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;

class UsageStatisticsPrecisionAdjustment implements PrecisionAdjustment {

  private final PrecisionAdjustment wrappedPrecAdjustment;

  public UsageStatisticsPrecisionAdjustment(PrecisionAdjustment pWrappedPrecAdjustment) {
    wrappedPrecAdjustment = pWrappedPrecAdjustment;
  }

  @Override
  public Triple<AbstractState, Precision, Action> prec(AbstractState pElement,
      Precision oldPrecision, UnmodifiableReachedSet pElements) throws CPAException, InterruptedException {

    Preconditions.checkArgument(pElement instanceof UsageStatisticsState);
    UsageStatisticsState element = (UsageStatisticsState)pElement;

    UnmodifiableReachedSet elements = new UnmodifiableReachedSetView(
        pElements,  ARGState.getUnwrapFunction(), Functions.<Precision>identity());

    AbstractState oldElement = element.getWrappedState();

    Precision oldWrappedPrecision = ((UsageStatisticsPrecision)oldPrecision).getWrappedPrecision();

    Triple<AbstractState, Precision, Action> unwrappedResult = wrappedPrecAdjustment.prec(oldElement, oldWrappedPrecision, elements);

    AbstractState newElement = unwrappedResult.getFirst();
    Precision newWrappedPrecision = unwrappedResult.getSecond();
    Action action = unwrappedResult.getThird();

    PredicateAbstractState state = AbstractStates.extractStateByType(newElement, PredicateAbstractState.class);
    if (state == null || !state.getAbstractionFormula().isFalse() && state.isAbstractionState()) {
      element.saveUnsafesInContainer();
      if (element.isTarget()) {
        action = Action.BREAK;
      }
    }

    if ((oldElement == newElement) && (oldWrappedPrecision == newWrappedPrecision)) {
      // nothing has changed
      return Triple.of(pElement, oldPrecision, action);
    }
    UsageStatisticsState resultElement = element.clone(newElement);
    UsageStatisticsPrecision newPrecision = ((UsageStatisticsPrecision)oldPrecision).clone(newWrappedPrecision);
    return Triple.of((AbstractState)resultElement, (Precision)newPrecision, action);
  }
}
