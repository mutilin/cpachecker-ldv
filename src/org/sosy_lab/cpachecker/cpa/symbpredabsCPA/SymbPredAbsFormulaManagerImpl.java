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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ForceStopCPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Abstraction;
import org.sosy_lab.cpachecker.util.symbpredabstraction.CommonFormulaManager;
import org.sosy_lab.cpachecker.util.symbpredabstraction.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.symbpredabstraction.PathFormula;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Predicate;
import org.sosy_lab.cpachecker.util.symbpredabstraction.SSAMap;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Cache.CartesianAbstractionCacheKey;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Cache.FeasibilityCacheKey;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Cache.TimeStampCache;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.AbstractFormula;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.AbstractFormulaManager;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.InterpolatingTheoremProver;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormula;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.SymbolicFormulaManager;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.TheoremProver;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.TheoremProver.AllSatResult;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;


@Options(prefix="cpas.symbpredabs")
class SymbPredAbsFormulaManagerImpl<T1, T2> extends CommonFormulaManager implements SymbPredAbsFormulaManager {

  static class Stats {
    public int numCallsAbstraction = 0;
    public int numCallsAbstractionCached = 0;
    public long abstractionSolveTime = 0;
    public long abstractionMaxSolveTime = 0;
    public long abstractionBddTime = 0;
    public long abstractionMaxBddTime = 0;
    public long allSatCount = 0;
    public int maxAllSatCount = 0;

    public int numCallsCexAnalysis = 0;
    public long cexAnalysisTime = 0;
    public long cexAnalysisMaxTime = 0;
    public long cexAnalysisSolverTime = 0;
    public long cexAnalysisMaxSolverTime = 0;
    public long cexAnalysisGetUsefulBlocksTime = 0;
    public long cexAnalysisGetUsefulBlocksMaxTime = 0;
  }
  final Stats stats;

  private final TheoremProver thmProver;
  private final InterpolatingTheoremProver<T1> firstItpProver;
  private final InterpolatingTheoremProver<T2> secondItpProver;

  private static final int MAX_CACHE_SIZE = 100000;

  @Option(name="abstraction.cartesian")
  private boolean cartesianAbstraction = false;

  @Option(name="mathsat.dumpHardAbstractionQueries")
  private boolean dumpHardAbstractions = false;

  @Option(name="explicit.getUsefulBlocks")
  private boolean getUsefulBlocks = false;

  @Option(name="shortestCexTrace")
  private boolean shortestTrace = false;

  @Option(name="refinement.atomicPredicates")
  private boolean atomicPredicates = true;

  @Option(name="refinement.splitItpAtoms")
  private boolean splitItpAtoms = false;

  @Option(name="shortestCexTraceUseSuffix")
  private boolean useSuffix = false;

  @Option(name="shortestCexTraceZigZag")
  private boolean useZigZag = false;

  @Option(name="refinement.addWellScopedPredicates")
  private boolean wellScopedPredicates = false;

  @Option(name="refinement.msatCexFile", type=Option.Type.OUTPUT_FILE)
  private File dumpCexFile = new File("counterexample.msat");

  @Option(name="refinement.dumpInterpolationProblems")
  private boolean dumpInterpolationProblems = false;

  @Option(name="formulaDumpFilePattern", type=Option.Type.OUTPUT_FILE)
  private File formulaDumpFile = new File("%s%04d-%s%03d.msat");
  private final String formulaDumpFilePattern; // = formulaDumpFile.getAbsolutePath()
  
  @Option(name="interpolation.timelimit")
  private long itpTimeLimit = 0;

  @Option(name="interpolation.changesolverontimeout")
  private boolean changeItpSolveOTF = false;

  @Option
  private boolean useBitwiseAxioms = false;
  
  private final Map<Pair<SymbolicFormula, Collection<Predicate>>, Abstraction> abstractionCache;
  //cache for cartesian abstraction queries. For each predicate, the values
  // are -1: predicate is false, 0: predicate is don't care,
  // 1: predicate is true
  private final TimeStampCache<CartesianAbstractionCacheKey, Byte> cartesianAbstractionCache;
  private final TimeStampCache<FeasibilityCacheKey, Boolean> feasibilityCache;

