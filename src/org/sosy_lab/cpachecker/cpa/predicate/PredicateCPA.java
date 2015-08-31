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

import java.util.Collection;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.algorithm.invariants.CPAInvariantGenerator;
import org.sosy_lab.cpachecker.core.algorithm.invariants.DoNothingInvariantGenerator;
import org.sosy_lab.cpachecker.core.algorithm.invariants.InvariantGenerator;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.blocking.BlockedCFAReducer;
import org.sosy_lab.cpachecker.util.blocking.interfaces.BlockComputer;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.SymbolicRegionManager;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.CachingPathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.solver.SolverException;

/**
 * CPA that defines symbolic predicate abstraction.
 */
@Options(prefix="cpa.predicate")
public class PredicateCPA implements ConfigurableProgramAnalysis, StatisticsProvider, ProofChecker, AutoCloseable {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PredicateCPA.class).withOptions(BlockOperator.class);
  }

  @Option(secure=true, name="abstraction.type", toUppercase=true, values={"BDD", "SYLVAN", "FORMULA"},
      description="What to use for storing abstractions")
  private String abstractionType = "BDD";

  @Option(secure=true, name="blk.useCache", description="use caching of path formulas")
  private boolean useCache = true;

  @Option(secure=true, name="enableBlockreducer", description="Enable the possibility to precompute explicit abstraction locations.")
  private boolean enableBlockreducer = false;

  @Option(secure=true, name="merge", values={"SEP", "ABE"}, toUppercase=true,
      description="which merge operator to use for predicate cpa (usually ABE should be used)")
  private String mergeType = "ABE";

  @Option(secure=true, name="stop", values={"SEP", "SEPPCC"}, toUppercase=true,
      description="which stop operator to use for predicate cpa (usually SEP should be used in analysis)")
  private String stopType = "SEP";

  @Option(secure=true, name="refinement.performInitialStaticRefinement",
      description="use heuristic to extract predicates from the CFA statically on first refinement")
  private boolean performInitialStaticRefinement = false;

  @Option(secure=true, description="Generate invariants and strengthen the formulas during abstraction with them.")
  private boolean useInvariantsForAbstraction = false;

  @Option(secure=true, description="Direction of the analysis?")
  private AnalysisDirection direction = AnalysisDirection.FORWARD;

  protected final Configuration config;
  protected final LogManager logger;
  protected final ShutdownNotifier shutdownNotifier;

  private final PredicateAbstractDomain domain;
  private final PredicateTransferRelation transfer;
  private final MergeOperator merge;
  private final PredicatePrecisionAdjustment prec;
  private final StopOperator stop;
  private final PredicatePrecision initialPrecision;
  private final PathFormulaManager pathFormulaManager;
  private final Solver solver;
  private final PredicateAbstractionManager predicateManager;
  private final PredicateCPAStatistics stats;
  private final PredicateAbstractState topState;
  private final PredicatePrecisionBootstrapper precisionBootstraper;
  private final PredicateStaticRefiner staticRefiner;
  private final CFA cfa;
  private final PredicateAssumeStore assumesStore;
  private final AbstractionManager abstractionManager;
  private final InvariantGenerator invariantGenerator;

  protected PredicateCPA(Configuration config, LogManager logger,
      BlockOperator blk, CFA pCfa, ReachedSetFactory reachedSetFactory,
      ShutdownNotifier pShutdownNotifier)
          throws InvalidConfigurationException, CPAException {
    config.inject(this, PredicateCPA.class);

    this.config = config;
    this.logger = logger;
    this.shutdownNotifier = pShutdownNotifier;

    cfa = pCfa;

    if (enableBlockreducer) {
      BlockComputer blockComputer = new BlockedCFAReducer(config, logger);
      blk.setExplicitAbstractionNodes(blockComputer.computeAbstractionNodes(cfa));
    }
    blk.setCFA(cfa);

    solver = Solver.create(config, logger, pShutdownNotifier);
    FormulaManagerView formulaManager = solver.getFormulaManager();
    String libraries = formulaManager.getVersion();

    PathFormulaManager pfMgr = new PathFormulaManagerImpl(formulaManager, config, logger, shutdownNotifier, cfa, direction);
    if (useCache) {
      pfMgr = new CachingPathFormulaManager(pfMgr);
    }
    pathFormulaManager = pfMgr;

    RegionManager regionManager;
    if (abstractionType.equals("FORMULA") || blk.alwaysReturnsFalse()) {
      // No need to load BDD library if we never abstract (might use lots of memory)
      regionManager = new SymbolicRegionManager(formulaManager, solver);
    } else {
      assert abstractionType.equals("BDD");
      regionManager = new BDDManagerFactory(config, logger).createRegionManager();
      libraries += " and " + regionManager.getVersion();
    }
    logger.log(Level.INFO, "Using predicate analysis with", libraries + ".");

    abstractionManager = new AbstractionManager(regionManager, formulaManager, config, logger, solver);

    assumesStore = new PredicateAssumeStore(formulaManager);

    predicateManager = new PredicateAbstractionManager(
        abstractionManager, formulaManager, pathFormulaManager,
        solver, config, logger, pShutdownNotifier, cfa.getLiveVariables());

    transfer = new PredicateTransferRelation(this, blk, config, direction, cfa);

    topState = PredicateAbstractState.mkAbstractionState(
        pathFormulaManager.makeEmptyPathFormula(),
        predicateManager.makeTrueAbstractionFormula(null),
        PathCopyingPersistentTreeMap.<CFANode, Integer>of());
    domain = new PredicateAbstractDomain(this, config);

    if (mergeType.equals("SEP")) {
      merge = MergeSepOperator.getInstance();
    } else if (mergeType.equals("ABE")) {
      merge = new PredicateMergeOperator(this);
    } else {
      throw new InternalError("Update list of allowed merge operators");
    }

    if (useInvariantsForAbstraction) {
      invariantGenerator = CPAInvariantGenerator.create(config, logger, pShutdownNotifier, cfa);
    } else {
      invariantGenerator = new DoNothingInvariantGenerator();
    }

    if (performInitialStaticRefinement) {
      staticRefiner = new PredicateStaticRefiner(config, logger, solver,
          pathFormulaManager, formulaManager, predicateManager, cfa);
    } else {
      staticRefiner = null;
    }

    precisionBootstraper = new PredicatePrecisionBootstrapper(config, logger, cfa, pathFormulaManager, abstractionManager, formulaManager);
    initialPrecision = precisionBootstraper.prepareInitialPredicates();
    logger.log(Level.FINEST, "Initial precision is", initialPrecision);

    stats = new PredicateCPAStatistics(this, blk, regionManager, abstractionManager,
        cfa, config);

    prec = new PredicatePrecisionAdjustment(this, invariantGenerator);

    if (stopType.equals("SEP")) {
      stop = new PredicateStopOperator(domain);
    } else if (stopType.equals("SEPPCC")) {
      stop = new PredicatePCCStopOperator(this);
    } else {
      throw new InternalError("Update list of allowed stop operators");
    }
  }

  public PredicateAssumeStore getAssumesStore() {
    return assumesStore;
  }

  @Override
  public PredicateAbstractDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public PredicateTransferRelation getTransferRelation() {
    return transfer;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return merge;
  }

  @Override
  public StopOperator getStopOperator() {
    return stop;
  }

  public PredicateAbstractionManager getPredicateManager() {
    return predicateManager;
  }

  public PathFormulaManager getPathFormulaManager() {
    return pathFormulaManager;
  }

  public Solver getSolver() {
    return solver;
  }

  Configuration getConfiguration() {
    return config;
  }

  LogManager getLogger() {
    return logger;
  }

  public ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  @Nullable
  public PredicateStaticRefiner getStaticRefiner() {
    return staticRefiner;
  }

  @Override
  public PredicateAbstractState getInitialState(CFANode node, StateSpacePartition pPartition) {
    prec.setInitialLocation(node);
    return topState;
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition pPartition) {
    return initialPrecision;
  }

  @Override
  public PredicatePrecisionAdjustment getPrecisionAdjustment() {
    return prec;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    precisionBootstraper.collectStatistics(pStatsCollection);
    if (invariantGenerator instanceof StatisticsProvider) {
      ((StatisticsProvider)invariantGenerator).collectStatistics(pStatsCollection);
    }
    solver.getFormulaManager().collectStatistics(pStatsCollection);
  }

  @Override
  public void close() throws Exception {
    solver.close();
  }

  @Override
  public boolean areAbstractSuccessors(AbstractState pElement, CFAEdge pCfaEdge, Collection<? extends AbstractState> pSuccessors) throws CPATransferException, InterruptedException {
    try {
      return getTransferRelation().areAbstractSuccessors(pElement, pCfaEdge, pSuccessors);
    } catch (SolverException e) {
      throw new CPATransferException("Solver failed during abstract-successor check", e);
    }
  }

  @Override
  public boolean isCoveredBy(AbstractState pElement, AbstractState pOtherElement) throws CPAException, InterruptedException {
    // isLessOrEqual for proof checking; formula based; elements can be trusted (i.e., invariants do not have to be checked)

    PredicateAbstractState e1 = (PredicateAbstractState) pElement;
    PredicateAbstractState e2 = (PredicateAbstractState) pOtherElement;

    if (e1.isAbstractionState() && e2.isAbstractionState()) {
      try {
        return predicateManager.checkCoverage(
            e1.getAbstractionFormula(),
            pathFormulaManager.makeEmptyPathFormula(e1.getPathFormula()),
            e2.getAbstractionFormula()
        );
      } catch (SolverException e) {
        throw new CPAException("Solver Failure", e);
      }
    } else {
      return false;
    }
  }

  public CFA getCfa() {
    return cfa;
  }

  public MachineModel getMachineModel() {
    return cfa.getMachineModel();
  }

  public AbstractionManager getAbstractionManager() {
    return abstractionManager;
  }
}
