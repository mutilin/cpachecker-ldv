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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RelevantPredicatesComputer;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;


public class ABMPredicateReducer implements Reducer {

  static final Timer reduceTimer = new Timer();
  static final Timer expandTimer = new Timer();
  static final Timer extractTimer = new Timer();

  private final PathFormulaManager pmgr;
  private final PredicateAbstractionManager pamgr;
  private final RelevantPredicatesComputer relevantComputer;
  private final LogManager logger;

  public ABMPredicateReducer(ABMPredicateCPA cpa, RelevantPredicatesComputer pRelevantPredicatesComputer) {
    this.pmgr = cpa.getPathFormulaManager();
    this.pamgr = cpa.getPredicateManager();
    this.logger = cpa.getLogger();
    this.relevantComputer = pRelevantPredicatesComputer;
  }

  @Override
  public AbstractState getVariableReducedState(
      AbstractState pExpandedState, Block pContext,
      CFANode pLocation) {

    PredicateAbstractState predicateElement = (PredicateAbstractState)pExpandedState;

    if (!predicateElement.isAbstractionState()) {
      return predicateElement;
    }

    reduceTimer.start();
    try {
      AbstractionFormula oldAbstraction = predicateElement.getAbstractionFormula();

      Region oldRegion = oldAbstraction.asRegion();

      Collection<AbstractionPredicate> predicates = extractPredicates(oldRegion);
      Collection<AbstractionPredicate> removePredicates =
          relevantComputer.getIrrelevantPredicates(pContext, predicates);

      PathFormula pathFormula = predicateElement.getPathFormula();
      assert pathFormula.getFormula().isTrue();

      AbstractionFormula newAbstraction = pamgr.reduce(oldAbstraction, removePredicates, pathFormula.getSsa());

      return PredicateAbstractState.abstractionState(pathFormula, newAbstraction);
    } finally {
      reduceTimer.stop();
    }
  }

  @Override
  public AbstractState getVariableExpandedState(
      AbstractState pRootState, Block pReducedContext,
      AbstractState pReducedState) {

    PredicateAbstractState rootState = (PredicateAbstractState)pRootState;
    PredicateAbstractState reducedState = (PredicateAbstractState)pReducedState;

    if (!reducedState.isAbstractionState()) { return reducedState; }
    //Note: ABM might introduce some additional abstraction if root region is not a cube
    expandTimer.start();
    try {

      AbstractionFormula rootAbstraction = rootState.getAbstractionFormula();
      AbstractionFormula reducedAbstraction = reducedState.getAbstractionFormula();

      Collection<AbstractionPredicate> rootPredicates = extractPredicates(rootAbstraction.asRegion());
      Collection<AbstractionPredicate> relevantRootPredicates =
          relevantComputer.getRelevantPredicates(pReducedContext, rootPredicates);
      //for each removed predicate, we have to lookup the old (expanded) value and insert it to the reducedStates region

      PathFormula oldPathFormula = reducedState.getPathFormula();
      assert oldPathFormula.getFormula().isTrue();
      SSAMap oldSSA = oldPathFormula.getSsa();

      //pathFormula.getSSa() might not contain index for the newly added variables in predicates; while the actual index is not really important at this point,
      //there still should be at least _some_ index for each variable of the abstraction formula.
      SSAMapBuilder builder = oldSSA.builder();
      SSAMap rootSSA = rootState.getPathFormula().getSsa();
      for (String var : rootSSA.allVariables()) {
        //if we do not have the index in the reduced map..
        if (oldSSA.getIndex(var) == -1) {
          //add an index (with the value of rootSSA)
          builder.setIndex(var, rootSSA.getIndex(var));
        }
      }
      SSAMap newSSA = builder.build();
      PathFormula newPathFormula = pmgr.makeNewPathFormula(oldPathFormula, newSSA);

      AbstractionFormula newAbstractionFormula =
          pamgr.expand(reducedAbstraction, rootAbstraction, relevantRootPredicates, newSSA);

      return PredicateAbstractState.abstractionState(newPathFormula,
          newAbstractionFormula);
    } finally {
      expandTimer.stop();
    }
  }

