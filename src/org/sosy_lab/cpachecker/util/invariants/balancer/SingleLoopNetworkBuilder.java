/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.invariants.balancer;

import static com.google.common.base.Predicates.instanceOf;

import java.util.Vector;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.cpa.art.Path;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException.Reason;
import org.sosy_lab.cpachecker.util.AbstractElements;
import org.sosy_lab.cpachecker.util.CFAUtils.Loop;
import org.sosy_lab.cpachecker.util.invariants.GraphUtil;
import org.sosy_lab.cpachecker.util.invariants.choosers.SingleLoopTemplateChooser;
import org.sosy_lab.cpachecker.util.invariants.choosers.TemplateChooser;
import org.sosy_lab.cpachecker.util.invariants.templates.TemplateFormula;
import org.sosy_lab.cpachecker.util.invariants.templates.TemplatePathFormulaBuilder;
import org.sosy_lab.cpachecker.util.predicates.PathFormula;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

public class SingleLoopNetworkBuilder implements NetworkBuilder {

  private final Path cePath;
  private final LogManager logger;
  private final TemplatePathFormulaBuilder tpfb;
  private final Loop loop;
  private final CFANode root;
  private final CFANode loopHead;
  private final CFANode error;
  private final PathFormula entryFormula;
  private final PathFormula loopFormula;
  private final PathFormula exitHead;
  private final PathFormula exitTail;
  private final PathFormula exitFormula;
  private final TemplateChooser chooser;

  public SingleLoopNetworkBuilder(Path pPath, LogManager pLogger) throws RefinementFailedException {
    cePath = pPath;
    logger = pLogger;
    tpfb = new TemplatePathFormulaBuilder();

    Multimap<String, Loop> loops = CFACreator.loops;
    if (loops.size() > 1) {
      // there are too many loops
      logger.log(Level.FINEST, "Could not use invariant generation for proving program safety, program has too many loops.");
      throw new RefinementFailedException(Reason.InvariantRefinementFailed, cePath);
    }

    if (loops.isEmpty()) {
      // invariant generation is unnecessary, program has no loops
      logger.log(Level.FINEST, "Could not use invariant generation for proving program safety, program has no loops.");
      throw new RefinementFailedException(Reason.InvariantRefinementFailed, cePath);
    }

    // There is just one loop. Get a hold of it.
    loop = Iterables.getOnlyElement(loops.values());

    // function edges do not count as incoming edges
    Iterable<CFAEdge> incomingEdges = Iterables.filter(loop.getIncomingEdges(),
                                                       Predicates.not(instanceOf(FunctionReturnEdge.class)));

    // Check that there is just one incoming edge into the loop,
    // and that the loop has just one loop head.
    if (Iterables.size(incomingEdges) > 1) {
      logger.log(Level.FINEST, "Could not use invariant generation for proving program safety, loop has too many incoming edges", incomingEdges);
      throw new RefinementFailedException(Reason.InvariantRefinementFailed, cePath);
    }

    if (loop.getLoopHeads().size() > 1) {
      logger.log(Level.FINEST, "Could not use invariant generation for proving program safety, loop has too many loop heads.");
      throw new RefinementFailedException(Reason.InvariantRefinementFailed, cePath);
    }

    // There is only one loop head. Grab it.
    loopHead = Iterables.getOnlyElement(loop.getLoopHeads());

    // Check that the loop head is unambiguous.
    assert loopHead.equals(Iterables.getOnlyElement(incomingEdges).getSuccessor());

    // At this point, we deem the program suitable for application of our method, and
    // we proceed to apply it.
    logger.log(Level.FINEST, "Constructing single loop network builder.");

    // Get root location.
    root = AbstractElements.extractLocation(cePath.getFirst().getFirst());

    // Get error location
    error = AbstractElements.extractLocation(cePath.getLast().getFirst());

    // Get entry path formula, from root node up to loop head,
    // loop formula, from loop head back to loop head, and
    // exit formula, from loop head to error location.
    entryFormula = buildEntryFormula(cePath, loopHead);
    loopFormula = buildLoopFormula(loop);

    Pair<PathFormula,PathFormula> exitHeadAndTail = buildExitFormulaHeadAndTail(cePath, loopHead);
    exitHead = exitHeadAndTail.getFirst();
    exitTail = exitHeadAndTail.getSecond();
    exitFormula = buildExitFormula(cePath, loopHead);

    logger.log(Level.ALL, "\nEntry, loop, and exit formulas:\nEntry: ", entryFormula, "\nLoop: ", loopFormula, "\nExit: ", exitFormula);

    // Choose an invariant template for the loop head.
    chooser = buildChooser(entryFormula, loopFormula, exitFormula, exitHead, exitTail);

  }

  private PathFormula buildEntryFormula(Path pPath, CFANode loopHead) {
    // gather CFAEdges until hit ARTElement whose location is loopHead
    Vector<CFAEdge> edges = new Vector<CFAEdge>();

    for (Pair<ARTElement, CFAEdge> pair : pPath) {
      ARTElement ae = pair.getFirst();
      CFAEdge edge = pair.getSecond();
      CFANode loc = AbstractElements.extractLocation(ae);
      if (loc == loopHead) {
        break;
      } else {
        edges.add(edge);
      }
    }
    // build path formula for these edges
    PathFormula entryFormula = tpfb.buildPathFormula(edges);
    return entryFormula;
  }

  private PathFormula buildLoopFormula(Loop loop) {
    logger.log(Level.ALL, "Loop:\n",loop);
    Vector<CFANode> loopNodes = new Vector<CFANode>(loop.getLoopNodes());
    Vector<CFAEdge> loopEdges = GraphUtil.makeEdgeLoop(loopNodes, logger);
    logger.log(Level.ALL,"Sequence of edges in loop:\n",loopEdges);
    PathFormula loopFormula = tpfb.buildPathFormula(loopEdges);
    return loopFormula;
  }

