
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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.mkNonAbstractionStateWithNewPathFormula;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpressionCollectorVisitor;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.c.CProblemType;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithAssumptions;
import org.sosy_lab.cpachecker.core.interfaces.FormulaReportingState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.argReplay.ARGReplayState;
import org.sosy_lab.cpachecker.cpa.assumptions.storage.AssumptionStorageState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState.ComputeAbstractionState;
import org.sosy_lab.cpachecker.cpa.predicate.persistence.PredicateAbstractionsStorage;
import org.sosy_lab.cpachecker.cpa.predicate.persistence.PredicateAbstractionsStorage.AbstractionNode;
import org.sosy_lab.cpachecker.cpa.predicate.persistence.PredicatePersistenceUtils.PredicateParsingFailedException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.precisionConverter.Converter;
import org.sosy_lab.cpachecker.util.predicates.precisionConverter.Converter.PrecisionConverter;
import org.sosy_lab.cpachecker.util.predicates.precisionConverter.FormulaParser;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.solver.SolverException;
import org.sosy_lab.solver.api.BooleanFormula;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * Transfer relation for symbolic predicate abstraction. First it computes
 * the strongest post for the given CFA edge. Afterwards it optionally
 * computes an abstraction.
 */
@Options(prefix = "cpa.predicate")
public class PredicateTransferRelation extends SingleEdgeTransferRelation {

  @Option(secure=true, name = "satCheck",
      description = "maximum blocksize before a satisfiability check is done\n"
          + "(non-negative number, 0 means never, if positive should be smaller than blocksize)")
  private int satCheckBlockSize = 0;

  @Option(secure = true,
      description = "Enables sat checks at abstraction location.\n"
          + "Infeasible paths are already excluded by transfer relation and not later by precision adjustment. This property is required in proof checking.")
  private boolean satCheckAtAbstraction = false;

  @Option(secure=true, description = "check satisfiability when a target state has been found (should be true)")
  private boolean targetStateSatCheck = true;

  @Option(secure=true, description = "do not include assumptions of states into path formula during strengthening")
  private boolean ignoreStateAssumptions = false;

  @Option(secure=true, description = "try to reuse old abstractions from file during strengthening")
  private boolean strengthenWithReusedAbstractions = false;

  @Option(secure = true, description = "Use formula reporting states for strengthening.")
  private boolean strengthenWithFormulaReportingStates = false;

  @Option(description="file that consists of old abstractions, to be used during strengthening")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private Path strengthenWithReusedAbstractionsFile = Paths.get("abstractions.txt");

  // statistics
  final Timer postTimer = new Timer();
  final Timer satCheckTimer = new Timer();
  final Timer pathFormulaTimer = new Timer();
  final Timer strengthenTimer = new Timer();
  final Timer strengthenCheckTimer = new Timer();
  final Timer strengthenReuseReadTimer = new Timer();
  final Timer strengthenReuseConvertTimer = new Timer();
  final Timer abstractionCheckTimer = new Timer();

  int numSatChecksFalse = 0;
  int numStrengthenChecksFalse = 0;

  private final LogManager logger;
  private final PredicateAbstractionManager formulaManager;
  private final PathFormulaManager pathFormulaManager;

  private final BlockOperator blk;

  private final Map<PredicateAbstractState, PathFormula> computedPathFormulae = new HashMap<>();

  private final FormulaManagerView fmgr;
  private final BooleanFormulaManagerView bfmgr;

  private final AnalysisDirection direction;
  private final CFA cfa;