  public SymbPredAbsFormulaManagerImpl(
      AbstractFormulaManager pAmgr,
      SymbolicFormulaManager pSmgr,
      TheoremProver pThmProver,
      InterpolatingTheoremProver<T1> pItpProver,
      InterpolatingTheoremProver<T2> pAltItpProver,
      Configuration config,
      LogManager logger) throws InvalidConfigurationException {
    super(pAmgr, pSmgr, config, logger);
    config.inject(this);
    
    if (formulaDumpFile != null) {
      formulaDumpFilePattern = formulaDumpFile.getAbsolutePath();
    } else {
      dumpHardAbstractions = false;
      formulaDumpFilePattern = null;
    }

    stats = new Stats();
    thmProver = pThmProver;
    firstItpProver = pItpProver;
    secondItpProver = pAltItpProver;

    if (wellScopedPredicates) {
      throw new InvalidConfigurationException("wellScopePredicates are currently disabled");
    }
//    if (inlineFunctions && wellScopedPredicates) {
//      logger.log(Level.WARNING, "Well scoped predicates not possible with function inlining, disabling them.");
//      wellScopedPredicates = false;
//    }

    if (useCache) {
      abstractionCache = new HashMap<Pair<SymbolicFormula, Collection<Predicate>>, Abstraction>();
    } else {
      abstractionCache = null;
    }
    if (useCache && cartesianAbstraction) {
      cartesianAbstractionCache = new TimeStampCache<CartesianAbstractionCacheKey, Byte>(MAX_CACHE_SIZE);
      feasibilityCache = new TimeStampCache<FeasibilityCacheKey, Boolean>(MAX_CACHE_SIZE);
    } else {
      cartesianAbstractionCache = null;
      feasibilityCache = null;
    }
  }

  /**
   * Abstract post operation.
   */
  @Override
  public Abstraction buildAbstraction(
      Abstraction abstractionFormula, PathFormula pathFormula,
      Collection<Predicate> predicates) {

    stats.numCallsAbstraction++;

    logger.log(Level.ALL, "Old abstraction:", abstractionFormula);
    logger.log(Level.ALL, "Path formula:", pathFormula);
    logger.log(Level.ALL, "Predicates:", predicates);
    
    SymbolicFormula absFormula = abstractionFormula.asSymbolicFormula();
    SymbolicFormula symbFormula = buildSymbolicFormula(pathFormula.getSymbolicFormula());
    SymbolicFormula f = smgr.makeAnd(absFormula, symbFormula);
    
    // caching
    Pair<SymbolicFormula, Collection<Predicate>> absKey = null;
    if (useCache) {
      absKey = new Pair<SymbolicFormula, Collection<Predicate>>(f, predicates);
      Abstraction result = abstractionCache.get(absKey);

      if (result != null) {
        // create new abstraction object to have a unique abstraction id
        result = new Abstraction(result.asAbstractFormula(), result.asSymbolicFormula(), result.getBlockFormula());
        logger.log(Level.ALL, "Abstraction was cached, result is", result);
        stats.numCallsAbstractionCached++;
        return result;
      }
    }
    
    AbstractFormula abs;
    if (cartesianAbstraction) {
      abs = buildCartesianAbstraction(f, pathFormula.getSsa(), predicates);
    } else {
      abs = buildBooleanAbstraction(f, pathFormula.getSsa(), predicates);
    }
    
    SymbolicFormula symbolicAbs = smgr.instantiate(toConcrete(abs), pathFormula.getSsa());
    Abstraction result = new Abstraction(abs, symbolicAbs, pathFormula.getSymbolicFormula());

    if (useCache) {
      abstractionCache.put(absKey, result);
    }
    
    return result;
  }

