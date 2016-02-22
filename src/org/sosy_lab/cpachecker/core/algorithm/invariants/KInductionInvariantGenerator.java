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
package org.sosy_lab.cpachecker.core.algorithm.invariants;

import static com.google.common.base.Preconditions.*;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.sosy_lab.common.Classes.UnexpectedCheckedException;
import org.sosy_lab.common.LazyFutureTask;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.concurrency.Threads;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.bmc.BMCAlgorithmForInvariantGeneration;
import org.sosy_lab.cpachecker.core.algorithm.bmc.BMCStatistics;
import org.sosy_lab.cpachecker.core.algorithm.bmc.CandidateGenerator;
import org.sosy_lab.cpachecker.core.algorithm.bmc.CandidateInvariant;
import org.sosy_lab.cpachecker.core.algorithm.bmc.EdgeFormulaNegation;
import org.sosy_lab.cpachecker.core.algorithm.bmc.ExpressionTreeLocationInvariant;
import org.sosy_lab.cpachecker.core.algorithm.bmc.StaticCandidateProvider;
import org.sosy_lab.cpachecker.core.algorithm.bmc.TargetLocationCandidateInvariant;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.automaton.TargetLocationProvider;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTree;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTrees;
import org.sosy_lab.solver.SolverException;

import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Generate invariants using k-induction.
 */
public class KInductionInvariantGenerator extends AbstractInvariantGenerator implements StatisticsProvider {

  @Options(prefix="invariantGeneration.kInduction")
  private static class KInductionInvariantGeneratorOptions {

    @FileOption(Type.OPTIONAL_INPUT_FILE)
    @Option(
      secure = true,
      description =
          "Provides additional candidate invariants to the k-induction invariant generator."
    )
    private Path invariantsAutomatonFile = null;

    @Option(secure = true, description = "Guess some candidates for the k-induction invariant generator from the CFA.")
    private boolean guessCandidatesFromCFA = true;

    @Option(secure = true, description = "For correctness-witness validation: Shut down if a candidate invariant is found to be incorrect.")
    private boolean terminateOnCounterexample = false;

    @Option(
      secure = true,
      description = "Check candidate invariants in a separate thread asynchronously."
    )
    private boolean async = true;
  }

  private static class KInductionInvariantGeneratorStatistics extends BMCStatistics {

    final Timer invariantGeneration = new Timer();

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
      out.println("Time for invariant generation:   " + invariantGeneration);
      super.printStatistics(out, result, reached);
    }

