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
package org.sosy_lab.cpachecker.cpa.symbpredabsCPA;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.Predicate;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormula;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

@Options(prefix="cpas.symbpredabs.predmap")
public class SymbPredAbsCPAStatistics implements Statistics {

    @Option
    private boolean export = true;

    @Option(type=Option.Type.OUTPUT_FILE)
    private File file = new File("predmap.txt");

    private final SymbPredAbsCPA cpa;

    public SymbPredAbsCPAStatistics(SymbPredAbsCPA cpa) throws InvalidConfigurationException {
      this.cpa = cpa;
      cpa.getConfiguration().inject(this);
    }

    @Override
    public String getName() {
      return "Summary Predicate Abstraction CPA";
    }

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
      SymbPredAbsFormulaManagerImpl<?, ?> amgr = cpa.getFormulaManager();

      Multimap<CFANode, Predicate> predicates = HashMultimap.create();

      for (AbstractElement e : reached) {
        Precision precision = reached.getPrecision(e);
        if (precision != null && precision instanceof WrapperPrecision) {

          SymbPredAbsPrecision preds = ((WrapperPrecision)precision).retrieveWrappedPrecision(SymbPredAbsPrecision.class);
          predicates.putAll(preds.getPredicateMap());
        }
      }

      Set<Predicate> allPreds = new HashSet<Predicate>(predicates.values());
      Collection<CFANode> allLocs = predicates.keySet();
      int maxPredsPerLocation = 0;
      int totPredsUsed = 0;

      for (CFANode l : allLocs) {
        Collection<Predicate> p = predicates.get(l);
        maxPredsPerLocation = Math.max(maxPredsPerLocation, p.size());
        totPredsUsed += p.size();
      }
      int avgPredsPerLocation = allLocs.size() > 0 ? totPredsUsed/allLocs.size() : 0;

      // check if/where to dump the predicate map
      if (result == Result.SAFE && export) {

        try {
          PrintWriter pw = new PrintWriter(file);
          pw.println("ALL PREDICATES:");
          for (Predicate p : allPreds) {
            Pair<SymbolicFormula, SymbolicFormula> d = amgr.getPredicateVarAndAtom(p);
            pw.format("%s ==> %s <-> %s\n", p, d.getFirst(), d.getSecond());
          }

          pw.println("\nFOR EACH LOCATION:");
          for (CFANode l : allLocs) {
            Collection<Predicate> c = predicates.get(l);
            pw.println("\nLOCATION: " + l);
            for (Predicate p : c) {
              pw.println(p);
            }
          }
          pw.flush();
          pw.close();
          if (pw.checkError()) {
            cpa.getLogger().log(Level.WARNING, "Could not write predicate map to file ", file);
          }
        } catch (FileNotFoundException e) {
          cpa.getLogger().log(Level.WARNING, "Could not write predicate map to file ", file,
              (e.getMessage() != null ? "(" + e.getMessage() + ")" : ""));
        }
      }

      SymbPredAbsFormulaManagerImpl.Stats bs = amgr.stats;
      SymbPredAbsTransferRelation trans = cpa.getTransferRelation();

      out.println("Number of abstraction steps:       " + bs.numCallsAbstraction + " (" + bs.numCallsAbstractionCached + " cached)");
      out.println("Max LBE block size:                " + trans.maxBlockSize);
      out.println("Number of abstractions:            " + trans.numAbstractions);
      out.println("Number of satisfiability checks:   " + trans.numSatChecks);
      out.println("Number of refinement steps:        " + bs.numCallsCexAnalysis);
      out.println("Number of coverage checks:         " + bs.numCoverageChecks);
      out.println();
      out.println("Number of predicates discovered:          " + allPreds.size());
      out.println("Number of abstraction locations:          " + allLocs.size());
      out.println("Max number of predicates per location:    " + maxPredsPerLocation);
      out.println("Avg number of predicates per location:    " + avgPredsPerLocation);
      out.println("Max number of predicates per abstraction: " + trans.maxPredsPerAbstraction);
      out.println("Total number of models for allsat:        " + bs.allSatCount);
      out.println("Max number of models for allsat:          " + bs.maxAllSatCount);
      if (bs.numCallsAbstraction > 0) {
        out.println("Avg number of models for allsat:          " + bs.allSatCount / bs.numCallsAbstraction);
      }
      out.println();
      out.println("Time for merge:                " + toTime(cpa.getMergeOperator().totalMergeTime));
      out.println("Time for abstraction post:     " + toTime(trans.abstractionTime));
      out.println("  initial abstraction formula: " + toTime(trans.initAbstractionFormulaTime));
      out.println("  computing abstraction:       " + toTime(trans.computingAbstractionTime));
      out.println("    Time for All-SMT: ");
      out.println("      Total:                   " + toTime(bs.abstractionTime));
      out.println("      Max:                     " + toTime(bs.abstractionMaxTime));
      out.println("      Solving time only:       " + toTime(bs.abstractionSolveTime));
      out.println("    Time for BDD construction: ");
      out.println("      Total:                   " + toTime(bs.abstractionBddTime));
      out.println("      Max:                     " + toTime(bs.abstractionMaxBddTime));
      out.println("    Time for coverage check: ");
      out.println("      Total:                   " + toTime(bs.bddCoverageCheckTime));
      out.println("      Max:                     " + toTime(bs.bddCoverageCheckMaxTime));
      out.println("Time for non-abstraction post: " + toTime(trans.nonAbstractionTime));
      out.println("Time for finding path formula: " + toTime(trans.pathFormulaTime));
      out.println("  actual computation of PF:    " + toTime(trans.pathFormulaComputationTime));
      out.println("Time for counterexample analysis/abstraction refinement: ");
      out.println("  Total:                       " + toTime(bs.cexAnalysisTime));
      out.println("  Max:                         " + toTime(bs.cexAnalysisMaxTime));
      out.println("  Solving time only:           " + toTime(bs.cexAnalysisSolverTime));
      if (bs.cexAnalysisGetUsefulBlocksTime != 0) {
        out.println("  Cex.focusing total:          " + toTime(bs.cexAnalysisGetUsefulBlocksTime));
        out.println("  Cex.focusing max:            " + toTime(bs.cexAnalysisGetUsefulBlocksMaxTime));
      }
    }

    private String toTime(long timeMillis) {
      return String.format("% 5d.%03ds", timeMillis/1000, timeMillis%1000);
    }
}