  private AbstractFormula buildCartesianAbstraction(SymbolicFormula f, SSAMap ssa,
      Collection<Predicate> predicates) {
    
    byte[] predVals = null;
    final byte NO_VALUE = -2;
    if (useCache) {
      predVals = new byte[predicates.size()];
      int predIndex = -1;
      for (Predicate p : predicates) {
        ++predIndex;
        CartesianAbstractionCacheKey key =
          new CartesianAbstractionCacheKey(f, p);
        if (cartesianAbstractionCache.containsKey(key)) {
          predVals[predIndex] = cartesianAbstractionCache.get(key);
        } else {
          predVals[predIndex] = NO_VALUE;
        }
      }
    }

    boolean skipFeasibilityCheck = false;
    if (useCache) {
      FeasibilityCacheKey key = new FeasibilityCacheKey(f);
      if (feasibilityCache.containsKey(key)) {
        skipFeasibilityCheck = true;
        if (!feasibilityCache.get(key)) {
          // abstract post leads to false, we can return immediately
          return amgr.makeFalse();
        }
      }
    }

    long solveStartTime = System.currentTimeMillis();

    thmProver.init();
    try {

      if (!skipFeasibilityCheck) {
        //++stats.abstractionNumMathsatQueries;
        boolean unsat = thmProver.isUnsat(f);
        if (useCache) {
          FeasibilityCacheKey key = new FeasibilityCacheKey(f);
          feasibilityCache.put(key, !unsat);
        }
        if (unsat) {
          return amgr.makeFalse();
        }
      } else {
        //++stats.abstractionNumCachedQueries;
      }

      thmProver.push(f);
      try {
        long totBddTime = 0;

        AbstractFormula absbdd = amgr.makeTrue();

        // check whether each of the predicate is implied in the next state...

        int predIndex = -1;
        for (Predicate p : predicates) {
          ++predIndex;
          if (useCache && predVals[predIndex] != NO_VALUE) {
            long startBddTime = System.currentTimeMillis();
            AbstractFormula v = p.getAbstractVariable();
            if (predVals[predIndex] == -1) { // pred is false
              v = amgr.makeNot(v);
              absbdd = amgr.makeAnd(absbdd, v);
            } else if (predVals[predIndex] == 1) { // pred is true
              absbdd = amgr.makeAnd(absbdd, v);
            }
            long endBddTime = System.currentTimeMillis();
            totBddTime += (endBddTime - startBddTime);
            //++stats.abstractionNumCachedQueries;
          } else {            
            logger.log(Level.ALL, "DEBUG_1",
                "CHECKING VALUE OF PREDICATE: ", p.getSymbolicAtom());

            // instantiate the definition of the predicate
            SymbolicFormula predTrue = smgr.instantiate(p.getSymbolicAtom(), ssa);
            SymbolicFormula predFalse = smgr.makeNot(predTrue);

            // check whether this predicate has a truth value in the next
            // state
            byte predVal = 0; // pred is neither true nor false

            //++stats.abstractionNumMathsatQueries;
            boolean isTrue = thmProver.isUnsat(predFalse);

            if (isTrue) {
              long startBddTime = System.currentTimeMillis();
              AbstractFormula v = p.getAbstractVariable();
              absbdd = amgr.makeAnd(absbdd, v);
              long endBddTime = System.currentTimeMillis();
              totBddTime += (endBddTime - startBddTime);

              predVal = 1;
            } else {
              // check whether it's false...
              //++stats.abstractionNumMathsatQueries;
              boolean isFalse = thmProver.isUnsat(predTrue);

              if (isFalse) {
                long startBddTime = System.currentTimeMillis();
                AbstractFormula v = p.getAbstractVariable();
                v = amgr.makeNot(v);
                absbdd = amgr.makeAnd(absbdd, v);
                long endBddTime = System.currentTimeMillis();
                totBddTime += (endBddTime - startBddTime);

                predVal = -1;
              }
            }

            if (useCache) {
              CartesianAbstractionCacheKey key =
                new CartesianAbstractionCacheKey(f, p);
              cartesianAbstractionCache.put(key, predVal);
            }
          }
        }
        long solveEndTime = System.currentTimeMillis();

        // update statistics
        long solveTime = (solveEndTime - solveStartTime) - totBddTime;
        stats.abstractionMaxBddTime =
          Math.max(totBddTime, stats.abstractionMaxBddTime);
        stats.abstractionBddTime += totBddTime;
        stats.abstractionSolveTime += solveTime;
        stats.abstractionMaxSolveTime =
          Math.max(solveTime, stats.abstractionMaxSolveTime);

        return absbdd;

      } finally {
        thmProver.pop();
      }

    } finally {
      thmProver.reset();
    }
  }

  private SymbolicFormula buildSymbolicFormula(SymbolicFormula symbFormula) {

    if (useBitwiseAxioms) {
      SymbolicFormula bitwiseAxioms = smgr.getBitwiseAxioms(symbFormula);
      if (!bitwiseAxioms.isTrue()) {
        symbFormula = smgr.makeAnd(symbFormula, bitwiseAxioms);

        logger.log(Level.ALL, "DEBUG_3", "ADDED BITWISE AXIOMS:", bitwiseAxioms);
      }
    }
    
    return symbFormula;
  }

  /**
   * Checks if (a1 & p1) => a2
   */
  @Override
  public boolean checkCoverage(Abstraction a1, PathFormula p1, Abstraction a2) {
    SymbolicFormula absFormula = a1.asSymbolicFormula();
    SymbolicFormula symbFormula = buildSymbolicFormula(p1.getSymbolicFormula()); 
    SymbolicFormula a = smgr.makeAnd(absFormula, symbFormula);

    SymbolicFormula b = smgr.instantiate(a2.asSymbolicFormula(), p1.getSsa());

    SymbolicFormula toCheck = smgr.makeAnd(a, smgr.makeNot(b));

    thmProver.init();
    try {
      return thmProver.isUnsat(toCheck);
    } finally {
      thmProver.reset();
    }
  }

