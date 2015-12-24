/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import java.io.PrintStream;

import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.exceptions.CPAException;


public abstract class GenericIterator<I, O> extends WrappedConfigurableRefinementBlock<I, O> {
  private Timer totalTimer = new Timer();
  private int numOfIterations = 0;

  public GenericIterator(ConfigurableRefinementBlock<O> pWrapper) {
    super(pWrapper);
  }

  @Override
  public RefinementResult call(I pInput) throws CPAException, InterruptedException {

    O iteration;

    totalTimer.start();
    PredicatePrecision completePrecision = PredicatePrecision.empty();
    RefinementResult result = RefinementResult.createFalse();
    init(pInput);

    try {
      while ((iteration = getNext(pInput)) != null) {
        numOfIterations++;
        totalTimer.stop();
        result = wrappedRefiner.call(iteration);
        totalTimer.start();

        if (result.isTrue()) {
          //Finish iteration, the race is found
          result.addPrecision(completePrecision);
          return result;
        }

        PredicatePrecision precision = result.getPrecision();
        if (precision != null) {
          completePrecision = completePrecision.mergeWith(precision);
        }

        finalize(pInput, iteration, result);
      }
      result.addPrecision(completePrecision);
      return result;
    } finally {
      sendFinishSignal();
      totalTimer.stopIfRunning();
    }
  }


  abstract protected O getNext(I pInput);
  protected void init(I pInput) {}
  protected void finalize(I input, O output, RefinementResult r) {}
  protected void printDetailedStatistics(PrintStream pOut) {}

  @Override
  public final void printStatistics(PrintStream pOut) {
    printDetailedStatistics(pOut);
    pOut.println("Timer for block:           " + totalTimer);
    pOut.println("Number of iterations:      " + numOfIterations);
    wrappedRefiner.printStatistics(pOut);
  }


}