  private Collection<AbstractionPredicate> extractPredicates(Region pRegion) {
    extractTimer.start();
    try {
      return pamgr.extractPredicates(pRegion);
    }
    finally {
      extractTimer.stop();
    }
  }

  @Override
  public Object getHashCodeForState(AbstractState pElementKey, Precision pPrecisionKey) {

    PredicateAbstractState element = (PredicateAbstractState)pElementKey;
    PredicatePrecision precision = (PredicatePrecision)pPrecisionKey;

    return Pair.of(element.getAbstractionFormula().asRegion(), precision);
  }

  private Map<Pair<Integer, Block>, Precision> reduceCache = new HashMap<Pair<Integer, Block>, Precision>();

  public void clearCaches() {
    reduceCache.clear();
  }

  @Override
  public Precision getVariableReducedPrecision(Precision pPrecision,
      Block pContext) {
    PredicatePrecision precision = (PredicatePrecision)pPrecision;
    Pair<Integer, Block> key = Pair.of(precision.getId(), pContext);
    Precision result = reduceCache.get(key);
    if(result != null) {
      return result;
    }

    result = new ReducedPredicatePrecision(precision, pContext);
    reduceCache.put(key, result);
    return result;
  }

  @Override
  public Precision getVariableExpandedPrecision(Precision pRootPrecision, Block pRootContext, Precision pReducedPrecision) {
    PredicatePrecision rootPrecision = (PredicatePrecision)pRootPrecision;
    PredicatePrecision toplevelPrecision = rootPrecision;
    if(rootPrecision instanceof ReducedPredicatePrecision) {
      toplevelPrecision = ((ReducedPredicatePrecision)rootPrecision).getRootPredicatePrecision();
    }

    PredicatePrecision derivedToplevelPrecision = ((ReducedPredicatePrecision)pReducedPrecision).getRootPredicatePrecision();

    if(derivedToplevelPrecision == toplevelPrecision) {
      return pRootPrecision;
    }

    PredicatePrecision mergedToplevelPrecision = mergePrecisions(toplevelPrecision, derivedToplevelPrecision);

    return getVariableReducedPrecision(mergedToplevelPrecision, pRootContext);
  }


  private PredicatePrecision mergePrecisions(PredicatePrecision lhs, PredicatePrecision rhs) {
    Set<AbstractionPredicate> globalPredicates = new HashSet<AbstractionPredicate>();
    globalPredicates.addAll(rhs.getGlobalPredicates());
    globalPredicates.addAll(lhs.getGlobalPredicates());

    ImmutableSetMultimap.Builder<CFANode, AbstractionPredicate> pmapBuilder = ImmutableSetMultimap.builder();
    pmapBuilder.putAll(rhs.getPredicateMap());
    pmapBuilder.putAll(lhs.getPredicateMap());

    return new PredicatePrecision(pmapBuilder.build(), globalPredicates);
  }

  private class ReducedPredicatePrecision extends PredicatePrecision {
    private final PredicatePrecision rootPredicatePrecision;

    private final PredicatePrecision expandedPredicatePrecision;
    private final Block context;

    private ImmutableSetMultimap<CFANode, AbstractionPredicate> evaluatedPredicateMap;
    private ImmutableSet<AbstractionPredicate> evaluatedGlobalPredicates;


    public ReducedPredicatePrecision(PredicatePrecision expandedPredicatePrecision, Block context) {
      super(null);

      this.expandedPredicatePrecision = expandedPredicatePrecision;
      this.context = context;

      if(expandedPredicatePrecision instanceof ReducedPredicatePrecision) {
        this.rootPredicatePrecision = ((ReducedPredicatePrecision) expandedPredicatePrecision).getRootPredicatePrecision();
      }
      else {
        this.rootPredicatePrecision = expandedPredicatePrecision;
      }
      assert !(rootPredicatePrecision instanceof ReducedPredicatePrecision);

      this.evaluatedPredicateMap = null;
      this.evaluatedGlobalPredicates = null;
    }

    public PredicatePrecision getRootPredicatePrecision() {
      return rootPredicatePrecision;
    }