  public PredicateTransferRelation(
      Configuration config,
      LogManager pLogger,
      AnalysisDirection pDirection,
      CFA pCfa,
      FormulaManagerView pFmgr,
      PathFormulaManager pPfmgr,
      BlockOperator pBlk,
      PredicateAbstractionManager pPredAbsManager)
      throws InvalidConfigurationException {
    config.inject(this, PredicateTransferRelation.class);

    logger = pLogger;
    formulaManager = pPredAbsManager;
    pathFormulaManager = pPfmgr;
    fmgr = pFmgr;
    bfmgr = fmgr.getBooleanFormulaManager();
    blk = pBlk;
    direction = pDirection;
    cfa = pCfa;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pElement, Precision pPrecision, CFAEdge edge)
          throws CPATransferException, InterruptedException {

    postTimer.start();
    try {

      PredicateAbstractState element = (PredicateAbstractState) pElement;
      CFANode loc = getAnalysisSuccesor(edge);
      CFANode predloc = getAnalysisPredecessor(edge);

      // Check whether abstraction is false.
      // Such elements might get created when precision adjustment computes an abstraction.
      if (element.getAbstractionFormula().isFalse()) { return Collections.emptySet(); }

      // calculate strongest post
      PathFormula pathFormula = convertEdgeToPathFormula(element.getPathFormula(), edge);
      logger.log(Level.ALL, "New path formula is", pathFormula);

      // check whether to do abstraction
      boolean doAbstraction = blk.isBlockEnd(loc, predloc, edge, pathFormula);

      return createState(element, pathFormula, doAbstraction);

    } catch (SolverException e) {
      throw new CPATransferException("Solver failed during successor generation", e);

    } finally {
      postTimer.stop();
    }
  }

  private CFANode getAnalysisSuccesor(CFAEdge pEdge) {
    if (direction == AnalysisDirection.BACKWARD) {
      return pEdge.getPredecessor();
    } else {
      return pEdge.getSuccessor();
    }
  }

  private CFANode getAnalysisPredecessor(CFAEdge pEdge) {
    if (direction == AnalysisDirection.BACKWARD) {
      return pEdge.getSuccessor();
    } else {
      return pEdge.getPredecessor();
    }
  }

  private Collection<? extends PredicateAbstractState> createState(
      PredicateAbstractState oldState, PathFormula pathFormula, boolean doAbstraction)
      throws SolverException, InterruptedException {
    if (doAbstraction) {
      if (satCheckAtAbstraction) {
        if (unsatCheck(oldState.getAbstractionFormula(), pathFormula)) {
          return Collections.emptySet();
        }
      }
      return Collections.singleton(
          new PredicateAbstractState.ComputeAbstractionState(
              pathFormula,
              oldState.getAbstractionFormula(),
              oldState.getAbstractionLocationsOnPath()));
    } else {
      return handleNonAbstractionFormulaLocation(pathFormula, oldState);
    }
  }


  /**
   * Does special things when we do not compute an abstraction for the
   * successor. This currently only envolves an optional sat check.
   */
  private Collection<PredicateAbstractState> handleNonAbstractionFormulaLocation(
      PathFormula pathFormula, PredicateAbstractState oldState)
          throws SolverException, InterruptedException {
    boolean satCheck = (satCheckBlockSize > 0) && (pathFormula.getLength() >= satCheckBlockSize);

    logger.log(Level.FINEST, "Handling non-abstraction location",
        (satCheck ? "with satisfiability check" : ""));

    if (satCheck) {
      if (unsatCheck(oldState.getAbstractionFormula(), pathFormula)) {
        return Collections.emptySet();
      }
    }

    // create the new abstract state for non-abstraction location
    return Collections.singleton(
        mkNonAbstractionStateWithNewPathFormula(pathFormula, oldState));
  }

  /**
   * Checks if lastAbstraction & pathFromLastAbstraction is unsat.
   * Collects sat check information for statistics
   */
  private boolean unsatCheck(final AbstractionFormula lastAbstraction, final PathFormula pathFormulaFromLastAbstraction)
      throws SolverException, InterruptedException {
    satCheckTimer.start();

    boolean unsat = formulaManager.unsat(lastAbstraction, pathFormulaFromLastAbstraction);

    satCheckTimer.stop();

    if (unsat) {
      numSatChecksFalse++;
      logger.log(Level.FINEST, "Abstraction & PathFormula is unsatisfiable.");
    }

    return unsat;
  }