  private AbstractFormula buildBooleanAbstraction(SymbolicFormula f, SSAMap ssa,
      Collection<Predicate> predicates) {

    // first, create the new formula corresponding to
    // (symbFormula & edges from e to succ)
    // TODO - at the moment, we assume that all the edges connecting e and
    // succ have no statement or assertion attached (i.e. they are just
    // return edges or gotos). This might need to change in the future!!
    // (So, for now we don't need to to anything...)

    // build the definition of the predicates, and instantiate them
    // also collect all predicate variables so that the solver knows for which
    // variables we want to have the satisfying assignments
    SymbolicFormula predDef = smgr.makeTrue();
    List<SymbolicFormula> predVars = new ArrayList<SymbolicFormula>(predicates.size());

    for (Predicate p : predicates) {
      // get propositional variable and definition of predicate
      SymbolicFormula var = p.getSymbolicVariable();
      SymbolicFormula def = p.getSymbolicAtom();
      def = smgr.instantiate(def, ssa);
      
      // build the formula (var <-> def) and add it to the list of definitions
      SymbolicFormula equiv = smgr.makeEquivalence(var, def);
      predDef = smgr.makeAnd(predDef, equiv);

      predVars.add(var);
    }

    // the formula is (abstractionFormula & pathFormula & predDef)
    SymbolicFormula fm = smgr.makeAnd(f, predDef);

    logger.log(Level.ALL, "COMPUTING ALL-SMT ON FORMULA: ", fm);

    long solveStartTime = System.currentTimeMillis();
    AllSatResult allSatResult = thmProver.allSat(fm, predVars, this, amgr);
    long solveEndTime = System.currentTimeMillis();

    // update statistics
    int numModels = allSatResult.getCount();
    if (numModels < Integer.MAX_VALUE) {
      stats.maxAllSatCount = Math.max(numModels, stats.maxAllSatCount);
      stats.allSatCount += numModels;
    }
    long bddTime   = allSatResult.getTotalTime();
    long solveTime = (solveEndTime - solveStartTime) - bddTime;

    stats.abstractionSolveTime += solveTime;
    stats.abstractionBddTime   += bddTime;

    stats.abstractionMaxBddTime =
      Math.max(bddTime, stats.abstractionMaxBddTime);
    stats.abstractionMaxSolveTime =
      Math.max(solveTime, stats.abstractionMaxSolveTime);

    // TODO dump hard abst
    if (solveTime > 10000 && dumpHardAbstractions) {
      // we want to dump "hard" problems...
      String dumpFile = String.format(formulaDumpFilePattern,
                               "abstraction", stats.numCallsAbstraction, "input", 0);
      dumpFormulaToFile(f, new File(dumpFile));

      dumpFile = String.format(formulaDumpFilePattern,
                               "abstraction", stats.numCallsAbstraction, "predDef", 0);
      dumpFormulaToFile(predDef, new File(dumpFile));

      dumpFile = String.format(formulaDumpFilePattern,
                               "abstraction", stats.numCallsAbstraction, "predVars", 0);
      printFormulasToFile(predVars, new File(dumpFile));
    }

    AbstractFormula result = allSatResult.getResult();
    logger.log(Level.ALL, "Abstraction computed, result is", result);
    return result;
  }

  /**
   * Checks if an abstraction formula and a pathFormula are unsatisfiable.
   * @param pAbstractionFormula the abstraction formula
   * @param pPathFormula the path formula
   * @return unsat(pAbstractionFormula & pPathFormula)
   */
  @Override
  public boolean unsat(Abstraction abstractionFormula, PathFormula pathFormula) {
    SymbolicFormula absFormula = abstractionFormula.asSymbolicFormula();
    SymbolicFormula symbFormula = buildSymbolicFormula(pathFormula.getSymbolicFormula());
    SymbolicFormula f = smgr.makeAnd(absFormula, symbFormula);
    logger.log(Level.ALL, "Checking satisfiability of formula", f);

    thmProver.init();
    try {
      return thmProver.isUnsat(f);
    } finally {
      thmProver.reset();
    }
  }

