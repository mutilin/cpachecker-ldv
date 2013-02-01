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
package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.toState;
import static org.sosy_lab.cpachecker.util.StatisticsUtils.div;

import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * This class provides a basic refiner implementation for predicate analysis.
 * When a counterexample is found, it creates a path for it and checks it for
 * feasibility, getting the interpolants if possible.
 *
 * It does not define any strategy for using the interpolants to update the
 * abstraction, this is left to an instance of {@link RefinementStrategy}.
 *
 * It does, however, produce a nice error path in case of a feasible counterexample.
 */
@Options(prefix="cpa.predicate.refinement")
public class PredicateCPARefiner extends AbstractARGBasedRefiner implements StatisticsProvider {

  @Option(description="slice block formulas, experimental feature!")
  private boolean sliceBlockFormulas = false;

  @Option(name="msatCexFile",
      description="where to dump the counterexample formula in case the error location is reached")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private File dumpCexFile = new File("counterexample.msat");


  private class Stats implements Statistics {

    private final Statistics statistics = strategy.getStatistics();

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
      int numberOfRefinements = totalRefinement.getNumberOfIntervals();

      if (numberOfRefinements > 0) {
        out.println("Avg. length of target path (in blocks):     " + div(totalPathLength, numberOfRefinements));
        out.println("Avg. number of blocks unchanged in path:    " + div(totalUnchangedPrefixLength, numberOfSuccessfulRefinements));
        out.println("Avg. number of states with non-trivial itp: " + div(totalNumberOfStatesWithNonTrivialInterpolant, numberOfSuccessfulRefinements));
        out.println();
        out.println("Time for refinement:                  " + totalRefinement);
        formulaManager.printStatistics(out, result, reached);
        out.println("  Error path post-processing:         " + errorPathProcessing);
      }

      statistics.printStatistics(out, result, reached);
    }

