/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import java.io.PrintStream;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

class AutomatonStatistics implements Statistics {

  private final ControlAutomatonCPA mCpa;

  public AutomatonStatistics(ControlAutomatonCPA pCpa) {
    mCpa = pCpa;
  }

  @Override
  public String getName() {
    return "AutomatonAnalysis (" + mCpa.getAutomaton().getName() + ")";
  }

  @Override
  public void printStatistics(PrintStream out, Result pResult,
      ReachedSet pReached) {

    AutomatonTransferRelation trans = mCpa.getTransferRelation();
    out.println("Total time for sucessor computation: " + trans.totalPostTime);
    out.println("  Time for transition matches:       " + trans.matchTime);
    out.println("  Time for transition assertions:    " + trans.assertionsTime);
    out.println("  Time for transition actions:       " + trans.actionTime);
    out.println("Total time for strengthen operator:  " + trans.totalStrengthenTime);
  }
}