  /**
   * Counterexample analysis and predicate discovery.
   * @param pAbstractTrace abstract trace of the error path
   * @param pItpProver interpolation solver used
   * @return counterexample info with predicated information
   * @throws CPAException
   */
  private <T> CounterexampleTraceInfo buildCounterexampleTraceWithSpecifiedItp(
      ArrayList<SymbPredAbsAbstractElement> pAbstractTrace, InterpolatingTheoremProver<T> pItpProver) throws CPAException {
    
    long startTime = System.currentTimeMillis();
    stats.numCallsCexAnalysis++;

    logger.log(Level.FINEST, "Building counterexample trace");

    List<SymbolicFormula> f = getFormulasForTrace(pAbstractTrace);

    if (useBitwiseAxioms) {
      SymbolicFormula bitwiseAxioms = smgr.makeTrue();
  
      for (SymbolicFormula fm : f) {
        SymbolicFormula a = smgr.getBitwiseAxioms(fm);
        if (!a.isTrue()) {
          bitwiseAxioms = smgr.makeAnd(bitwiseAxioms, a);  
        }
      }
  
      if (!bitwiseAxioms.isTrue()) {
        logger.log(Level.ALL, "DEBUG_3", "ADDING BITWISE AXIOMS TO THE",
            "LAST GROUP: ", bitwiseAxioms);
        int lastIndex = f.size()-1;
        f.set(lastIndex, smgr.makeAnd(f.get(lastIndex), bitwiseAxioms));
      }
    }

    f = Collections.unmodifiableList(f);

    logger.log(Level.ALL, "Counterexample trace formulas:", f);

    logger.log(Level.FINEST, "Checking feasibility of counterexample trace");

    // now f is the DAG formula which is satisfiable iff there is a
    // concrete counterexample

    // create a working environment
    pItpProver.init();

    long msatSolveTimeStart = System.currentTimeMillis();

    if (shortestTrace && getUsefulBlocks) {
      f = Collections.unmodifiableList(getUsefulBlocks(f, useSuffix, useZigZag));
    }

    if (dumpInterpolationProblems) {
      int k = 0;
      for (SymbolicFormula formula : f) {
        String dumpFile = String.format(formulaDumpFilePattern,
                    "interpolation", stats.numCallsCexAnalysis, "formula", k++);
        dumpFormulaToFile(formula, new File(dumpFile));
      }
    }

    List<T> itpGroupsIds = new ArrayList<T>(f.size());
    for (int i = 0; i < f.size(); i++) {
      itpGroupsIds.add(null);
    }

    boolean spurious;
    if (getUsefulBlocks || !shortestTrace) {
      // check all formulas in f at once

      for (int i = useSuffix ? f.size()-1 : 0;
      useSuffix ? i >= 0 : i < f.size(); i += useSuffix ? -1 : 1) {

        itpGroupsIds.set(i, pItpProver.addFormula(f.get(i)));
      }
      spurious = pItpProver.isUnsat();

    } else {
      spurious = checkInfeasabilityOfShortestTrace(f, itpGroupsIds, pItpProver);
    }
    assert itpGroupsIds.size() == f.size();
    assert !itpGroupsIds.contains(null); // has to be filled completely

    long msatSolveTimeEnd = System.currentTimeMillis();
    long msatSolveTime = msatSolveTimeEnd - msatSolveTimeStart;

    logger.log(Level.FINEST, "Counterexample trace is", (spurious ? "infeasible" : "feasible"));

    CounterexampleTraceInfo info;

    if (spurious) {
      info = new CounterexampleTraceInfo();

      // the counterexample is spurious. Extract the predicates from
      // the interpolants

      // how to partition the trace into (A, B) depends on whether
      // there are function calls involved or not: in general, A
      // is the trace from the entry point of the current function
      // to the current point, and B is everything else. To implement
      // this, we keep track of which function we are currently in.
      // if we don't want "well-scoped" predicates, A always starts at the beginning
      Deque<Integer> entryPoints = null;
      if (wellScopedPredicates) {
        entryPoints = new ArrayDeque<Integer>();
        entryPoints.push(0);
      }
      boolean foundPredicates = false;

      for (int i = 0; i < f.size()-1; ++i) {
        // last iteration is left out because B would be empty
        final int start_of_a = (wellScopedPredicates ? entryPoints.peek() : 0);
        SymbPredAbsAbstractElement e = pAbstractTrace.get(i);

        logger.log(Level.ALL, "Looking for interpolant for formulas from",
            start_of_a, "to", i);

        msatSolveTimeStart = System.currentTimeMillis();
        SymbolicFormula itp = pItpProver.getInterpolant(itpGroupsIds.subList(start_of_a, i+1));
        msatSolveTimeEnd = System.currentTimeMillis();
        msatSolveTime += msatSolveTimeEnd - msatSolveTimeStart;

        if (dumpInterpolationProblems) {
          String dumpFile = String.format(formulaDumpFilePattern,
                  "interpolation", stats.numCallsCexAnalysis, "interpolant", i);
          dumpFormulaToFile(itp, new File(dumpFile));
        }

        if (itp.isTrue() || itp.isFalse()) {
          logger.log(Level.ALL, "For step", i, "got no interpolant.");

        } else {
          foundPredicates = true;

          Collection<Predicate> preds = getAtomsAsPredicates(itp);
          assert !preds.isEmpty();
          info.addPredicatesForRefinement(e, preds);

          logger.log(Level.ALL, "For step", i, "got:",
              "interpolant", itp,
              "predicates", preds);

          if (dumpInterpolationProblems) {
            String dumpFile = String.format(formulaDumpFilePattern,
                        "interpolation", stats.numCallsCexAnalysis, "atoms", i);
            Collection<SymbolicFormula> atoms = Collections2.transform(preds,
                new Function<Predicate, SymbolicFormula>(){
                      @Override
                      public SymbolicFormula apply(Predicate pArg0) {
                        return pArg0.getSymbolicAtom();
                      }
                });
            printFormulasToFile(atoms, new File(dumpFile));
          }
        }

        // TODO wellScopedPredicates have been disabled
        
        // TODO the following code relies on the fact that there is always an abstraction on function call and return

        // If we are entering or exiting a function, update the stack
        // of entry points
        // TODO checking if the abstraction node is a new function
//        if (wellScopedPredicates && e.getAbstractionLocation() instanceof CFAFunctionDefinitionNode) {
//          entryPoints.push(i);
//        }
          // TODO check we are returning from a function
//        if (wellScopedPredicates && e.getAbstractionLocation().getEnteringSummaryEdge() != null) {
//          entryPoints.pop();
//        }
      }

      if (!foundPredicates) {
        throw new RefinementFailedException(RefinementFailedException.Reason.InterpolationFailed, null);
      }

    } else {
      // this is a real bug, notify the user
      
      // TODO - reconstruct counterexample
      // For now, we dump the asserted formula to a user-specified file
      if (dumpCexFile != null) {
        SymbolicFormula cex = smgr.makeTrue();
        for (SymbolicFormula part : f) {
          cex = smgr.makeAnd(cex, part);
        }
        dumpFormulaToFile(cex, dumpCexFile);
      }
      
      // get the reachingPathsFormula and add it to the solver environment
      // this formula contains predicates for all branches we took
      // this way we can figure out which branches make a feasible path
      SymbPredAbsAbstractElement lastElement = pAbstractTrace.get(pAbstractTrace.size()-1);
      pItpProver.addFormula(lastElement.getPathFormula().getReachingPathsFormula());
      
      // need to ask solver for satisfiability again,
      // otherwise model doesn't contain new predicates
      boolean stillSatisfiable = !pItpProver.isUnsat();
      if (!stillSatisfiable) {
        pItpProver.reset();
        pItpProver.init();
        logger.log(Level.WARNING, "Could not get precise error path information because of inconsistent reachingPathsFormula!");

        int k = 0;
        for (SymbolicFormula formula : f) {
          pItpProver.addFormula(formula);
          String dumpFile = String.format(formulaDumpFilePattern,
                      "interpolation", stats.numCallsCexAnalysis, "formula", k++);
          dumpFormulaToFile(formula, new File(dumpFile));
        }
        String dumpFile = String.format(formulaDumpFilePattern,
            "interpolation", stats.numCallsCexAnalysis, "formula", k++);
        dumpFormulaToFile(lastElement.getPathFormula().getReachingPathsFormula(), new File(dumpFile));
        pItpProver.isUnsat();
      }
      
      info = new CounterexampleTraceInfo(pItpProver.getModel());
    }

    pItpProver.reset();

    // update stats
    long endTime = System.currentTimeMillis();
    long totTime = endTime - startTime;
    stats.cexAnalysisTime += totTime;
    stats.cexAnalysisMaxTime = Math.max(totTime, stats.cexAnalysisMaxTime);
    stats.cexAnalysisSolverTime += msatSolveTime;
    stats.cexAnalysisMaxSolverTime =
      Math.max(msatSolveTime, stats.cexAnalysisMaxSolverTime);

    logger.log(Level.ALL, "Counterexample information:", info);

    return info;

  }