  private PathFormula buildExitFormula(Path pPath, CFANode loopHead) {
    // gather CFAEdges from ARTElement whose location is loopHead to end of path
    Vector<CFAEdge> edges = new Vector<CFAEdge>();

    boolean begun = false;
    int N = pPath.size() - 1; // we ignore last pair, since last edge is useless, hence " - 1"
    for (int i = 0; i < N; i++) {
      Pair<ARTElement, CFAEdge> pair = pPath.get(i);
      if (begun) {
        CFAEdge edge = pair.getSecond();
        edges.add(edge);
      } else {
        ARTElement ae = pair.getFirst();
        CFANode loc = AbstractElements.extractLocation(ae);
        if (loc == loopHead) {
          begun = true;
          CFAEdge edge = pair.getSecond();
          edges.add(edge);
        }
      }
    }
    // build path formula for these edges
    PathFormula exitFormula = tpfb.buildPathFormula(edges);
    return exitFormula;
  }

  private Pair<PathFormula,PathFormula> buildExitFormulaHeadAndTail(Path pPath, CFANode loopHead) {
    // Like buildExitFormula method, only returns the formula for the exit path in two parts:
    // the "head", being the first edge, and the "tail", being the remainder of the path.
    CFAEdge headEdge = null;
    Vector<CFAEdge> tailEdges = new Vector<CFAEdge>();

    boolean begun = false;
    int N = pPath.size() - 1; // we ignore last pair, since last edge is useless, hence " - 1"
    for (int i = 0; i < N; i++) {
      Pair<ARTElement, CFAEdge> pair = pPath.get(i);
      if (begun) {
        CFAEdge edge = pair.getSecond();
        tailEdges.add(edge);
      } else {
        ARTElement ae = pair.getFirst();
        CFANode loc = AbstractElements.extractLocation(ae);
        if (loc == loopHead) {
          begun = true;
          CFAEdge edge = pair.getSecond();
          headEdge = edge;
        }
      }
    }
    // build path formula for these edges
    PathFormula headFormula = tpfb.buildPathFormula(headEdge);
    PathFormula tailFormula = tpfb.buildPathFormula(tailEdges);
    Pair<PathFormula,PathFormula> exitFormulae = Pair.<PathFormula,PathFormula>of(headFormula,tailFormula);
    return exitFormulae;
  }

  private TemplateChooser buildChooser(PathFormula pEntryFormula, PathFormula pLoopFormula, PathFormula pExitFormula,
      PathFormula pExitHead, PathFormula pExitTail) {
    // Pull the Formulas out of the PathFormulas, cast them into TemplateFormulas,
    // construct a TemplateChooser, and ask it to choose a template.
    TemplateFormula entryFormula = (TemplateFormula) pEntryFormula.getFormula();
    TemplateFormula loopFormula = (TemplateFormula) pLoopFormula.getFormula();
    TemplateFormula exitFormula = (TemplateFormula) pExitFormula.getFormula();
    TemplateFormula exitHead = (TemplateFormula) pExitHead.getFormula();
    TemplateFormula exitTail = (TemplateFormula) pExitTail.getFormula();
    TemplateChooser chooser = new SingleLoopTemplateChooser(entryFormula, loopFormula, exitFormula, exitHead, exitTail);
    return chooser;
  }

  /**
   * Build the Network object for a simple loop network, with root node R and loop head L.
   * @param pRoot the root node R.
   * @param pLoopHead the loop head node L.
   * @param pInvTemp the invariant template to put at L.
   * @param pEntryFormula the path formula from R to L.
   * @param pLoopFormula the path formula from L back to L.
   * @return
   */
  private TemplateNetwork buildSimpleLoopNetwork(CFANode pRoot, CFANode pLoopHead, CFANode pError,
      Template pInvTemp, PathFormula pEntryFormula, PathFormula pLoopFormula,
      PathFormula pExitFormula) {

    // Locations:
    Location root = new Location(pRoot);
    Location loopHead = new Location(pLoopHead);
    Location error = new Location(pError);

    // Template map:
    TemplateMap tmap = new TemplateMap();
    tmap.put(root, Template.makeTrueTemplate());
    tmap.put(loopHead, pInvTemp);
    tmap.put(error, Template.makeFalseTemplate());

    // Path formulas:
    TemplateFormula entryFormula = (TemplateFormula)pEntryFormula.getFormula();
    TemplateFormula loopFormula = (TemplateFormula)pLoopFormula.getFormula();
    TemplateFormula exitFormula = (TemplateFormula)pExitFormula.getFormula();

    // Transitions:
    Transition entryTrans = new Transition(tmap, root, entryFormula, loopHead);
    Transition loopTrans = new Transition(tmap, loopHead, loopFormula, loopHead);
    Transition exitTrans = new Transition(tmap, loopHead, exitFormula, error);

    // Construct and return program.
    TemplateNetwork tnet = new TemplateNetwork(tmap, entryTrans, loopTrans, exitTrans);
    return tnet;

  }

  public TemplateNetwork nextNetwork() {
    Template invTemp = chooser.chooseNextTemplate();

    logger.log(Level.ALL, "\nChosen invariant template for loop head:\n", invTemp);
    //diag:
    System.out.println("Chosen invariant template for loop head:");
    System.out.println(invTemp);
    //

    TemplateNetwork tnet = buildSimpleLoopNetwork(root, loopHead, error, invTemp, entryFormula, loopFormula, exitFormula);
    return tnet;
  }

}