    @Override
    public String getName() {
      return "k-Induction-based invariant generator";
    }
  }

  private final KInductionInvariantGeneratorStatistics stats = new KInductionInvariantGeneratorStatistics();

  private final BMCAlgorithmForInvariantGeneration algorithm;
  private final ConfigurableProgramAnalysis cpa;
  private final ReachedSetFactory reachedSetFactory;

  private final LogManager logger;
  private final ShutdownManager shutdownManager;

  private final boolean async;

  // After start(), this will hold a Future for the final result of the invariant generation.
  // We use a Future instead of just the atomic reference below
  // to be able to ask for termination and see thrown exceptions.
  private Future<InvariantSupplier> invariantGenerationFuture = null;

  private final ShutdownRequestListener shutdownListener = new ShutdownRequestListener() {

    @Override
    public void shutdownRequested(String pReason) {
      invariantGenerationFuture.cancel(true);
    }
  };

  public static KInductionInvariantGenerator create(final Configuration pConfig,
      final LogManager pLogger, final ShutdownManager pShutdownManager,
      final CFA pCFA, final ReachedSetFactory pReachedSetFactory, TargetLocationProvider pTargetLocationProvider)
          throws InvalidConfigurationException, CPAException {

    KInductionInvariantGeneratorOptions options = new KInductionInvariantGeneratorOptions();
    pConfig.inject(options);

    return new KInductionInvariantGenerator(
        pConfig,
        pLogger.withComponentName("KInductionInvariantGenerator"),
        pShutdownManager,
        pCFA,
        pReachedSetFactory,
        options.async,
        getCandidateInvariants(
            options,
            pConfig,
            pLogger,
            pCFA,
            pShutdownManager,
            pReachedSetFactory,
            pTargetLocationProvider));
  }

  public static KInductionInvariantGenerator create(final Configuration pConfig,
      final LogManager pLogger, final ShutdownManager pShutdownManager,
      final CFA pCFA, final ReachedSetFactory pReachedSetFactory, CandidateGenerator candidateGenerator, boolean pAsync)
          throws InvalidConfigurationException, CPAException {

    return new KInductionInvariantGenerator(
            pConfig,
            pLogger.withComponentName("KInductionInvariantGenerator"),
            pShutdownManager,
            pCFA,
            pReachedSetFactory,
            pAsync,
            candidateGenerator);
  }

  private KInductionInvariantGenerator(final Configuration config, final LogManager pLogger,
      final ShutdownManager pShutdownNotifier, final CFA cfa,
      final ReachedSetFactory pReachedSetFactory, final boolean pAsync,
      final CandidateGenerator pCandidateGenerator)
          throws InvalidConfigurationException, CPAException {
    logger = pLogger;
    shutdownManager = pShutdownNotifier;

    reachedSetFactory = pReachedSetFactory;
    async = pAsync;

    CPABuilder invGenBMCBuilder =
        new CPABuilder(config, logger, shutdownManager.getNotifier(), pReachedSetFactory);
    cpa = invGenBMCBuilder.buildCPAWithSpecAutomatas(cfa);
    Algorithm cpaAlgorithm = CPAAlgorithm.create(cpa, logger, config, shutdownManager.getNotifier());
    algorithm = new BMCAlgorithmForInvariantGeneration(
        cpaAlgorithm, cpa, config, logger, pReachedSetFactory,
        shutdownManager, cfa, stats, pCandidateGenerator);

    PredicateCPA predicateCPA = CPAs.retrieveCPA(cpa, PredicateCPA.class);
    if (predicateCPA == null) {
      throw new InvalidConfigurationException("Predicate CPA required");
    }
    if (async && !predicateCPA.getSolver().getVersion().toLowerCase().contains("smtinterpol")) {
      throw new InvalidConfigurationException("Solver does not support concurrent execution, use SMTInterpol instead.");
    }
  }

  @Override
  public void start(final CFANode initialLocation) {
    checkState(invariantGenerationFuture == null);

    Callable<InvariantSupplier> task = new InvariantGenerationTask(initialLocation);

    if (async) {
      // start invariant generation asynchronously
      ExecutorService executor = Executors.newSingleThreadExecutor(Threads.threadFactory());
      invariantGenerationFuture = executor.submit(task);
      executor.shutdown(); // will shutdown after task is finished

    } else {
      // create future for lazy synchronous invariant generation
      invariantGenerationFuture = new LazyFutureTask<>(task);
    }

    shutdownManager.getNotifier().registerAndCheckImmediately(shutdownListener);
  }

  private final AtomicBoolean cancelled = new AtomicBoolean();

  @Override
  public void cancel() {
    checkState(invariantGenerationFuture != null);
    shutdownManager.requestShutdown("Invariant generation cancel requested.");
    cancelled.set(true);
  }

  @Override
  public InvariantSupplier get() throws CPAException, InterruptedException {
    checkState(invariantGenerationFuture != null);

    if (async && (!invariantGenerationFuture.isDone()) || cancelled.get()) {
      // grab intermediate result that is available so far
      return algorithm.getCurrentInvariants();

    } else {
      try {
        return invariantGenerationFuture.get();
      } catch (ExecutionException e) {
        Throwables.propagateIfPossible(e.getCause(), CPAException.class, InterruptedException.class);
        throw new UnexpectedCheckedException("invariant generation", e.getCause());
      } catch (CancellationException e) {
        shutdownManager.getNotifier().shutdownIfNecessary();
        throw e;
      }
    }
  }

  @Override
  public ExpressionTreeSupplier getAsExpressionTree() throws CPAException, InterruptedException {
    get();
    return algorithm.getCurrentInvariantsAsExpressionTree();
  }

  @Override
  public boolean isProgramSafe() {
    return algorithm.isProgramSafe();
  }

  @Override
  public void injectInvariant(CFANode pLocation, AssumeEdge pAssumption) {
    // ignore for now (never called anyway)
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    algorithm.collectStatistics(pStatsCollection);
    pStatsCollection.add(stats);
  }

  private class InvariantGenerationTask implements Callable<InvariantSupplier> {

    private final CFANode initialLocation;

    private InvariantGenerationTask(final CFANode pInitialLocation) {
      initialLocation = checkNotNull(pInitialLocation);
    }

    @Override
    public InvariantSupplier call() throws InterruptedException, CPAException {
      stats.invariantGeneration.start();
      shutdownManager.getNotifier().shutdownIfNecessary();

      try {
        ReachedSet reachedSet = reachedSetFactory.create();
        AbstractState initialState = cpa.getInitialState(initialLocation, StateSpacePartition.getDefaultPartition());
        Precision initialPrecision = cpa.getInitialPrecision(initialLocation, StateSpacePartition.getDefaultPartition());
        reachedSet.add(initialState, initialPrecision);
        algorithm.run(reachedSet);
        return algorithm.getCurrentInvariants();

      } catch (SolverException e) {
        throw new CPAException("Solver Failure", e);
      } finally {
        stats.invariantGeneration.stop();
        CPAs.closeCpaIfPossible(cpa, logger);
        CPAs.closeIfPossible(algorithm, logger);
      }
    }
  }

  public static CandidateGenerator getCandidateInvariants(
      KInductionInvariantGeneratorOptions pOptions,
      Configuration pConfig,
      LogManager pLogger,
      CFA pCFA,
      final ShutdownManager pShutdownManager,
      ReachedSetFactory pReachedSetFactory,
      TargetLocationProvider pTargetLocationProvider)
      throws InvalidConfigurationException, CPAException {

    final Set<CandidateInvariant> candidates = Sets.newLinkedHashSet();

    if (pOptions.guessCandidatesFromCFA) {
      for (AssumeEdge assumeEdge : getRelevantAssumeEdges(pTargetLocationProvider.tryGetAutomatonTargetLocations(pCFA.getMainFunction()))) {
        candidates.add(new EdgeFormulaNegation(pCFA.getLoopStructure().get().getAllLoopHeads(), assumeEdge));
      }
    }

    final Multimap<String, CFANode> candidateGroupLocations = HashMultimap.create();
    if (pOptions.invariantsAutomatonFile != null) {
      ReachedSet reachedSet =
          analyzeWitness(pConfig, pLogger, pCFA, pShutdownManager, pReachedSetFactory, pOptions);
      extractCandidatesFromReachedSet(pShutdownManager, candidates, candidateGroupLocations,
          reachedSet);
    }

    final TargetLocationCandidateInvariant safetyProperty = new TargetLocationCandidateInvariant(pCFA.getAllLoopHeads().get());
    if (pCFA.getAllLoopHeads().isPresent()) {
      candidates.add(safetyProperty);
    }

    if (pOptions.terminateOnCounterexample) {
      return new StaticCandidateProvider(candidates) {

        private boolean safetyPropertyConfirmed = false;

        @Override
        public Iterator<CandidateInvariant> iterator() {
          final Iterator<CandidateInvariant> iterator = super.iterator();
          return new Iterator<CandidateInvariant>() {

            private CandidateInvariant candidate;

            @Override
            public boolean hasNext() {
              return !safetyPropertyConfirmed && iterator.hasNext();
            }

            @Override
            public CandidateInvariant next() {
              if (safetyPropertyConfirmed) {
                throw new NoSuchElementException("No more candidates available: The safety property has already been confirmed.");
              }
              return candidate = iterator.next();
            }

            @Override
            public void remove() {
              if (candidate instanceof ExpressionTreeLocationInvariant) {
                ExpressionTreeLocationInvariant expressionTreeLocationInvariant =
                    (ExpressionTreeLocationInvariant) candidate;

                // Remove the location from the group
                String groupId = expressionTreeLocationInvariant.getGroupId();
                Collection<CFANode> remainingLocations = candidateGroupLocations.get(groupId);
                remainingLocations.removeAll(expressionTreeLocationInvariant.getLocations());

                // If no location remains, the invariant has been disproved at all possible locations
                if (remainingLocations.isEmpty()) {
                  pShutdownManager.requestShutdown("Incorrect invariant: " + candidate.toString());
                }
              }
              iterator.remove();
            }
          };
        }

        @Override
        public void confirmCandidates(Iterable<CandidateInvariant> pCandidates) {
          super.confirmCandidates(pCandidates);
          if (Iterables.contains(pCandidates, safetyProperty)) {
            safetyPropertyConfirmed = true;
          }
        }
      };
    }
    return new StaticCandidateProvider(candidates);
  }

  private static ReachedSet analyzeWitness(Configuration pConfig, LogManager pLogger, CFA pCFA,
      final ShutdownManager pShutdownManager, ReachedSetFactory pReachedSetFactory,
      KInductionInvariantGeneratorOptions options) throws InvalidConfigurationException,
      CPAException {
    ConfigurationBuilder configBuilder = Configuration.builder();
    String machineModelOption = "analysis.machineModel";
    if (pConfig.hasProperty(machineModelOption)) {
      configBuilder.copyOptionFrom(pConfig, machineModelOption);
    }
    configBuilder.setOption("cpa", "cpa.arg.ARGCPA");
    configBuilder.setOption("ARGCPA.cpa", "cpa.composite.CompositeCPA");
    configBuilder.setOption(
        "CompositeCPA.cpas",
        "cpa.location.LocationCPA, "
            + "cpa.callstack.CallstackCPA, "
            + "cpa.functionpointer.FunctionPointerCPA");
    Configuration config = configBuilder.build();
    ShutdownNotifier notifier = pShutdownManager.getNotifier();
    ReachedSet reachedSet = pReachedSetFactory.create();
    CPABuilder builder = new CPABuilder(config, pLogger, notifier, pReachedSetFactory);
    ConfigurableProgramAnalysis cpa =
        builder.buildCPAs(pCFA, Arrays.asList(options.invariantsAutomatonFile));
    CPAAlgorithm algorithm = CPAAlgorithm.create(cpa, pLogger, config, notifier);
    CFANode rootNode = pCFA.getMainFunction();
    StateSpacePartition partition = StateSpacePartition.getDefaultPartition();
    reachedSet.add(
        cpa.getInitialState(rootNode, partition),
        cpa.getInitialPrecision(rootNode, partition));
    try {
      algorithm.run(reachedSet);
    } catch (InterruptedException e) {
      // Candidate collection was interrupted,
      // but instead of throwing the exception here,
      // let it be thrown by the invariant generator.
    }
    return reachedSet;
  }

  private static void extractCandidatesFromReachedSet(final ShutdownManager pShutdownManager,
      final Set<CandidateInvariant> candidates,
      final Multimap<String, CFANode> candidateGroupLocations, ReachedSet reachedSet) {
    Set<CFANode> visited = Sets.newHashSet();
    Multimap<CFANode, ExpressionTreeLocationInvariant> potentialAdditionalCandidates =
        HashMultimap.create();
    for (AbstractState abstractState : reachedSet) {
      if (pShutdownManager.getNotifier().shouldShutdown()) {
        return;
      }
      Iterable<CFANode> locations = AbstractStates.extractLocations(abstractState);
      Iterables.addAll(visited, locations);
      for (AutomatonState automatonState :
          AbstractStates.asIterable(abstractState).filter(AutomatonState.class)) {
        ExpressionTree<AExpression> candidate = automatonState.getCandidateInvariants();
        if (!candidate.equals(ExpressionTrees.getTrue())) {
          String groupId = automatonState.getInternalStateName();
          candidateGroupLocations.putAll(groupId, locations);
          for (CFANode location : locations) {
            potentialAdditionalCandidates.removeAll(location);
            CandidateInvariant candidateInvariant =
                new ExpressionTreeLocationInvariant(groupId, location, candidate);
            candidates.add(candidateInvariant);
            // Check if there are any leaving return edges:
            // The predecessors are also potential matches for the invariant
            for (FunctionReturnEdge returnEdge :
                CFAUtils.leavingEdges(location).filter(FunctionReturnEdge.class)) {
              CFANode successor = returnEdge.getSuccessor();
              if (!candidateGroupLocations.containsEntry(groupId, successor)
                  && !visited.contains(successor)) {
                potentialAdditionalCandidates.put(
                    successor,
                    new ExpressionTreeLocationInvariant(groupId, successor, candidate));
              }
            }
          }
        }
      }
    }
    for (Map.Entry<CFANode, Collection<ExpressionTreeLocationInvariant>> potentialCandidates :
        potentialAdditionalCandidates.asMap().entrySet()) {
      if (!visited.contains(potentialCandidates.getKey())) {
        for (ExpressionTreeLocationInvariant candidateInvariant :
            potentialCandidates.getValue()) {
          candidateGroupLocations.put(
              candidateInvariant.getGroupId(), potentialCandidates.getKey());
          candidates.add(candidateInvariant);
        }
      }
    }
  }

  /**
   * Gets the relevant assume edges.
   *
   * @param pTargetLocations the predetermined target locations.
   *
   * @return the relevant assume edges.
   */
  private static Set<AssumeEdge> getRelevantAssumeEdges(Collection<CFANode> pTargetLocations) {
    final Set<AssumeEdge> assumeEdges = Sets.newLinkedHashSet();
    Set<CFANode> visited = Sets.newHashSet(pTargetLocations);
    Queue<CFANode> waitlist = new ArrayDeque<>(pTargetLocations);
    while (!waitlist.isEmpty()) {
      CFANode current = waitlist.poll();
      for (CFAEdge enteringEdge : CFAUtils.enteringEdges(current)) {
        CFANode predecessor = enteringEdge.getPredecessor();
        if (enteringEdge.getEdgeType() == CFAEdgeType.AssumeEdge) {
          assumeEdges.add((AssumeEdge)enteringEdge);
        } else if (visited.add(predecessor)) {
          waitlist.add(predecessor);
        }
      }
    }
    return assumeEdges;
  }
}