  /**
   * Counterexample analysis and predicate discovery.
   * This method is just an helper to delegate the actual work
   * This is used to detect timeouts for interpolation
   * @throws CPAException
   */
  @Override
  public CounterexampleTraceInfo buildCounterexampleTrace(
      ArrayList<SymbPredAbsAbstractElement> pAbstractTrace) throws CPAException {
    
    // if we don't want to limit the time given to the solver
    if (itpTimeLimit == 0) {
      return buildCounterexampleTraceWithSpecifiedItp(pAbstractTrace, firstItpProver);
    }
    
    // how many times is the problem tried to be solved so far?
    int noOfTries = 0;
    
    while (true) {
      TransferCallable<?> tc;
      
      if (noOfTries == 0) {
        tc = new TransferCallable<T1>(pAbstractTrace, firstItpProver);
      } else {
        tc = new TransferCallable<T2>(pAbstractTrace, secondItpProver);
      }

      Future<CounterexampleTraceInfo> future = CEGARAlgorithm.executor.submit(tc);

      try {
        // here we get the result of the post computation but there is a time limit
        // given to complete the task specified by timeLimit
        return future.get(itpTimeLimit, TimeUnit.MILLISECONDS);
        
      } catch (TimeoutException e){
        // if first try failed and changeItpSolveOTF enabled try the alternative solver
        if (changeItpSolveOTF && noOfTries == 0) {
          logger.log(Level.WARNING, "SMT-solver timed out during interpolation process, trying next solver.");
          noOfTries++;

        } else {
          logger.log(Level.SEVERE, "SMT-solver timed out during interpolation process");
          throw new RefinementFailedException(Reason.TIMEOUT, null);
        }
      } catch (InterruptedException e) {
        throw new ForceStopCPAException();
      
      } catch (ExecutionException e) {
        Throwable t = e.getCause();
        Throwables.propagateIfPossible(t, CPAException.class);
        
        logger.logException(Level.SEVERE, t, "Unexpected exception during interpolation!");
        throw new ForceStopCPAException();
      }
    }
  }

