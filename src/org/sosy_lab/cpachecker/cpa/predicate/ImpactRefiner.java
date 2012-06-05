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

import static com.google.common.collect.Iterables.skip;
import static com.google.common.collect.Lists.transform;
import static org.sosy_lab.cpachecker.util.AbstractElements.extractElementByType;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGElement;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.Path;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.util.AbstractElements;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.ExtendedFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.SymbolicRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interpolation.AbstractInterpolationBasedRefiner;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;
import org.sosy_lab.cpachecker.util.predicates.interpolation.UninstantiatingInterpolationManager;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ImpactRefiner extends AbstractInterpolationBasedRefiner<Formula, ARGElement> implements StatisticsProvider {

  private class Stats implements Statistics {

    private int newItpWasAdded = 0;

    private final Timer itpCheck  = new Timer();
    private final Timer coverTime = new Timer();
    private final Timer argUpdate = new Timer();

    @Override
    public String getName() {
      return "Impact Refiner";
    }

    @Override
    public void printStatistics(PrintStream out, Result pResult, ReachedSet pReached) {
      ImpactRefiner.this.printStatistics(out, pResult, pReached);
      out.println("  Checking whether itp is new:    " + itpCheck);
      out.println("  Coverage checks:                " + coverTime);
      out.println("  ARG update:                     " + argUpdate);
      out.println();
      out.println("Number of interpolants added:     " + newItpWasAdded);
      out.println("Number of non-new interpolants:   " + (itpCheck.getNumberOfIntervals() - newItpWasAdded));
    }
  }

  protected final ExtendedFormulaManager fmgr;
  protected final Solver solver;

  private final Stats stats = new Stats();

  public static ImpactRefiner create(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    PredicateCPA predicateCpa = CPAs.retrieveCPA(pCpa, PredicateCPA.class);
    if (predicateCpa == null) {
      throw new InvalidConfigurationException(ImpactRefiner.class.getSimpleName() + " needs a PredicateCPA");
    }

    Region initialRegion = predicateCpa.getInitialElement(null).getAbstractionFormula().asRegion();
    if (!(initialRegion instanceof SymbolicRegionManager.SymbolicRegion)) {
      throw new InvalidConfigurationException(ImpactRefiner.class.getSimpleName() + " works only with a PredicateCPA configured to store abstractions as formulas (cpa.predicate.abstraction.type=FORMULA)");
    }

    Configuration config = predicateCpa.getConfiguration();
    LogManager logger = predicateCpa.getLogger();
    ExtendedFormulaManager fmgr = predicateCpa.getFormulaManager();
    Solver solver = predicateCpa.getSolver();

    InterpolationManager<Formula> manager = new UninstantiatingInterpolationManager(
                                                  fmgr,
                                                  predicateCpa.getPathFormulaManager(),
                                                  solver,
                                                  predicateCpa.getFormulaManagerFactory(),
                                                  config, logger);

    return new ImpactRefiner(config, logger, pCpa, manager, fmgr, solver);
  }


  protected ImpactRefiner(final Configuration config, final LogManager logger,
      final ConfigurableProgramAnalysis pCpa,
      final InterpolationManager<Formula> pInterpolationManager,
      final ExtendedFormulaManager pFmgr, final Solver pSolver) throws InvalidConfigurationException, CPAException {

    super(config, logger, pCpa, pInterpolationManager);

    solver = pSolver;
    fmgr = pFmgr;
  }


  @Override
  protected List<ARGElement> transformPath(Path pPath) {
    // filter abstraction elements

    List<ARGElement> result = ImmutableList.copyOf(
        Iterables.filter(
            Iterables.transform(
                skip(pPath, 1),
                Pair.<ARGElement>getProjectionToFirst()),

            new Predicate<ARGElement>() {
                @Override
                public boolean apply(ARGElement pInput) {
                  return extractElementByType(pInput, PredicateAbstractElement.class).isAbstractionElement();
                }
            }));

    assert pPath.getLast().getFirst() == result.get(result.size()-1);
    return result;
  }

  @Override
  protected List<Formula> getFormulasForPath(List<ARGElement> pPath, ARGElement pInitialElement) {

    return transform(pPath,
        new Function<ARGElement, Formula>() {
          @Override
          public Formula apply(ARGElement e) {
            return extractElementByType(e, PredicateAbstractElement.class).getAbstractionFormula().getBlockFormula();
          }
        });
  }

  @Override
  protected void performRefinement(ARGReachedSet pReached, List<ARGElement> path,
      CounterexampleTraceInfo<Formula> cex, boolean pRepeatedCounterexample) throws CPAException {

    ReachedSet reached = pReached.asReachedSet();
    ARGElement lastElement = path.get(path.size()-1);
    assert lastElement.isTarget();

    path = path.subList(0, path.size()-1); // skip last element, itp is always false there
    assert cex.getPredicatesForRefinement().size() ==  path.size();

    List<ARGElement> changedElements = new ArrayList<ARGElement>();
    ARGElement infeasiblePartOfART = lastElement;

    for (Pair<Formula, ARGElement> interpolationPoint : Pair.zipList(cex.getPredicatesForRefinement(), path)) {
      Formula itp = interpolationPoint.getFirst();
      ARGElement w = interpolationPoint.getSecond();

      if (itp.isTrue()) {
        // do nothing
        continue;
      }

      if (itp.isFalse()) {
        // we have reached the part of the path that is infeasible
        infeasiblePartOfART = w;
        break;
      }

      Formula stateFormula = getStateFormula(w);

      stats.itpCheck.start();
      boolean isNewItp = !solver.implies(stateFormula, itp);
      stats.itpCheck.stop();

      if (isNewItp) {
        stats.newItpWasAdded++;
        addFormulaToState(itp, w);
        changedElements.add(w);
      }
    }

    if (changedElements.isEmpty() && pRepeatedCounterexample) {
      // TODO One cause for this exception is that the CPAAlgorithm sometimes
      // re-adds the parent of the error element to the waitlist, and thus the
      // error element would get re-discovered immediately again.
      // Currently the CPAAlgorithm does this only when there are siblings of
      // the target element, which should rarely happen.
      // We still need a better handling for this situation.
      throw new RefinementFailedException(RefinementFailedException.Reason.RepeatedCounterexample, null);
    }

    stats.argUpdate.start();
    for (ARGElement w : changedElements) {
      pReached.removeCoverageOf(w);
    }

    Set<ARGElement> infeasibleSubtree = infeasiblePartOfART.getSubtree();
    assert infeasibleSubtree.contains(lastElement);

    uncover(infeasibleSubtree, pReached);

    for (ARGElement removedNode : infeasibleSubtree) {
      removedNode.removeFromART();
    }
    reached.removeAll(infeasibleSubtree);
    stats.argUpdate.stop();

    // optimization: instead of closing all ancestors of v,
    // close only those that were strengthened during refine
    stats.coverTime.start();
    try {
      for (ARGElement w : changedElements) {
        if (cover(w, pReached)) {
          break; // all further elements are covered anyway
        }
      }
    } finally {
      stats.coverTime.stop();
    }

    assert !reached.contains(lastElement);
  }


  private boolean cover(ARGElement v, ARGReachedSet pReached) throws CPAException {
    assert v.mayCover();
    ReachedSet reached = pReached.asReachedSet();

    getArtCpa().getStopOperator().stop(v, reached.getReached(v), reached.getPrecision(v));
    // ignore return value of stop, because it will always be false

    if (v.isCovered()) {
      reached.removeOnlyFromWaitlist(v);

      Set<ARGElement> subtree = v.getSubtree();

      // first, uncover all necessary states

      uncover(subtree, pReached);

      // second, clean subtree of covered element
      subtree.remove(v); // but no not clean v itself

      for (ARGElement childOfV : subtree) {
        // each child of v is now not covered directly anymore
        if (childOfV.isCovered()) {
          childOfV.uncover();
        }

        reached.removeOnlyFromWaitlist(childOfV);

        childOfV.setNotCovering();
      }

      for (ARGElement childOfV : subtree) {
        // each child of v now doesn't cover anything anymore
        assert childOfV.getCoveredByThis().isEmpty();
        assert !childOfV.mayCover();
      }

      assert !reached.getWaitlist().contains(v.getSubtree());
      return true;
    }
    return false;
  }

  private void uncover(Set<ARGElement> subtree, ARGReachedSet reached) {
    Set<ARGElement> coveredStates = ARGUtils.getCoveredBy(subtree);
    for (ARGElement coveredState : coveredStates) {
      // uncover each previously covered state
      reached.uncover(coveredState);
    }
    assert ARGUtils.getCoveredBy(subtree).isEmpty() : "Subtree of covered node still covers other elements";
  }

  private Formula getStateFormula(ARGElement pARGElement) {
    return AbstractElements.extractElementByType(pARGElement, PredicateAbstractElement.class).getAbstractionFormula().asFormula();
  }

  private void addFormulaToState(Formula f, ARGElement e) {
    PredicateAbstractElement predElement = AbstractElements.extractElementByType(e, PredicateAbstractElement.class);
    AbstractionFormula af = predElement.getAbstractionFormula();

    Formula newFormula = fmgr.makeAnd(f, af.asFormula());
    Formula instantiatedNewFormula = fmgr.instantiate(newFormula, predElement.getPathFormula().getSsa());
    AbstractionFormula newAF = new AbstractionFormula(new SymbolicRegionManager.SymbolicRegion(newFormula), newFormula, instantiatedNewFormula, af.getBlockFormula());
    predElement.setAbstraction(newAF);
  }


  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
  }
}