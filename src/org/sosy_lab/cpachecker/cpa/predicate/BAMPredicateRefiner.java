/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.getPredicateState;
import static org.sosy_lab.cpachecker.util.AbstractStates.*;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.AbstractBAMBasedRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RefineableRelevantPredicatesComputer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RelevantPredicatesComputer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.refinement.PrefixProvider;

import com.google.common.base.Function;
import com.google.common.collect.Sets;


/**
 * Implements predicate refinements when using BAM.
 * It is based on the {@link AbstractBAMBasedRefiner} and delegates the work to
 * a {@link ExtendedPredicateRefiner}, which is a small extension of the regular
 * {@link PredicateCPARefiner}.
 *
 * So the hierarchy is as follows:
 *
 *               AbstractARGBasedRefiner
 *                         ^
 *                         |                                PredicateAbstractionRefinementStrategy
 *           +-------------+-------------+                                    ^
 *           |                           |                                    |
 * AbstractBAMBasedRefiner       PredicateCPARefiner ---> BAMPredicateAbstractionRefinementStrategy
 *           ^                           ^
 *           |                           |
 *   BAMPredicateRefiner ---> ExtendedPredicateRefiner
 *
 * Here ^ means inheritance and -> means reference.
 */
public final class BAMPredicateRefiner extends AbstractBAMBasedRefiner implements StatisticsProvider {

