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

import static com.google.common.collect.FluentIterable.from;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

@Options(prefix="cpa.usagestatistics")
public class InterruptFilter extends
    WrappedConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>, Pair<ExtendedARGPath, ExtendedARGPath>> {

  Timer totalTimer = new Timer();

  @Option(name = "interruptHandlers", description = "The names of interrupt handlers")
  private Set<String> interruptHandlerNames = new HashSet<>();

  private String mainFunction = "ldv_main";

  Predicate<ARGState> isFirstCall = new Predicate<ARGState>() {
    @Override
    public boolean apply(@Nullable ARGState pInput) {
      CFANode location = AbstractStates.extractLocation(pInput);
      if (location instanceof CFunctionEntryNode) {
        CallstackState callstack = AbstractStates.extractStateByType(pInput, CallstackState.class);
        if (callstack.getPreviousState() != null && callstack.getPreviousState().getCurrentFunction().equals(mainFunction)) {
          return true;
        }
      }
      return false;
    }

  };

  Function<ARGState, String> getFunctionName = new Function<ARGState, String>() {
    @Override
    public String apply(@Nullable ARGState pInput) {
      CFANode location = AbstractStates.extractLocation(pInput);

      return location.getFunctionName();
    }
  };

  public InterruptFilter(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper,
      Configuration pConfig) throws InvalidConfigurationException {
    super(pWrapper);
    pConfig.inject(this);
    mainFunction = pConfig.getProperty("analysis.entryFunction");
  }

  @Override
  public RefinementResult call(Pair<ExtendedARGPath, ExtendedARGPath> pInput) throws CPAException, InterruptedException {
    totalTimer.start();

    try {
      ExtendedARGPath firstPath = pInput.getFirst();
      ExtendedARGPath secondPath = pInput.getSecond();
      if (isInterruptHandlerCall(firstPath) && isInterruptHandlerCall(secondPath)) {
        return RefinementResult.createFalse();
      } else {
        return wrappedRefiner.call(pInput);
      }
    } finally {
      totalTimer.stop();
    }
  }

  private boolean isInterruptHandlerCall(ExtendedARGPath path) {
    List<String> callerFunctions = from(path.getStateSet()).
                                 filter(isFirstCall).
                                 transform(getFunctionName).toList();
    //assert callerFunctions.size() == 1;
    //TODO Now I believe, it is enough to check the last function called from main - this is related to the call stack
    if (callerFunctions.size() > 1) {
      String targetFunctionName = callerFunctions.get(callerFunctions.size() - 1);
      return interruptHandlerNames.contains(targetFunctionName);
    } else {
      //Usage in main
      return false;
    }
  }

  @Override
  public void printStatistics(PrintStream pOut) {
    pOut.println("--InterruptFilter--");
    pOut.println("Timer for block:           " + totalTimer);
  }

}
