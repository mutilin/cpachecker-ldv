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
package org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Files;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.fllesh.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.fllesh.cpa.art.ARTReachedSet;
import org.sosy_lab.cpachecker.fllesh.cpa.art.AbstractARTBasedRefiner;
import org.sosy_lab.cpachecker.fllesh.cpa.art.Path;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.interfaces.Predicate;
import org.sosy_lab.cpachecker.fllesh.cpa.symbpredabsCPA.util.symbpredabstraction.trace.CounterexampleTraceInfo;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;

@Options(prefix="cpas.symbpredabs")
public class SymbPredAbsRefiner extends AbstractARTBasedRefiner {

  @Option(name="refinement.addPredicatesGlobally")
  private boolean addPredicatesGlobally = false;
  
  @Option(name="errorPath.export")
  private boolean exportErrorPath = true;
  
  @Option(name="errorPath.file", type=Option.Type.OUTPUT_FILE)
  private File exportFile = new File("ErrorPathAssignment.txt");
  
  private final LogManager logger;
  private final SymbPredAbsFormulaManager formulaManager;
  private CounterexampleTraceInfo mCounterexampleTraceInfo;

  public SymbPredAbsRefiner(final ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);

    SymbPredAbsCPA symbPredAbsCpa = this.getArtCpa().retrieveWrappedCpa(SymbPredAbsCPA.class);
    if (symbPredAbsCpa == null) {
      throw new CPAException(getClass().getSimpleName() + " needs a SymbPredAbsCPA");
    }