  private final ExtendedPredicateRefiner refiner;


  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    return new BAMPredicateRefiner(pCpa);
  }

  public BAMPredicateRefiner(final ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {

    super(pCpa);

    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    BAMPredicateCPA predicateCpa = ((WrapperCPA)pCpa).retrieveWrappedCpa(BAMPredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " needs an BAMPredicateCPA");
    }

    LogManager logger = predicateCpa.getLogger();

    InterpolationManager manager = new InterpolationManager(
                                          predicateCpa.getPathFormulaManager(),
                                          predicateCpa.getSolver(),
                                          predicateCpa.getCfa().getLoopStructure(),
                                          predicateCpa.getCfa().getVarClassification(),
                                          predicateCpa.getConfiguration(),
                                          predicateCpa.getShutdownNotifier(),
                                          logger);

    PathChecker pathChecker = new PathChecker(
                                          predicateCpa.getConfiguration(),
                                          logger,
                                          predicateCpa.getShutdownNotifier(),
                                          predicateCpa.getMachineModel(),
                                          predicateCpa.getPathFormulaManager(),
                                          predicateCpa.getSolver());


    PrefixProvider prefixProvider = new PredicateBasedPrefixProvider(predicateCpa.getConfiguration(),
                                          logger,
                                          predicateCpa.getSolver(),
                                          predicateCpa.getPathFormulaManager());

    RefinementStrategy strategy = new BAMPredicateAbstractionRefinementStrategy(
                                          predicateCpa.getConfiguration(),
                                          logger,
                                          predicateCpa,
                                          predicateCpa.getSolver(),
                                          predicateCpa.getPredicateManager(),
                                          predicateCpa.getStaticRefiner());

    this.refiner = new ExtendedPredicateRefiner(
                                          predicateCpa.getConfiguration(),
                                          logger,
                                          pCpa,
                                          manager,
                                          pathChecker,
                                          prefixProvider,
                                          predicateCpa.getPathFormulaManager(),
                                          strategy,
                                          predicateCpa.getSolver(),
                                          predicateCpa.getAssumesStore(),
                                          predicateCpa.getCfa());
  }

  @Override
  protected final CounterexampleInfo performRefinement0(ARGReachedSet pReached, ARGPath pPath)
      throws CPAException, InterruptedException {

    return refiner.performRefinement(pReached, pPath);
  }

  /**
   * This is a small extension of PredicateCPARefiner that overrides
   * {@link #getFormulasForPath(List, ARGState)} so that it respects BAM.
   */
  private static final class ExtendedPredicateRefiner extends PredicateCPARefiner {

    private final Timer ssaRenamingTimer = new Timer();

    private ExtendedPredicateRefiner(final Configuration config, final LogManager logger,
        final ConfigurableProgramAnalysis pCpa,
        final InterpolationManager pInterpolationManager,
        final PathChecker pPathChecker,
        final PrefixProvider pPrefixProvider,
        final PathFormulaManager pPathFormulaManager,
        final RefinementStrategy pStrategy,
        final Solver pSolver,
        final PredicateAssumeStore pAssumesStore,
        final CFA pCfa)
            throws CPAException, InvalidConfigurationException {

      super(config,
          logger,
          pCpa,
          pInterpolationManager,
          pPathChecker,
          pPrefixProvider,
          pPathFormulaManager,
          pStrategy,
          pSolver,
          pAssumesStore,
          pCfa);

    }

    @Override
    protected final List<BooleanFormula> getFormulasForPath(List<ARGState> pPath, ARGState initialState) throws CPATransferException, InterruptedException {
      // the elements in the path are not expanded, so they contain the path formulas
      // with the wrong indices
      // we need to re-create all path formulas in the flattened ARG

      ssaRenamingTimer.start();
      try {
        return computeBlockFormulas(initialState);

      } finally {
        ssaRenamingTimer.stop();
      }
    }

    private List<BooleanFormula> computeBlockFormulas(final ARGState pRoot) throws CPATransferException, InterruptedException {

      final Map<ARGState, ARGState> callStacks = new HashMap<>(); // contains states and their next higher callstate
      final Map<ARGState, PathFormula> finishedFormulas = new HashMap<>();
      final List<BooleanFormula> abstractionFormulas = new ArrayList<>();
      final Deque<ARGState> waitlist = new ArrayDeque<>();

      // initialize
      assert pRoot.getParents().isEmpty() : "rootState must be the first state of the program";
      callStacks.put(pRoot, null); // main-start has no callstack
      finishedFormulas.put(pRoot, pfmgr.makeEmptyPathFormula());
      waitlist.addAll(pRoot.getChildren());

      // iterate over all elements in the ARG with BFS
      while (!waitlist.isEmpty()) {
        final ARGState currentState = waitlist.pollFirst();
        if (finishedFormulas.containsKey(currentState)) {
          continue; // already handled
        }

        if (!finishedFormulas.keySet().containsAll(currentState.getParents())) {
          // parent not handled yet, re-queue current element and wait for all parents
          waitlist.addLast(currentState);
          continue;
        }

        // collect formulas for current location
        final List<PathFormula> currentFormulas = new ArrayList<>(currentState.getParents().size());
        final List<ARGState> currentStacks = new ArrayList<>(currentState.getParents().size());
        for (ARGState parentElement : currentState.getParents()) {
          PathFormula parentFormula = finishedFormulas.get(parentElement);
          final CFAEdge edge = parentElement.getEdgeToChild(currentState);
          assert edge != null: "ARG is invalid: parent has no edge to child";

          final ARGState prevCallState;
          // we enter a function, so lets add the previous state to the stack
          if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
            prevCallState = parentElement;

          } else if (edge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
            // we leave a function, so rebuild return-state before assigning the return-value.
            // rebuild states with info from previous state
            assert callStacks.containsKey(parentElement);
            final ARGState callState = callStacks.get(parentElement);

            assert extractLocation(callState).getLeavingSummaryEdge().getSuccessor() == extractLocation(currentState) :
                    "callstack does not match entry of current function-exit.";
            assert callState != null || currentState.getChildren().isEmpty() :
                    "returning from empty callstack is only possible at program-exit";

            prevCallState = callStacks.get(callState);
            parentFormula = rebuildStateAfterFunctionCall(parentFormula,
                finishedFormulas.get(callState), (FunctionExitNode)extractLocation(parentElement));

          } else {
            assert callStacks.containsKey(parentElement); // check for null is not enough
            prevCallState = callStacks.get(parentElement);
          }

          final PathFormula currentFormula = pfmgr.makeAnd(parentFormula, edge);
          currentFormulas.add(currentFormula);
          currentStacks.add(prevCallState);
        }

        assert currentFormulas.size() >= 1 : "each state except root must have parents";
        assert currentStacks.size() == currentFormulas.size() : "number of callstacks must match predecessors";

        // merging after functioncall with different callstates is ugly.
        // this is also guaranteed by the abstraction-locations at function-entries
        // (--> no merge of states with different latest abstractions).
        assert Sets.newHashSet(currentStacks).size() <= 1 : "function with multiple entry-states not supported";

        callStacks.put(currentState, currentStacks.get(0));

        PathFormula currentFormula;
        final PredicateAbstractState predicateElement = extractStateByType(currentState, PredicateAbstractState.class);
        if (predicateElement.isAbstractionState()) {
          // abstraction element is the start of a new part of the ARG

          assert waitlist.isEmpty() : "todo should be empty, because of the special ARG structure";
          assert currentState.getParents().size() == 1 : "there should be only one parent, because of the special ARG structure";

          // finishedFormulas.clear(); // free some memory
          // TODO disabled, we need to keep callStates for later usage

          // start new block with empty formula
          currentFormula = getOnlyElement(currentFormulas);
          abstractionFormulas.add(currentFormula.getFormula());
          currentFormula = pfmgr.makeEmptyPathFormula(currentFormula);

        } else {
          // merge the formulas
          Iterator<PathFormula> it = currentFormulas.iterator();
          currentFormula = it.next();
          while (it.hasNext()) {
            currentFormula = pfmgr.makeOr(currentFormula, it.next());
          }
        }

        assert !finishedFormulas.containsKey(currentState) : "a state should only be finished once";
        finishedFormulas.put(currentState, currentFormula);
        waitlist.addAll(currentState.getChildren());
      }
      return abstractionFormulas;
    }

    /* rebuild indices from outer scope */
    private PathFormula rebuildStateAfterFunctionCall(final PathFormula parentFormula, final PathFormula rootFormula,
        final FunctionExitNode functionExitNode) {
      final SSAMap newSSA = BAMPredicateReducer.updateIndices(rootFormula.getSsa(), parentFormula.getSsa(), functionExitNode);
      return pfmgr.makeNewPathFormula(parentFormula, newSSA);
    }

    @Override
    public void collectStatistics(Collection<Statistics> pStatsCollection) {
      pStatsCollection.add(new Stats() {
        @Override
        public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
          super.printStatistics(out, result, reached);
          out.println("Time for SSA renaming:                " + ssaRenamingTimer);
        }
      });
    }
  }

  /**
   * This is an extension of {@link PredicateAbstractionRefinementStrategy}
   * that takes care of updating the BAM state.
   */
  private static class BAMPredicateAbstractionRefinementStrategy extends PredicateAbstractionRefinementStrategy {

    private final RefineableRelevantPredicatesComputer relevantPredicatesComputer;
    private final BAMPredicateCPA predicateCpa;

    private List<Region> lastAbstractions = null;
    private boolean refinedLastRelevantPredicatesComputer = false;

    private BAMPredicateAbstractionRefinementStrategy(final Configuration config, final LogManager logger,
        final BAMPredicateCPA predicateCpa,
        final Solver pSolver,
        final PredicateAbstractionManager pPredAbsMgr,
        final PredicateStaticRefiner pStaticRefiner)
            throws CPAException, InvalidConfigurationException {

      super(config, logger, predicateCpa.getShutdownNotifier(), pPredAbsMgr, pStaticRefiner, pSolver);

      RelevantPredicatesComputer relevantPredicatesComputer = predicateCpa.getRelevantPredicatesComputer();
      if (relevantPredicatesComputer instanceof RefineableRelevantPredicatesComputer) {
        this.relevantPredicatesComputer = (RefineableRelevantPredicatesComputer)relevantPredicatesComputer;
      } else {
        this.relevantPredicatesComputer = null;
      }

      this.predicateCpa = predicateCpa;
    }

    private static final Function<PredicateAbstractState, Region> GET_REGION
    = new Function<PredicateAbstractState, Region>() {
        @Override
        public Region apply(PredicateAbstractState e) {
          assert e.isAbstractionState();
          return e.getAbstractionFormula().asRegion();
        }
      };

    private List<Region> getRegionsForPath(List<ARGState> path) {
      return from(path)
              .transform(toState(PredicateAbstractState.class))
              .transform(GET_REGION)
              .toList();
    }

    @Override
    public void performRefinement(
        ARGReachedSet pReached,
        List<ARGState> pPath,
        List<BooleanFormula> pInterpolants,
        boolean pRepeatedCounterexample) throws CPAException, InterruptedException {

      // overriding this method is needed, as, in principle, it is possible to get two successive spurious counterexamples
      // which only differ in its abstractions (with 'aggressive caching').

      boolean refinedRelevantPredicatesComputer = false;

      if (pRepeatedCounterexample) {
        //block formulas are the same as last time; check if abstractions also agree
        pRepeatedCounterexample = getRegionsForPath(pPath).equals(lastAbstractions);

        if (pRepeatedCounterexample && !refinedLastRelevantPredicatesComputer && relevantPredicatesComputer != null) {
          //even abstractions agree; try refining relevant predicates reducer
          refineRelevantPredicatesComputer(pPath, pReached);
          pRepeatedCounterexample = false;
          refinedRelevantPredicatesComputer = true;
        }
      }

      lastAbstractions = getRegionsForPath(pPath);
      refinedLastRelevantPredicatesComputer = refinedRelevantPredicatesComputer;
      super.performRefinement(pReached, pPath, pInterpolants, pRepeatedCounterexample);
    }

    private void refineRelevantPredicatesComputer(List<ARGState> pPath, ARGReachedSet pReached) {
      UnmodifiableReachedSet reached = pReached.asReachedSet();
      Precision oldPrecision = reached.getPrecision(reached.getLastState());
      PredicatePrecision oldPredicatePrecision = Precisions.extractPrecisionByType(oldPrecision, PredicatePrecision.class);

      BlockPartitioning partitioning = predicateCpa.getPartitioning();
      Deque<Block> openBlocks = new ArrayDeque<>();
      openBlocks.push(partitioning.getMainBlock());
      for (ARGState pathElement : pPath) {
        CFANode currentNode = AbstractStates.extractLocation(pathElement);
        Integer currentNodeInstance = getPredicateState(pathElement)
                                      .getAbstractionLocationsOnPath().get(currentNode);
        if (partitioning.isCallNode(currentNode)) {
          openBlocks.push(partitioning.getBlockForCallNode(currentNode));
        }

        Collection<AbstractionPredicate> localPreds = oldPredicatePrecision.getPredicates(currentNode, currentNodeInstance);
        for (Block block : openBlocks) {
          for (AbstractionPredicate pred : localPreds) {
            relevantPredicatesComputer.considerPredicateAsRelevant(block, pred);
          }
        }

        while (openBlocks.peek().isReturnNode(currentNode)) {
          openBlocks.pop();
        }
      }
    }

    @Override
    protected void analyzePathPrecisions(ARGReachedSet argReached, List<ARGState> path) {
      // Not implemented for BAM (different sets of reached states have to be handled)
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    refiner.collectStatistics(pStatsCollection);
  }
}
