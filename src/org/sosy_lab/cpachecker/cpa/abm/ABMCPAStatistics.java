/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.abm;

import static com.google.common.base.Preconditions.checkState;

import java.io.PrintStream;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

/**
 * Prints some ABM related statistics
 */
class ABMCPAStatistics implements Statistics {

    private final ABMCPA cpa;
    private AbstractABMBasedRefiner refiner = null;

    public ABMCPAStatistics(ABMCPA cpa) {
      this.cpa = cpa;
    }

    @Override
    public String getName() {
      return "ABMCPA";
    }

    public void addRefiner(AbstractABMBasedRefiner pRefiner) {
      checkState(refiner == null);
      refiner = pRefiner;
    }

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

      ABMTransferRelation transferRelation = cpa.getTransferRelation();
      TimedReducer reducer = cpa.getReducer();

      int sumCalls = transferRelation.cacheMisses + transferRelation.partialCacheHits + transferRelation.fullCacheHits;

      out.println("Maximum block depth:                                            " + transferRelation.maxRecursiveDepth);
      out.println("Total number of recursive CPA calls:                            " + sumCalls);
      out.println("  Number of cache misses:                                       " + transferRelation.cacheMisses + " (" + toPercent(transferRelation.cacheMisses, sumCalls) + " of all calls)");
      out.println("  Number of partial cache hits:                                 " + transferRelation.partialCacheHits + " (" + toPercent(transferRelation.partialCacheHits, sumCalls) + " of all calls)");
      out.println("  Number of full cache hits:                                    " + transferRelation.fullCacheHits + " (" + toPercent(transferRelation.fullCacheHits, sumCalls) + " of all calls)");
      out.println("Time for reducing abstract elements:                            " + reducer.reduceTime + " (Calls: " + reducer.reduceTime.getNumberOfIntervals() + ")");
      out.println("Time for expanding abstract elements:                           " + reducer.expandTime + " (Calls: " + reducer.expandTime.getNumberOfIntervals() + ")");
      out.println("Time for checking equality of abstract elements:                " + transferRelation.equalsTimer + " (Calls: " + transferRelation.equalsTimer.getNumberOfIntervals() + ")");
      out.println("Time for computing the hashCode of abstract elements:           " + transferRelation.hashingTimer + " (Calls: " + transferRelation.hashingTimer.getNumberOfIntervals() + ")");
      out.println("Time for reducing precisions:                                   " + reducer.reducePrecisionTime + " (Calls: " + reducer.reducePrecisionTime.getNumberOfIntervals() + ")");
      out.println("Time for expanding precisions:                                  " + reducer.expandPrecisionTime + " (Calls: " + reducer.expandPrecisionTime.getNumberOfIntervals() + ")");

      out.println("Time for removing cached subtrees for refinement:               " + transferRelation.removeCachedSubtreeTimer);
      out.println("Time for recomputing ARTs during counterexample analysis:       " + transferRelation.recomputeARTTimer);
      if (refiner != null) {
        out.println("Compute path for refinement:                                    " + refiner.computePathTimer);
        out.println("  Constructing flat ART:                                        " + refiner.computeSubtreeTimer);
        out.println("  Searching path to error location:                             " + refiner.computeCounterexampleTimer);
      }
    }


    private String toPercent(double val, double full) {
      return String.format("%1.0f", val/full*100) + "%";
    }
}