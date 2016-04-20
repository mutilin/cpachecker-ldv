/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.formulaslicing;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.util.predicates.pathformula.CachingPathFormulaManager;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * Statistics for formula slicing.
 */
class FormulaSlicingStatistics implements Statistics {

  private final CachingPathFormulaManager cachingPathFormulaManager;

  FormulaSlicingStatistics(CachingPathFormulaManager pFmgr) {
    cachingPathFormulaManager = pFmgr;
  }

  /**
   * Time spent constructing formulas.
   */
  final Timer propagation = new Timer();

  /**
   * Time spent in inductive weakening.
   */
  final Timer inductiveWeakening = new Timer();
  final Multiset<CFANode> inductiveWeakeningLocations = HashMultiset.create();
  int cachedInductiveWeakenings = 0;

  /**
   * Reachability statistics.
   */
  final Multiset<CFANode> satChecksLocations = HashMultiset.create();
  final Timer reachabilityTargetTimer = new Timer();
  int cachedTargetReachabilityChecks = 0;

  final Timer reachabilityAbstractionTimer = new Timer();
  int cachedAbstractionReachabilityChecks = 0;

  void recordReachabilityCacheHit(boolean isTarget) {
    if (isTarget) {
      cachedTargetReachabilityChecks++;
    } else {
      cachedAbstractionReachabilityChecks++;
    }
  }

  @Override
  public void printStatistics(PrintStream out,
                              Result result,
                              ReachedSet reached) {
    printTimer(out, propagation, "propagating formulas",
        cachingPathFormulaManager.pathFormulaCacheHits);

    printTimer(out, inductiveWeakening, "inductive weakening",
        cachedInductiveWeakenings);

    printTimer(out, reachabilityTargetTimer,
        "checking reachability for target states",
        cachedTargetReachabilityChecks);
    printTimer(out, reachabilityAbstractionTimer,
        "checking reachability for abstraction states",
        cachedAbstractionReachabilityChecks);
  }

  @Override
  public String getName() {
    return "Formula Slicing Manager";
  }

  private void printTimer(PrintStream out, Timer t, String name,
                          int cacheHits) {
    out.printf("Time spent in %s: %s (Max: %s), (Avg: %s), (#calls = %s), "
        + "(#cached = %d) %n",
        name,
        t.getSumTime().formatAs(TimeUnit.SECONDS),
        t.getMaxTime().formatAs(TimeUnit.SECONDS),
        t.getAvgTime().formatAs(TimeUnit.SECONDS),
        t.getNumberOfIntervals(),
        cacheHits);
  }
}