  private List<SymbolicFormula> getFormulasForTrace(
      List<SymbPredAbsAbstractElement> abstractTrace) {

    // create the DAG formula corresponding to the abstract trace. We create
    // n formulas, one per interpolation group
    List<SymbolicFormula> result = new ArrayList<SymbolicFormula>(abstractTrace.size());

    for (SymbPredAbsAbstractElement e : abstractTrace) {
      result.add(e.getAbstraction().getBlockFormula());
    }
    return result;
  }

  private <T> boolean checkInfeasabilityOfShortestTrace(List<SymbolicFormula> traceFormulas,
        List<T> itpGroupsIds, InterpolatingTheoremProver<T> pItpProver) {
    Boolean tmpSpurious = null;

    if (useZigZag) {
      int e = traceFormulas.size()-1;
      int s = 0;
      boolean fromStart = false;
      while (s <= e) {
        int i = fromStart ? s : e;
        if (fromStart) s++;
        else e--;
        fromStart = !fromStart;

        tmpSpurious = null;
        SymbolicFormula fm = traceFormulas.get(i);
        itpGroupsIds.set(i, pItpProver.addFormula(fm));
        if (!fm.isTrue()) {
          if (pItpProver.isUnsat()) {
            tmpSpurious = Boolean.TRUE;
            for (int j = s; j <= e; ++j) {
              itpGroupsIds.set(j, pItpProver.addFormula(traceFormulas.get(j)));
            }
            break;
          } else {
            tmpSpurious = Boolean.FALSE;
          }
        }
      }

    } else {
      for (int i = useSuffix ? traceFormulas.size()-1 : 0;
      useSuffix ? i >= 0 : i < traceFormulas.size(); i += useSuffix ? -1 : 1) {

        tmpSpurious = null;
        SymbolicFormula fm = traceFormulas.get(i);
        itpGroupsIds.set(i, pItpProver.addFormula(fm));
        if (!fm.isTrue()) {
          if (pItpProver.isUnsat()) {
            tmpSpurious = Boolean.TRUE;
            // we need to add the other formulas to the itpProver
            // anyway, so it can setup its internal state properly
            for (int j = i+(useSuffix ? -1 : 1);
            useSuffix ? j >= 0 : j < traceFormulas.size();
            j += useSuffix ? -1 : 1) {
              itpGroupsIds.set(j, pItpProver.addFormula(traceFormulas.get(j)));
            }
            break;
          } else {
            tmpSpurious = Boolean.FALSE;
          }
        }
      }
    }

    return (tmpSpurious == null) ? pItpProver.isUnsat() : tmpSpurious;
  }

