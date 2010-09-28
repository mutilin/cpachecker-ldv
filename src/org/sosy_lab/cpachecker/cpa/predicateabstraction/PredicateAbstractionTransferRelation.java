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
package org.sosy_lab.cpachecker.cpa.predicateabstraction;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.symbpredabstraction.Predicate;
import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.AbstractFormula;
/**
 * TransferRelation for explicit-state lazy abstraction. This is the most
 * complex of the CPA-related classes, and where the analysis is actually
 * performed.
 *
 * @author Alberto Griggio <alberto.griggio@disi.unitn.it>
 */
public class PredicateAbstractionTransferRelation implements TransferRelation {

  private PredicateAbstractionAbstractDomain domain;
  private final LogManager logger;
  //private Map<Path, Integer> abstractCex;

  private int numAbstractStates = 0; // for statistics

  // this is used for deciding how much of the ART to undo after refinement
  // private Deque<ExplicitAbstractElement> lastErrorPath;
  // private int samePathAlready = 0;

  private boolean notEnoughPredicatesFlag = false;

  public PredicateAbstractionTransferRelation(PredicateAbstractionAbstractDomain d, LogManager logger) {
    domain = d;
    this.logger = logger;
    //  abstractTree = new AbstractReachabilityTree();
    // lastErrorPath = null;
    //  abstractCex = new HashMap<Path, Integer>();
  }

  public boolean notEnoughPredicates() { return notEnoughPredicatesFlag; }

  public int getNumAbstractStates() { return numAbstractStates; }

  // isFunctionStart and isFunctionEnd are used to manage the call stack
  //private boolean isFunctionStart(PredicateAbstractionAbstractElement elem) {
  //return (elem.getLocation() instanceof FunctionDefinitionNode);
  //}

  //private boolean isFunctionEnd(PredicateAbstractionAbstractElement elem) {
  //CFANode n = elem.getLocation();
  //return (n.getNumLeavingEdges() > 0 &&
  //n.getLeavingEdge(0) instanceof ReturnEdge);
  //}

  // performs the abstract post operation
  private Collection<PredicateAbstractionAbstractElement> buildSuccessor(PredicateAbstractionAbstractElement e,
      CFAEdge edge) throws CPATransferException {
    PredicateAbstractionCPA cpa = domain.getCPA();
    //    CFANode succLoc = edge.getSuccessor();

    // check whether the successor is an error location: if so, we want
    // to check for feasibility of the path...

    Collection<Predicate> predicates =
      cpa.getPredicateMap().getRelevantPredicates(
          edge.getSuccessor());
    //  if (predicates.isEmpty() && e.getParent() != null) {
    //  predicates = cpa.getPredicateMap().getRelevantPredicates(
    //  e.getParent().getLocation());
    //  }

    PredicateAbstractionAbstractElement succ = new PredicateAbstractionAbstractElement(cpa);

    // if e is the end of a function, we must find the correct return
    // location
    //  if (isFunctionEnd(e)) {
    //  CFANode retNode = e.topContextLocation();
    //  if (!succLoc.equals(retNode)) {
    //  CPAMain.logManager.log(Level.ALL, "DEBUG_1",
    //  "Return node for this call is: ", retNode,
    //  ", but edge leads to: ", succLoc, ", returning BOTTOM");
    //  return domain.getBottomElement();
    //  }
    //  }

    //  succ.setContext(e.getContext(), false);
    //  if (isFunctionEnd(e)) {
    //  succ.popContext();
    //  }

    PredicateAbstractionFormulaManager amgr = cpa.getPredAbsFormulaManager();
    AbstractFormula abstraction = amgr.buildAbstraction(
        e, succ, edge, predicates);
    succ.setAbstraction(abstraction);
    //  succ.setParent(e);

    if (logger.wouldBeLogged(Level.ALL)) {
      logger.log(Level.ALL, "DEBUG_1", "COMPUTED ABSTRACTION:",
          amgr.toConcrete(abstraction));
    }

    if (cpa.getAbstractFormulaManager().isFalse(abstraction)) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(succ);
    }
  }

  @Override
  public Collection<PredicateAbstractionAbstractElement> getAbstractSuccessors(
      AbstractElement element, Precision prec, CFAEdge cfaEdge) throws CPATransferException {
    logger.log(Level.FINEST,
        "Getting Abstract Successor of element: ", element,
        " on edge: ", cfaEdge.getRawStatement());

    // To get the successor, we compute the predicate abstraction of the
    // formula of element plus all the edges that connect any of the
    // inner nodes of the summary of element to any inner node of the
    // destination
    PredicateAbstractionAbstractElement e = (PredicateAbstractionAbstractElement)element;

    Collection<PredicateAbstractionAbstractElement> ret = buildSuccessor(e, cfaEdge);

    logger.log(Level.FINEST,
        "Successor is: ", ret);

    return ret;
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element,
      List<AbstractElement> otherElements, CFAEdge cfaEdge,
      Precision precision) {
    return null;
  }
}
