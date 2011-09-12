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
package org.sosy_lab.cpachecker.cpa.art;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractElement;
import org.sosy_lab.cpachecker.util.AbstractElements;

import com.google.common.collect.Iterables;

/**
 * Helper class with collection of ART related utility methods.
 */
public class ARTUtils {

  private ARTUtils() { }

  /**
   * Get all elements on all paths from the ART root to a given element.
   *
   * @param pLastElement The last element in the paths.
   * @return A set of elements, all of which have pLastElement as their (transitive) child.
   */
  public static Set<ARTElement> getAllElementsOnPathsTo(ARTElement pLastElement) {

    Set<ARTElement> result = new HashSet<ARTElement>();
    Deque<ARTElement> waitList = new ArrayDeque<ARTElement>();

    result.add(pLastElement);
    waitList.add(pLastElement);

    while (!waitList.isEmpty()) {
      ARTElement currentElement = waitList.poll();
      for (ARTElement parent : currentElement.getParents()) {
        if (result.add(parent)) {
          waitList.push(parent);
        }
      }
    }

    return result;
  }

  /**
   * Create a path in the ART from root to the given element.
   * If there are several such paths, one is chosen randomly.
   *
   * @param pLastElement The last element in the path.
   * @return A path from root to lastElement.
   */
  public static Path getOnePathTo(ARTElement pLastElement) {
    Path path = new Path();
    Set<ARTElement> seenElements = new HashSet<ARTElement>();

    // each element of the path consists of the abstract element and the outgoing
    // edge to its successor

    ARTElement currentARTElement = pLastElement;
    assert pLastElement.isTarget();
    // add the error node and its -first- outgoing edge
    // that edge is not important so we pick the first even
    // if there are more outgoing edges
    CFANode loc = currentARTElement.retrieveLocationElement().getLocationNode();
    CFAEdge lastEdge = null;
    if (loc.getNumLeavingEdges() > 0) {
      lastEdge = loc.getLeavingEdge(0);
    }
    path.addFirst(Pair.of(currentARTElement, lastEdge));
    seenElements.add(currentARTElement);

    while (!currentARTElement.getParents().isEmpty()) {
      Iterator<ARTElement> parents = currentARTElement.getParents().iterator();

      ARTElement parentElement = parents.next();
      while (!seenElements.add(parentElement) && parents.hasNext()) {
        // while seenElements already contained parentElement, try next parent
        parentElement = parents.next();
      }

      CFAEdge edge = parentElement.getEdgeToChild(currentARTElement);
      path.addFirst(Pair.of(parentElement, edge));

      currentARTElement = parentElement;
    }
    return path;
  }

  /**
   * Create String with ART in the DOT format of Graphviz.
   * @param pReached the reached set
   * @param pathEdges the edges of the error path (may be empty)
   * @return the ART as DOT graph
   */
  static String convertARTToDot(ReachedSet pReached, Set<Pair<ARTElement, ARTElement>> pathEdges) {
    ARTElement firstElement = (ARTElement)pReached.getFirstElement();

    Deque<ARTElement> worklist = new LinkedList<ARTElement>();
    Set<Integer> nodesList = new HashSet<Integer>();
    Set<ARTElement> processed = new HashSet<ARTElement>();
    StringBuilder sb = new StringBuilder();
    StringBuilder edges = new StringBuilder();

    sb.append("digraph ART {\n");
    sb.append("style=filled; fontsize=10.0; fontname=\"Courier New\"; \n");

    worklist.add(firstElement);

    while(worklist.size() != 0){
      ARTElement currentElement = worklist.removeLast();
      if(processed.contains(currentElement)){
        continue;
      }
      processed.add(currentElement);
      if(!nodesList.contains(currentElement.getElementId())){
        String color;
        if (currentElement.isCovered()) {
          color = "green";
        } else if (currentElement.isTarget()) {
          color = "red";
        } else {
          PredicateAbstractElement abselem = AbstractElements.extractElementByType(currentElement, PredicateAbstractElement.class);
          if (abselem != null && abselem.isAbstractionElement()) {
            color = "blue";
          } else {
            color = "white";
          }
        }

        CFANode loc = currentElement.retrieveLocationElement().getLocationNode();
        String label = (loc==null ? 0 : loc.getNodeNumber()) + "000" + currentElement.getElementId();

        sb.append("node [shape = diamond, color = " + color + ", style = filled, label=" + label +" id=\"" + currentElement.getElementId() + "\"] " + currentElement.getElementId() + ";\n");

        nodesList.add(currentElement.getElementId());
      }

      for (ARTElement covered : currentElement.getCoveredByThis()) {
        edges.append(covered.getElementId());
        edges.append(" -> ");
        edges.append(currentElement.getElementId());
        edges.append(" [style = dashed, label = \"covered by\"];\n");
      }

      for (ARTElement child : currentElement.getChildren()) {
        boolean colored = pathEdges.contains(Pair.of(currentElement, child));
        CFAEdge edge = currentElement.getEdgeToChild(child);
        edges.append(currentElement.getElementId());
        edges.append(" -> ");
        edges.append(child.getElementId());
        edges.append(" [");
        if (colored) {
          edges.append("color = red");
        }
        if (edge != null) {
          edges.append(" label = \"");
          edges.append(edge.toString().replace('"', '\''));
          edges.append("\"");
          edges.append(" id=\"");
          edges.append(currentElement.getElementId());
          edges.append("->");
          edges.append(child.getElementId());
          edges.append("\"");
        }
        edges.append("];\n");
        if(!worklist.contains(child)){
          worklist.add(child);
        }
      }
    }
    sb.append(edges);
    sb.append("}\n");
    return sb.toString();
  }