    private void computeView() {
      if(evaluatedPredicateMap == null) {
        ReducedPredicatePrecision lExpandedPredicatePrecision = null;
        if(expandedPredicatePrecision instanceof ReducedPredicatePrecision) {
          lExpandedPredicatePrecision = (ReducedPredicatePrecision)expandedPredicatePrecision;
        }

        evaluatedGlobalPredicates = ImmutableSet.copyOf(relevantComputer.getRelevantPredicates(context, rootPredicatePrecision.getGlobalPredicates()));

        ImmutableSetMultimap.Builder<CFANode, AbstractionPredicate> pmapBuilder = ImmutableSetMultimap.builder();
        Set<CFANode> keySet = lExpandedPredicatePrecision==null?rootPredicatePrecision.getPredicateMap().keySet():lExpandedPredicatePrecision.approximatePredicateMap().keySet();
        for(CFANode node : keySet) {
          if(context.getNodes().contains(node)) {
            Collection<AbstractionPredicate> set = relevantComputer.getRelevantPredicates(context, rootPredicatePrecision.getPredicates(node));
            pmapBuilder.putAll(node, set);
          }
        }

        evaluatedPredicateMap = pmapBuilder.build();
      }
    }

    private SetMultimap<CFANode, AbstractionPredicate> approximatePredicateMap() {
      if(evaluatedPredicateMap == null) {
        return rootPredicatePrecision.getPredicateMap();
      } else {
        return evaluatedPredicateMap;
      }
    }

    @Override
    public SetMultimap<CFANode, AbstractionPredicate> getPredicateMap() {
      computeView();
      return evaluatedPredicateMap;
    }

    @Override
    public Set<AbstractionPredicate> getGlobalPredicates() {
      if(evaluatedGlobalPredicates != null) {
        return evaluatedGlobalPredicates;
      } else {
        return relevantComputer.getRelevantPredicates(context, rootPredicatePrecision.getGlobalPredicates());
      }
    }

    @Override
    public Set<AbstractionPredicate> getPredicates(CFANode loc) {
      if(!context.getNodes().contains(loc)) {
        logger.log(Level.WARNING, context, "was left in an unexpected way. Analysis might be unsound.");
      }

      if(evaluatedPredicateMap != null) {
        Set<AbstractionPredicate> result = evaluatedPredicateMap.get(loc);
        if (result.isEmpty()) {
          result = evaluatedGlobalPredicates;
        }
        return result;
      }
      else {
        Set<AbstractionPredicate> result = relevantComputer.getRelevantPredicates(context, rootPredicatePrecision.getPredicates(loc));
        if (result.isEmpty()) {
          result = relevantComputer.getRelevantPredicates(context, rootPredicatePrecision.getGlobalPredicates());
        }
        return result;
      }
    }

    @Override
    public boolean equals(Object pObj) {
      if (pObj == this) {
        return true;
      } else if (!(pObj instanceof ReducedPredicatePrecision)) {
        return false;
      } else {
        computeView();
        return evaluatedPredicateMap.equals(((ReducedPredicatePrecision)pObj).evaluatedPredicateMap);
      }
    }

    @Override
    public int hashCode() {
      computeView();
      return evaluatedPredicateMap.hashCode();
    }

    @Override
    public String toString() {
      if(evaluatedPredicateMap != null) {
        return evaluatedPredicateMap.toString();
      } else {
        return "ReducedPredicatePrecision (view not computed yet)";
      }
    }

  }

  @Override
  public int measurePrecisionDifference(Precision pPrecision, Precision pOtherPrecision) {
    PredicatePrecision precision = (PredicatePrecision)pPrecision;
    PredicatePrecision otherPrecision = (PredicatePrecision)pOtherPrecision;

    int distance = 0;

    for(AbstractionPredicate p : precision.getGlobalPredicates()) {
      if(!otherPrecision.getGlobalPredicates().contains(p)) {
        distance++;
      }
    }

    for(CFANode node : precision.getPredicateMap().keySet()) {
      for(AbstractionPredicate p : precision.getPredicateMap().get(node)) {
        if(!otherPrecision.getPredicateMap().get(node).contains(p)) {
          distance++;
        }
      }
    }

    return distance;
  }
}