    @Override
    public String getName() {
      return strategy.getStatistics().getName();
    }
  }

  // statistics
  private int numberOfSuccessfulRefinements = 0;
  private int totalPathLength = 0; // measured in blocks
  private int totalUnchangedPrefixLength = 0; // measured in blocks
  private int totalNumberOfStatesWithNonTrivialInterpolant = 0;

  private final Timer totalRefinement = new Timer();
  private final Timer errorPathProcessing = new Timer();


  private final LogManager logger;

  private final FormulaManagerView fmgr;
  private final PathFormulaManager pfmgr;
  private final InterpolationManager formulaManager;
  private final RefinementStrategy strategy;


  // the previously analyzed counterexample to detect repeated counterexamples
  private List<BooleanFormula> lastErrorPath = null;

  public PredicateCPARefiner(final Configuration config, final LogManager pLogger,
      final ConfigurableProgramAnalysis pCpa,
      final InterpolationManager pInterpolationManager,
      final FormulaManagerView pFormulaManager,
      final PathFormulaManager pPathFormulaManager,
      final RefinementStrategy pStrategy) throws CPAException, InvalidConfigurationException {

    super(pCpa);

    config.inject(this, PredicateCPARefiner.class);

    logger = pLogger;
    formulaManager = pInterpolationManager;
    fmgr = pFormulaManager;
    pfmgr = pPathFormulaManager;
    strategy = pStrategy;
  }

  @Override
  public final CounterexampleInfo performRefinement(final ARGReachedSet pReached, final ARGPath pPath) throws CPAException, InterruptedException {
    totalRefinement.start();

    Set<ARGState> elementsOnPath = ARGUtils.getAllStatesOnPathsTo(pPath.getLast().getFirst());

    boolean branchingOccurred = true;
    if (elementsOnPath.size() == pPath.size()) {
      // No branches/merges in path, it is precise.
      // We don't need to care about creating extra predicates for branching etc.
      elementsOnPath = Collections.emptySet();
      branchingOccurred = false;
    }

    logger.log(Level.FINEST, "Starting interpolation-based refinement");

    // create path with all abstraction location elements (excluding the initial element)
    // the last element is the element corresponding to the error location
    final List<ARGState> path = transformPath(pPath);
    totalPathLength += path.size();

    logger.log(Level.ALL, "Abstraction trace is", path);

    // create list of formulas on path
    final List<BooleanFormula> formulas = getFormulasForPath(path, pPath.getFirst().getFirst());
    assert path.size() == formulas.size();

    // build the counterexample
    final CounterexampleTraceInfo<BooleanFormula> counterexample = formulaManager.buildCounterexampleTrace(formulas, elementsOnPath);

    // if error is spurious refine
    if (counterexample.isSpurious()) {
      logger.log(Level.FINEST, "Error trace is spurious, refining the abstraction");
      numberOfSuccessfulRefinements++;

      boolean repeatedCounterexample = formulas.equals(lastErrorPath);
      lastErrorPath = formulas;

      // just statistics
      for (BooleanFormula interpolant : counterexample.getInterpolants()) {
        if (fmgr.getBooleanFormulaManager().isTrue(interpolant)) {
          totalUnchangedPrefixLength++;
        } else if (fmgr.getBooleanFormulaManager().isFalse(interpolant)) {
          break;
        } else {
          totalNumberOfStatesWithNonTrivialInterpolant++;
        }
      }

      strategy.performRefinement(pReached, path, counterexample, repeatedCounterexample);

      totalRefinement.stop();
      return CounterexampleInfo.spurious();

    } else {
      // we have a real error
      logger.log(Level.FINEST, "Error trace is not spurious");
      final ARGPath targetPath;
      final CounterexampleTraceInfo<BooleanFormula> preciseCounterexample;

      if (branchingOccurred) {
        Pair<ARGPath, CounterexampleTraceInfo<BooleanFormula>> preciseInfo = findPreciseErrorPath(pPath, counterexample);

        if (preciseInfo != null) {
          targetPath = preciseInfo.getFirst();
          preciseCounterexample = preciseInfo.getSecond();
        } else {
          logger.log(Level.WARNING, "The error path and the satisfying assignment may be imprecise!");
          targetPath = pPath;
          preciseCounterexample = counterexample;
        }
      } else {
        targetPath = pPath;
        preciseCounterexample = counterexample;
      }

      CounterexampleInfo cex = CounterexampleInfo.feasible(targetPath, preciseCounterexample.getCounterexample());

      cex.addFurtherInformation(new Object() {
        // lazily call formulaManager.dumpCounterexample()
        @Override
        public String toString() {
          return formulaManager.dumpCounterexample(preciseCounterexample);
        }
      }, dumpCexFile);

      totalRefinement.stop();
      return cex;
    }
  }

  static List<ARGState> transformPath(ARGPath pPath) {
    List<ARGState> result = from(pPath)
      .skip(1)
      .transform(Pair.<ARGState>getProjectionToFirst())
      .filter(Predicates.compose(PredicateAbstractState.FILTER_ABSTRACTION_STATES,
                                 toState(PredicateAbstractState.class)))
      .toImmutableList();

    assert from(result).allMatch(new Predicate<ARGState>() {
      @Override
      public boolean apply(@Nullable ARGState pInput) {
        boolean correct = pInput.getParents().size() <= 1;
        assert correct : "PredicateCPARefiner expects abstraction states to have only one parent, but this state has more:" + pInput;
        return correct;
      }
    });

    assert pPath.getLast().getFirst() == result.get(result.size()-1);
    return result;
  }

  private static final Function<PredicateAbstractState, BooleanFormula> GET_BLOCK_FORMULA
                = new Function<PredicateAbstractState, BooleanFormula>() {
                    @Override
                    public BooleanFormula apply(PredicateAbstractState e) {
                      assert e.isAbstractionState();
                      return e.getAbstractionFormula().getBlockFormula();
                    }
                  };

  /**
   * Get the block formulas from a path.
   * @param path A list of all abstraction elements
   * @param initialState The initial element of the analysis (= the root element of the ARG)
   * @return A list of block formulas for this path.
   * @throws CPATransferException
   */
  protected List<BooleanFormula> getFormulasForPath(List<ARGState> path, ARGState initialState)
      throws CPATransferException {
    if (sliceBlockFormulas) {
      BlockFormulaSlicer bfs = new BlockFormulaSlicer(pfmgr);
      return bfs.sliceFormulasForPath(path, initialState);
    } else {
      return from(path)
          .transform(toState(PredicateAbstractState.class))
          .transform(GET_BLOCK_FORMULA)
          .toImmutableList();
    }
  }

  private Pair<ARGPath, CounterexampleTraceInfo<BooleanFormula>> findPreciseErrorPath(ARGPath pPath, CounterexampleTraceInfo<BooleanFormula> counterexample) {
    errorPathProcessing.start();
    try {

      Map<Integer, Boolean> preds = counterexample.getBranchingPredicates();
      if (preds.isEmpty()) {
        logger.log(Level.WARNING, "No information about ARG branches available!");
        return null;
      }

      // find correct path
      ARGPath targetPath;
      try {
        ARGState root = pPath.getFirst().getFirst();
        ARGState target = pPath.getLast().getFirst();
        Set<ARGState> pathElements = ARGUtils.getAllStatesOnPathsTo(target);

        targetPath = ARGUtils.getPathFromBranchingInformation(root, target,
            pathElements, preds);

      } catch (IllegalArgumentException e) {
        logger.logUserException(Level.WARNING, e, null);
        return null;
      }

      // try to create a better satisfying assignment by replaying this single path
      CounterexampleTraceInfo<BooleanFormula> info2;
      try {
        info2 = formulaManager.checkPath(targetPath.asEdgesList());

      } catch (CPATransferException e) {
        // path is now suddenly a problem
        logger.logUserException(Level.WARNING, e, "Could not replay error path");
        return null;
      }

      if (info2.isSpurious()) {
        logger.log(Level.WARNING, "Inconsistent replayed error path!");
        return null;

      } else {
        return Pair.of(targetPath, info2);
      }

    } finally {
      errorPathProcessing.stop();
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(new Stats());
  }
}