  /**
   * Find a path in the ART. The necessary information to find the path is a
   * boolean value for each branching situation that indicates which of the two
   * AssumeEdges should be taken.
   *
   * @param root The root element of the ART (where to start the path)
   * @param art All elements in the ART or a subset thereof (elements outside this set will be ignored).
   * @param branchingInformation A map from ART element ids to boolean values indicating the outgoing direction.
   * @return A path through the ART from root to target.
   * @throws IllegalArgumentException If the direction information doesn't match the ART or the ART is inconsistent.
   */
  public static Path getPathFromBranchingInformation(
      ARTElement root, Collection<? extends AbstractElement> art,
      Map<Integer, Boolean> branchingInformation) throws IllegalArgumentException {

    checkArgument(art.contains(root));

    Path result = new Path();
    ARTElement currentElement = root;
    while (!currentElement.isTarget()) {
      Set<ARTElement> children = currentElement.getChildren();

      ARTElement child;
      CFAEdge edge;
      switch (children.size()) {

      case 0:
        throw new IllegalArgumentException("ART target path terminates without reaching target element!");

      case 1: // only one successor, easy
        child = Iterables.getOnlyElement(children);
        edge = currentElement.getEdgeToChild(child);
        break;

      case 2: // branch
        // first, find out the edges and the children
        CFAEdge trueEdge = null;
        CFAEdge falseEdge = null;
        ARTElement trueChild = null;
        ARTElement falseChild = null;

        for (ARTElement currentChild : children) {
          CFAEdge currentEdge = currentElement.getEdgeToChild(currentChild);
          if (!(currentEdge instanceof AssumeEdge)) {
            throw new IllegalArgumentException("ART branches where there is no AssumeEdge!");
          }

          if (((AssumeEdge)currentEdge).getTruthAssumption()) {
            trueEdge = currentEdge;
            trueChild = currentChild;
          } else {
            falseEdge = currentEdge;
            falseChild = currentChild;
          }
        }
        if (trueEdge == null || falseEdge == null) {
          throw new IllegalArgumentException("ART branches with non-complementary AssumeEdges!");
        }
        assert trueChild != null;
        assert falseChild != null;

        // search first idx where we have a predicate for the current branching
        Boolean predValue = branchingInformation.get(currentElement.getElementId());
        if (predValue == null) {
          throw new IllegalArgumentException("ART branches without direction information!");
        }

        // now select the right edge
        if (predValue) {
          edge = trueEdge;
          child = trueChild;
        } else {
          edge = falseEdge;
          child = falseChild;
        }
        break;

      default:
        throw new IllegalArgumentException("ART splits with more than two branches!");
      }

      if (!art.contains(child)) {
        throw new IllegalArgumentException("ART and direction information from solver disagree!");
      }

      result.add(Pair.of(currentElement, edge));
      currentElement = child;
    }


    // need to add another pair with target element and one (arbitrary) outgoing edge
    CFANode loc = currentElement.retrieveLocationElement().getLocationNode();
    CFAEdge lastEdge = null;
    if (loc.getNumLeavingEdges() > 0) {
      lastEdge = loc.getLeavingEdge(0);
    }
    result.add(Pair.of(currentElement, lastEdge));

    return result;
  }

  /**
   * Find a path in the ART. The necessary information to find the path is a
   * boolean value for each branching situation that indicates which of the two
   * AssumeEdges should be taken.
   * This method checks that the path ends in a certain element.
   *
   * @param root The root element of the ART (where to start the path)
   * @param target The target element (where to end the path, needs to be a target element)
   * @param art All elements in the ART or a subset thereof (elements outside this set will be ignored).
   * @param branchingInformation A map from ART element ids to boolean values indicating the outgoing direction.
   * @return A path through the ART from root to target.
   * @throws IllegalArgumentException If the direction information doesn't match the ART or the ART is inconsistent.
   */
  public static Path getPathFromBranchingInformation(
      ARTElement root, ARTElement target, Collection<? extends AbstractElement> art,
      Map<Integer, Boolean> branchingInformation) throws IllegalArgumentException {

    checkArgument(art.contains(target));
    checkArgument(target.isTarget());

    Path result = getPathFromBranchingInformation(root, art, branchingInformation);

    if (result.getLast().getFirst() != target) {
      throw new IllegalArgumentException("ART target path reached the wrong target element!");
    }

    return result;
  }
}