    symbPredAbsCpa.getConfiguration().inject(this);
    logger = symbPredAbsCpa.getLogger();
    formulaManager = symbPredAbsCpa.getFormulaManager();
  }

  @Override
  public boolean performRefinement(ARTReachedSet pReached, Path pPath) throws CPAException {

    logger.log(Level.FINEST, "Starting refinement for SymbPredAbsCPA");

    // create path with all abstraction location elements (excluding the initial
    // element, which is not in pPath)
    // the last element is the element corresponding to the error location
    // (which is twice in pPath)
    ArrayList<SymbPredAbsAbstractElement> path = new ArrayList<SymbPredAbsAbstractElement>();
    SymbPredAbsAbstractElement lastElement = null;
    for (Pair<ARTElement,CFAEdge> artPair : pPath) {
      SymbPredAbsAbstractElement symbElement =
        artPair.getFirst().retrieveWrappedElement(SymbPredAbsAbstractElement.class);

      if (symbElement.isAbstractionNode() && symbElement != lastElement) {
        path.add(symbElement);
      }
      lastElement = symbElement;
    }

    Precision oldPrecision = pReached.getPrecision(pReached.getLastElement());
    SymbPredAbsPrecision oldSymbPredAbsPrecision = null;
    if (oldPrecision instanceof SymbPredAbsPrecision) {
      oldSymbPredAbsPrecision = (SymbPredAbsPrecision)oldPrecision;
    } else if (oldPrecision instanceof WrapperPrecision) {
      oldSymbPredAbsPrecision = ((WrapperPrecision)oldPrecision).retrieveWrappedPrecision(SymbPredAbsPrecision.class);
    }
    if (oldSymbPredAbsPrecision == null) {
      throw new IllegalStateException("Could not find the SymbPredAbsPrecision for the error element");
    }

    logger.log(Level.ALL, "Abstraction trace is", path);

    // build the counterexample
    CounterexampleTraceInfo info = formulaManager.buildCounterexampleTrace(path);
    mCounterexampleTraceInfo = info;

    // if error is spurious refine
    if (info.isSpurious()) {
      logger.log(Level.FINEST, "Error trace is spurious, refining the abstraction");
      Pair<ARTElement, SymbPredAbsPrecision> refinementResult =
              performRefinement(oldSymbPredAbsPrecision, path, pPath, info);

      pReached.removeSubtree(refinementResult.getFirst(), refinementResult.getSecond());
      return true;
    } else {
      // we have a real error
      logger.log(Level.FINEST, "Error trace is not spurious");
      
      if (exportErrorPath) {
        try {
          Files.writeFile(exportFile, info.getCounterexample(), false);
        } catch (IOException e) {
          logger.log(Level.WARNING, "Could not write satisfying assignment for error path to file! ("
              + e.getMessage() + ")");
        }
      }
      return false;
    }
  }

  private Pair<ARTElement, SymbPredAbsPrecision> performRefinement(SymbPredAbsPrecision oldPrecision,
      ArrayList<SymbPredAbsAbstractElement> pPath, Path pArtPath, CounterexampleTraceInfo pInfo) throws CPAException {

    Multimap<CFANode, Predicate> oldPredicateMap = oldPrecision.getPredicateMap();
    Set<Predicate> globalPredicates = oldPrecision.getGlobalPredicates();
    SymbPredAbsAbstractElement symbPredRootElement = null;
    SymbPredAbsAbstractElement firstInterpolationElement = null;

    ImmutableSetMultimap.Builder<CFANode, Predicate> pmapBuilder = ImmutableSetMultimap.builder();

    pmapBuilder.putAll(oldPredicateMap);

    for (SymbPredAbsAbstractElement e : pPath) {
      Collection<Predicate> newpreds = pInfo.getPredicatesForRefinement(e);
      CFANode loc = e.getAbstractionLocation();
      if (firstInterpolationElement == null && newpreds.size() > 0) {
        firstInterpolationElement = e;
      }
      if ((symbPredRootElement == null) && !oldPredicateMap.get(loc).containsAll(newpreds)) {
        // new predicates for this location
        symbPredRootElement = e;
      }
      pmapBuilder.putAll(loc, newpreds);
      pmapBuilder.putAll(loc, globalPredicates);
    }
    assert(firstInterpolationElement != null);

    ImmutableSetMultimap<CFANode, Predicate> newPredicateMap = pmapBuilder.build();
    SymbPredAbsPrecision newPrecision;
    if (addPredicatesGlobally) {
      newPrecision = new SymbPredAbsPrecision(newPredicateMap.values());
    } else {
      newPrecision = new SymbPredAbsPrecision(newPredicateMap, globalPredicates);
    }

    logger.log(Level.ALL, "Predicate map now is", newPredicateMap);

    // symbPredRootElement might be null here, but firstInterpolationElement
    // might be not. TODO investigate why this happens
    // We have two different strategies for the refinement root: set it to
    // the firstInterpolationElement or set it to highest location in the ART
    // where the same CFANode appears.
    // Both work, so this is a heuristics question to get the best performance.
    // My benchmark showed, that at least for the benchmarks-lbe examples it is
    // best to use strategy one iff symbPredRootElement is not null.

    ARTElement root;
    if (symbPredRootElement != null) {
      logger.log(Level.FINEST, "Found spurious counterexample,",
          "trying strategy 1: remove everything below", firstInterpolationElement, "from ART.");

      root = findARTElementof(firstInterpolationElement, pArtPath.getLast());

    } else {
      CFANode loc = firstInterpolationElement.getAbstractionLocation();

      logger.log(Level.FINEST, "Found spurious counterexample,",
          "trying strategy 2: remove everything below node", loc, "from ART.");

      root = this.getArtCpa().findHighest(pArtPath.getLast().getFirst(), loc);
    }
    return new Pair<ARTElement, SymbPredAbsPrecision>(root, newPrecision);
  }

  private ARTElement findARTElementof(SymbPredAbsAbstractElement pSymbPredRootElement,
      Pair<ARTElement, CFAEdge> pLastElement) throws CPAException {

    Deque<ARTElement> workList = new ArrayDeque<ARTElement>();
    Set<ARTElement> handled = new HashSet<ARTElement>();

    // get the error element
    workList.add(pLastElement.getFirst());

    // go backwards
    while (!workList.isEmpty()) {
      ARTElement currentElement = workList.removeFirst();
      if (!handled.add(currentElement)) {
        // currentElement was already handled
        continue;
      }

      SymbPredAbsAbstractElement currentSymbPredElement =
                currentElement.retrieveWrappedElement(SymbPredAbsAbstractElement.class);
      if (currentSymbPredElement == pSymbPredRootElement){
        return currentElement;
      }
      workList.addAll(currentElement.getParents());
    }

    throw new CPAException("Inconsistent ART");
  }
  
  public CounterexampleTraceInfo getCounterexampleTraceInfo() {
    return mCounterexampleTraceInfo;
  }
  
}