  private List<SymbolicFormula> getUsefulBlocks(List<SymbolicFormula> f,
      boolean suffixTrace, boolean zigZag) {
    long gubStart = System.currentTimeMillis();

    // try to find a minimal-unsatisfiable-core of the trace (as Blast does)

    thmProver.init();

    logger.log(Level.ALL, "DEBUG_1", "Calling getUsefulBlocks on path",
        "of length:", f.size());

    SymbolicFormula trueFormula = smgr.makeTrue();
    SymbolicFormula[] needed = new SymbolicFormula[f.size()];
    for (int i = 0; i < needed.length; ++i) {
      needed[i] = trueFormula;
    }
    int pos = suffixTrace ? f.size()-1 : 0;
    int incr = suffixTrace ? -1 : 1;
    int toPop = 0;

    while (true) {
      boolean consistent = true;
      // 1. assert all the needed constraints
      for (int i = 0; i < needed.length; ++i) {
        if (!needed[i].isTrue()) {
          thmProver.push(needed[i]);
          ++toPop;
        }
      }
      // 2. if needed is inconsistent, then return it
      if (thmProver.isUnsat(trueFormula)) {
        f = Arrays.asList(needed);
        break;
      }
      // 3. otherwise, assert one block at a time, until we get an
      // inconsistency
      if (zigZag) {
        int s = 0;
        int e = f.size()-1;
        boolean fromStart = false;
        while (true) {
          int i = fromStart ? s : e;
          if (fromStart) ++s;
          else --e;
          fromStart = !fromStart;

          SymbolicFormula t = f.get(i);
          thmProver.push(t);
          ++toPop;
          if (thmProver.isUnsat(trueFormula)) {
            // add this block to the needed ones, and repeat
            needed[i] = t;
            logger.log(Level.ALL, "DEBUG_1",
                "Found needed block: ", i, ", term: ", t);
            // pop all
            while (toPop > 0) {
              --toPop;
              thmProver.pop();
            }
            // and go to the next iteration of the while loop
            consistent = false;
            break;
          }

          if (e < s) break;
        }
      } else {
        for (int i = pos; suffixTrace ? i >= 0 : i < f.size();
        i += incr) {
          SymbolicFormula t = f.get(i);
          thmProver.push(t);
          ++toPop;
          if (thmProver.isUnsat(trueFormula)) {
            // add this block to the needed ones, and repeat
            needed[i] = t;
            logger.log(Level.ALL, "DEBUG_1",
                "Found needed block: ", i, ", term: ", t);
            // pop all
            while (toPop > 0) {
              --toPop;
              thmProver.pop();
            }
            // and go to the next iteration of the while loop
            consistent = false;
            break;
          }
        }
      }
      if (consistent) {
        // if we get here, the trace is consistent:
        // this is a real counterexample!
        break;
      }
    }

    while (toPop > 0) {
      --toPop;
      thmProver.pop();
    }

    thmProver.reset();

    logger.log(Level.ALL, "DEBUG_1", "Done getUsefulBlocks");

    long gubEnd = System.currentTimeMillis();
    stats.cexAnalysisGetUsefulBlocksTime += gubEnd - gubStart;
    stats.cexAnalysisGetUsefulBlocksMaxTime = Math.max(
        stats.cexAnalysisGetUsefulBlocksMaxTime, gubEnd - gubStart);

    return f;
  }

  @Override
  public List<Predicate> getAtomsAsPredicates(SymbolicFormula f) {
    Collection<SymbolicFormula> atoms;
    if (atomicPredicates) {
      atoms = smgr.extractAtoms(f, splitItpAtoms, false);
    } else {
      atoms = Collections.singleton(smgr.uninstantiate(f));
    }

    List<Predicate> preds = new ArrayList<Predicate>(atoms.size());

    for (SymbolicFormula atom : atoms) {
      preds.add(makePredicate(smgr.createPredicateVariable(atom), atom));
    }
    return preds;    
  }
  
  private class TransferCallable<T> implements Callable<CounterexampleTraceInfo> {

    private final ArrayList<SymbPredAbsAbstractElement> abstractTrace;
    private final InterpolatingTheoremProver<T> currentItpProver;

    public TransferCallable(ArrayList<SymbPredAbsAbstractElement> pAbstractTrace,
        InterpolatingTheoremProver<T> pItpProver) {
      abstractTrace = pAbstractTrace;
      currentItpProver = pItpProver;
    }

    @Override
    public CounterexampleTraceInfo call() throws CPAException {
      return buildCounterexampleTraceWithSpecifiedItp(abstractTrace, currentItpProver);
    }
  }
}
