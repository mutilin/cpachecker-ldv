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
import java.util.LinkedList;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.exceptions.CPAException;


public class PairIterator extends WrappedConfigurableRefinementBlock<ExtendedARGPath, Pair<ExtendedARGPath, ExtendedARGPath>>{
  private List<ExtendedARGPath> storedPaths = new LinkedList<>();
  private int handledPairs = 0;
  private Timer loopTimer = new Timer();
  private List<Integer> calledSize = new LinkedList<>();

  public PairIterator(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper) {
    super(pWrapper);
  }

  @Override
  public RefinementResult call(ExtendedARGPath pInput) throws CPAException, InterruptedException {
    storedPaths.add(pInput);
    return RefinementResult.createUnknown();
  }

  @Override
  public void printStatistics(PrintStream pOut) {
    pOut.println("--PairIterator--");
    pOut.println("Number of handled paths: " + handledPairs);
    pOut.println("Total calls:       " + calledSize.size());
    pOut.println(calledSize);
  }

  @Override
  public Object handleFinishSignal(Class<? extends RefinementInterface> callerClass) throws CPAException, InterruptedException {
    loopTimer.start();
    if (callerClass.equals(UsageIterator.class)) {
      System.out.println("Number of paths: " + storedPaths.size());
      calledSize.add(storedPaths.size());
      for (int i = 0; i < storedPaths.size(); i++) {
        for (int j = i + 1; j < storedPaths.size(); j++) {
          Pair<ExtendedARGPath, ExtendedARGPath> pair = Pair.of(storedPaths.get(i), storedPaths.get(j));
          handledPairs++;
          RefinementResult result = wrappedRefiner.call(pair);
        }
      }
      storedPaths.clear();
    }
    loopTimer.stop();
    return null;
  }
}