  /**
   * Converts an edge into a formula and creates a conjunction of it with the
   * previous pathFormula.
   *
   * This method implements the strongest post operator.
   *
   * @param pathFormula The previous pathFormula.
   * @param edge  The edge to analyze.
   * @return  The new pathFormula.
   */
  private PathFormula convertEdgeToPathFormula(PathFormula pathFormula, CFAEdge edge) throws CPATransferException, InterruptedException {
    pathFormulaTimer.start();
    try {
      // compute new pathFormula with the operation on the edge
      return pathFormulaManager.makeAnd(pathFormula, edge);
    } finally {
      pathFormulaTimer.stop();
    }
  }

  /*
   * Here is some code that checks memory safety properties with predicate analysis.
   * It used two configuration flags to enable these checks,
   * and relied on PredicateAbstractState to implement Targetable.
   * This is both not desired (especially the former),
   * since specifications should not be hard-coded in analysis,
   * but instead given as automata.
   * Furthermore, these checks were too expensive to be usable.
   * Thus this code is disabled now.
   * If it is one day desired to re-add these checks,
   * the checks should get executed on request of the AutomatonCPA,
   * possibly via the AbstractQueryableState interface or strengthen.

      Pair<PathFormula, ErrorConditions> edgeResult;
      pathFormulaTimer.start();
      try {
        edgeResult = pathFormulaManager.makeAndWithErrorConditions(element.getPathFormula(), edge);
      } finally {
        pathFormulaTimer.stop();
      }

      PathFormula pathFormula = edgeResult.getFirst();
      ErrorConditions conditions = edgeResult.getSecond();

      // check whether to do abstraction
      boolean doAbstraction = blk.isBlockEnd(edge, pathFormula);

      BooleanFormula invalidDerefCondition = conditions.getInvalidDerefCondition();
      BooleanFormula invalidFreeCondition = conditions.getInvalidFreeCondition();

      if (bfmgr.isTrue(invalidDerefCondition)) {
        return createState(element, pathFormula, loc, doAbstraction, ViolatedProperty.VALID_DEREF);
      }
      if (bfmgr.isTrue(invalidFreeCondition)) {
        return createState(element, pathFormula, loc, doAbstraction, ViolatedProperty.VALID_FREE);
      }

      List<PredicateAbstractState> newStates = new ArrayList<>(2);

      if (checkValidDeref && !bfmgr.isFalse(invalidDerefCondition)) {
        logger.log(Level.ALL, "Adding invalid-deref condition", invalidDerefCondition);
        PathFormula targetPathFormula = pathFormulaManager.makeAnd(edgeResult.getFirst(), invalidDerefCondition);
        newStates.addAll(createState(element, targetPathFormula, loc, doAbstraction,
            ViolatedProperty.VALID_DEREF));

        pathFormula = pathFormulaManager.makeAnd(pathFormula,
            bfmgr.not(invalidDerefCondition));
      }

      if (checkValidFree && !bfmgr.isFalse(invalidFreeCondition)) {
        logger.log(Level.ALL, "Adding invalid-free condition", invalidFreeCondition);
        PathFormula targetPathFormula = pathFormulaManager.makeAnd(edgeResult.getFirst(), invalidFreeCondition);
        newStates.addAll(createState(element, targetPathFormula, loc, doAbstraction,
            ViolatedProperty.VALID_FREE));

        pathFormula = pathFormulaManager.makeAnd(pathFormula,
            bfmgr.not(invalidFreeCondition));
      }
   */


  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pElement,
      List<AbstractState> otherElements, CFAEdge edge, Precision pPrecision)
          throws CPATransferException, InterruptedException {

    strengthenTimer.start();
    try {

      PredicateAbstractState element = (PredicateAbstractState) pElement;
      if (element.isAbstractionState()) {
        // can't do anything with this object because the path formula of
        // abstraction elements has to stay "true"
        return Collections.singleton(element);
      }

      if (element instanceof ComputeAbstractionState && strengthenWithReusedAbstractions) {
        CFANode location =
            Iterables.getOnlyElement(
                from(otherElements).transform(AbstractStates.EXTRACT_LOCATION).filter(notNull()));
        element = updateStateWithAbstractionFromFile((ComputeAbstractionState) element, location);
      }

      boolean errorFound = false;
      for (AbstractState lElement : otherElements) {
        if (lElement instanceof AssumptionStorageState) {
          element = strengthen(element, (AssumptionStorageState) lElement);
        }

        if (element instanceof ComputeAbstractionState && lElement instanceof ARGReplayState) {
          element = strengthen((ComputeAbstractionState)element, (ARGReplayState) lElement);
        }

        /*
         * Add additional assumptions from an automaton state.
         */
        if (!ignoreStateAssumptions && lElement instanceof AbstractStateWithAssumptions) {
          element = strengthen(edge.getSuccessor(), element, (AbstractStateWithAssumptions) lElement);
        }

        if (strengthenWithFormulaReportingStates && lElement instanceof FormulaReportingState) {
          element = strengthen(element, (FormulaReportingState) lElement);
        }


        if (AbstractStates.isTargetState(lElement)) {
          errorFound = true;
        }
      }

      // check satisfiability in case of error
      // (not necessary for abstraction elements)
      if (errorFound && targetStateSatCheck) {
        element = strengthenSatCheck(element, getAnalysisSuccesor(edge));
        if (element == null) {
          // successor not reachable
          return Collections.emptySet();
        }
      }

      return Collections.singleton(element);
    } catch (SolverException e) {
      throw new CPATransferException("Solver failed during strengthen sat check", e);

    } finally {
      strengthenTimer.stop();
    }
  }

  private Multimap<Integer, BooleanFormula> abstractions = null; // lazy initialization

  private PredicateAbstractState updateStateWithAbstractionFromFile(
      ComputeAbstractionState pPredicateState, CFANode pLocation)
      throws CPATransferException {

    if (abstractions == null) { // lazy initialization
      strengthenReuseReadTimer.start();

      PredicateAbstractionsStorage abstractionStorage;
      Converter converter = Converter.getConverter(PrecisionConverter.INT2BV, cfa, logger);
      try {
        abstractionStorage = new PredicateAbstractionsStorage(strengthenWithReusedAbstractionsFile, logger, fmgr, converter);
      } catch (PredicateParsingFailedException e) {
        throw new CPATransferException("cannot read abstractions from file, parsing fail", e);
      }

      abstractions = HashMultimap.create();
      for (AbstractionNode absNode : abstractionStorage.getAbstractions().values()) {
        Optional<Integer> location = absNode.getLocationId();
        if (location.isPresent()) {
          abstractions.put(location.get(), absNode.getFormula());
        }
      }

      strengthenReuseReadTimer.stop();
    }

    for (BooleanFormula possibleConstraint : abstractions.get(pLocation.getNodeNumber())) {
      // lets try all available abstractions formulas, perhaps more of them are valid
      addConstraintIfValid(pPredicateState, possibleConstraint);
    }

    return pPredicateState;
  }

  private PredicateAbstractState strengthen(ComputeAbstractionState predicateState, ARGReplayState state) {
    // we have following step: [transfer, strengthen, refine]
    // in "refine" the expansive abstraction is computed,
    // so we try to get information from other states to avoid abstraction.
    for (ARGState innerState : state.getStates()) {
      PredicateAbstractState oldPredicateState = AbstractStates.extractStateByType(innerState, PredicateAbstractState.class);
      if (oldPredicateState != null && oldPredicateState.isAbstractionState()) {
        PredicateCPA oldPredicateCPA = CPAs.retrieveCPA(state.getCPA(), PredicateCPA.class);
        predicateState = updateComputeAbstractionState(predicateState, oldPredicateState, oldPredicateCPA);
        // we can either break here, or we use all available matching states.
      }
    }
    return predicateState;
  }

  private ComputeAbstractionState updateComputeAbstractionState(ComputeAbstractionState pPredicateState,
      PredicateAbstractState pOldPredicateState, PredicateCPA oldPredicateCPA) {
    // TODO while converting constraints from  INT to BV, re-use as many sub-formula as possible for all old abstractions,
    // such that we get the same BDD-nodes for atoms of different old abstractions.

    strengthenReuseConvertTimer.start();

    StringBuilder in = new StringBuilder();
    Converter converter = Converter.getConverter(PrecisionConverter.INT2BV, cfa, logger);
    Appender app = oldPredicateCPA.getTransferRelation().fmgr.dumpFormula(
        pOldPredicateState.getAbstractionFormula().asFormula());

    try {
      app.appendTo(in);
    } catch (IOException e) {
      throw new AssertionError(e.getMessage());
    }

    LogManagerWithoutDuplicates logger2 = new LogManagerWithoutDuplicates(logger);
    StringBuilder out = new StringBuilder();
    for (String line : in.toString().split("\n")) {
      line = FormulaParser.convertFormula(checkNotNull(converter), line, logger2);
      if (line != null) {
        out.append(line).append("\n");
      }
    }

    BooleanFormula constraint = this.fmgr.parse(out.toString());

    strengthenReuseConvertTimer.stop();

    addConstraintIfValid(pPredicateState, constraint);

    return pPredicateState;
  }

  /**
   * We add the constraint as predicate, such that the later executed
   * predicate abstraction automatically checks the validity.
   */
  private void addConstraintIfValid(ComputeAbstractionState pPredicateState,
      BooleanFormula constraint) {
    pPredicateState.addAdditionalPredicates(formulaManager.getPredicatesForAtomsOf(constraint));
  }

  private PredicateAbstractState strengthen(CFANode pNode, PredicateAbstractState pElement,
      AbstractStateWithAssumptions pAssumeElement) throws CPATransferException, InterruptedException {

    PathFormula pf = pElement.getPathFormula();

    for (AssumeEdge assumption : pAssumeElement.getAsAssumeEdges(pNode.getFunctionName())) {
      // assumptions do not contain compete type nor scope information
      // hence, not all types can be resolved, so ignore these
      // TODO: the witness automaton is complete in that regard, so use that in future
      if(assumptionContainsProblemType(assumption)) {
        continue;
      }
      pf = convertEdgeToPathFormula(pf, assumption);
    }

    if (pf != pElement.getPathFormula()) {
      return replacePathFormula(pElement, pf);
    } else {
      return pElement;
    }
  }

  private PredicateAbstractState strengthen(PredicateAbstractState pElement,
      AssumptionStorageState pElement2) {

    if (pElement2.isAssumptionTrue() || pElement2.isAssumptionFalse()) {
      // we don't add the assumption false in order to not forget the content of the path formula
      // (we need it for post-processing)
      return pElement;
    }

    String asmpt = pElement2.getAssumptionAsString().toString();

    PathFormula pf = pathFormulaManager.makeAnd(pElement.getPathFormula(), fmgr.parse(asmpt));

    return replacePathFormula(pElement, pf);
  }

  private PredicateAbstractState strengthen(
      PredicateAbstractState pElement, FormulaReportingState pFormulaReportingState) {

    BooleanFormula formula =
        pFormulaReportingState.getFormulaApproximation(fmgr, pathFormulaManager);

    if (bfmgr.isTrue(formula) || bfmgr.isFalse(formula)) {
      return pElement;
    }

    PathFormula previousPathFormula = pElement.getPathFormula();
    PathFormula newPathFormula = pathFormulaManager.makeAnd(previousPathFormula, formula);

    return replacePathFormula(pElement, newPathFormula);
  }

  /**
   * Returns a new state with a given pathFormula. All other fields stay equal.
   */
  private PredicateAbstractState replacePathFormula(PredicateAbstractState oldElement, PathFormula newPathFormula) {
    if (oldElement instanceof ComputeAbstractionState) {
      return new ComputeAbstractionState(
          newPathFormula,
          oldElement.getAbstractionFormula(),
          oldElement.getAbstractionLocationsOnPath());
    } else {
      assert !oldElement.isAbstractionState();
      return mkNonAbstractionStateWithNewPathFormula(newPathFormula, oldElement);
    }
  }

  private PredicateAbstractState strengthenSatCheck(
      PredicateAbstractState pElement, CFANode loc)
          throws SolverException, InterruptedException {
    logger.log(Level.FINEST, "Checking for feasibility of path because error has been found");

    strengthenCheckTimer.start();
    PathFormula pathFormula = pElement.getPathFormula();
    boolean unsat = formulaManager.unsat(pElement.getAbstractionFormula(), pathFormula);
    strengthenCheckTimer.stop();

    if (unsat) {
      numStrengthenChecksFalse++;
      logger.log(Level.FINEST, "Path is infeasible.");
      return null;
    } else {
      // although this is not an abstraction location, we fake an abstraction
      // because refinement code expects it to be like this
      logger.log(Level.FINEST, "Last part of the path is not infeasible.");

      // set abstraction to true (we don't know better)
      AbstractionFormula abs = formulaManager.makeTrueAbstractionFormula(pathFormula);

      PathFormula newPathFormula = pathFormulaManager.makeEmptyPathFormula(pathFormula);

      // update abstraction locations map
      PersistentMap<CFANode, Integer> abstractionLocations = pElement.getAbstractionLocationsOnPath();
      Integer newLocInstance = firstNonNull(abstractionLocations.get(loc), 0) + 1;
      abstractionLocations = abstractionLocations.putAndCopy(loc, newLocInstance);

      return PredicateAbstractState.mkAbstractionState(newPathFormula,
          abs, abstractionLocations);
    }
  }

  boolean areAbstractSuccessors(AbstractState pElement, CFAEdge pCfaEdge,
      Collection<? extends AbstractState> pSuccessors)
          throws SolverException, CPATransferException, InterruptedException {
    PredicateAbstractState predicateElement = (PredicateAbstractState) pElement;
    PathFormula pathFormula = computedPathFormulae.get(predicateElement);
    if (pathFormula == null) {
      pathFormula = pathFormulaManager.makeEmptyPathFormula(predicateElement.getPathFormula());
    }
    boolean result = true;

    if (pSuccessors.isEmpty()) {
      satCheckTimer.start();
      PathFormula pFormula = convertEdgeToPathFormula(pathFormula, pCfaEdge);
      Collection<? extends AbstractState> foundSuccessors =
          handleNonAbstractionFormulaLocation(pFormula, predicateElement);
      //if we found successors, they all have to be unsat
      for (AbstractState e : foundSuccessors) {
        PredicateAbstractState successor = (PredicateAbstractState) e;
        if (!formulaManager.unsat(successor.getAbstractionFormula(), successor.getPathFormula())) {
          result = false;
        }
      }
      satCheckTimer.stop();
      return result;
    }

    for (AbstractState e : pSuccessors) {
      PredicateAbstractState successor = (PredicateAbstractState) e;

      if (successor.isAbstractionState()) {
        pathFormula = convertEdgeToPathFormula(pathFormula, pCfaEdge);
        // check abstraction
        abstractionCheckTimer.start();
        if (!formulaManager.checkCoverage(predicateElement.getAbstractionFormula(), pathFormula,
            successor.getAbstractionFormula())) {
          result = false;
        }
        abstractionCheckTimer.stop();
      } else {
        // check abstraction
        abstractionCheckTimer.start();
        if (!successor.getAbstractionFormula().equals(predicateElement.getAbstractionFormula())) {
          result = false;
        }
        abstractionCheckTimer.stop();

        // compute path formula
        PathFormula computedPathFormula = convertEdgeToPathFormula(pathFormula, pCfaEdge);
        PathFormula mergeWithPathFormula = computedPathFormulae.get(successor);
        if (mergeWithPathFormula != null) {
          computedPathFormulae.put(successor, pathFormulaManager.makeOr(mergeWithPathFormula, computedPathFormula));
        } else {
          computedPathFormulae.put(successor, computedPathFormula);
        }
      }
    }

    return result;
  }

  private boolean assumptionContainsProblemType(AssumeEdge assumption) {
    CExpression expression = (CExpression) assumption.getExpression();
    CIdExpressionCollectorVisitor collector = new CIdExpressionCollectorVisitor();
    expression.accept(collector);
    for (CIdExpression var : collector.getReferencedIdExpressions()) {
      if (var.getExpressionType() instanceof CProblemType) {
        return true;
      }
    }
    return false;
  }

  public void changeExplicitAbstractionNodes(final ImmutableSet<CFANode> explicitlyAbstractAt) {
    blk.setExplicitAbstractionNodes(explicitlyAbstractAt);
  }
}
