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
package org.sosy_lab.cpachecker.core.counterexample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.sosy_lab.common.JSON;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.ConcerteStatePathNode;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.MultiConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.ConcreteStatePath.SingleConcreteState;
import org.sosy_lab.cpachecker.core.counterexample.Model.AssignableTerm;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;


/**
 * This class represents a path of cfaEdges, that contain the additional Information
 * at which edge which assignableTerm was created when this path was checked by
 * the class {@link PathChecker}.
 *
 */
public class CFAPathWithAssignments implements Iterable<CFAEdgeWithAssignments> {

  private final List<CFAEdgeWithAssignments> pathWithAssignments;

  //TODO Erase this
  private final Multimap<CFAEdge, AssignableTerm> allAssignableTerms;

  private CFAPathWithAssignments(
      List<CFAEdgeWithAssignments> pPathWithAssignments) {
    pathWithAssignments = ImmutableList.copyOf(pPathWithAssignments);
    allAssignableTerms = ImmutableListMultimap.of();
  }

  public CFAPathWithAssignments() {
    pathWithAssignments = ImmutableList.of();
    allAssignableTerms = ImmutableListMultimap.of();
  }


  private CFAPathWithAssignments(List<CFAEdgeWithAssignments> pPathWithAssignments,
      Multimap<CFAEdge, AssignableTerm> pUsedAssignableTerms) {
    pathWithAssignments = ImmutableList.copyOf(pPathWithAssignments);
    allAssignableTerms = pUsedAssignableTerms;
  }

  @Nullable
  public CFAPathWithAssignments getExactVariableValues(List<CFAEdge> pPath) {

    if (fitsPath(pPath)) {
      return this;
    }

    int index = pathWithAssignments.size() - pPath.size();

    if (index < 0) {
      return null;
    }

    List<CFAEdgeWithAssignments> result;

    result = new ArrayList<>(pPath.size());

    for (CFAEdge edge : pPath) {

      if (index > pathWithAssignments.size()) {
        return null;
      }

      CFAEdgeWithAssignments cfaWithAssignment = pathWithAssignments.get(index);

      if (!edge.equals(cfaWithAssignment.getCFAEdge())) {
        return null;
      }

      result.add(cfaWithAssignment);
      index++;
    }

    return new CFAPathWithAssignments(result);
  }

  private boolean fitsPath(List<CFAEdge> pPath) {

    if (pPath.size() != pathWithAssignments.size()) {
      return false;
    }

    int index = 0;

    for (CFAEdge edge : pPath) {

      CFAEdgeWithAssignments cfaWithAssignment = pathWithAssignments.get(index);

      if (!edge.equals(cfaWithAssignment.getCFAEdge())) {
        return false;
      }

      index++;
    }

    return true;
  }

  @Nullable
  public Map<ARGState, CFAEdgeWithAssignments> getExactVariableValues(ARGPath pPath) {


    if (pPath.size() != (pathWithAssignments.size() + 1)) {
      return null;
    }

    Map<ARGState, CFAEdgeWithAssignments> result = new HashMap<>();

    PathIterator pathIterator = pPath.pathIterator();
    while (pathIterator.hasNext()) {

      CFAEdgeWithAssignments edgeWithAssignment = pathWithAssignments.get(pathIterator.getIndex());
      if (edgeWithAssignment.getCFAEdge().equals(pathIterator.getOutgoingEdge())) {
        // path is not equivalent
        return null;
      }

      result.put(pathIterator.getAbstractState(), edgeWithAssignment);
      pathIterator.advance();
    }
    // last state is ignored

    return result;
  }

  public static CFAPathWithAssignments of(ConcreteStatePath statePath,
      LogManager pLogger, MachineModel pMachineModel) {

    List<CFAEdgeWithAssignments> result = new ArrayList<>(statePath.size());

    for (ConcerteStatePathNode node : statePath) {
      if (node instanceof SingleConcreteState) {

        SingleConcreteState singleState = (SingleConcreteState) node;
        CFAEdgeWithAssignments edge = createCFAEdgeWithAssignment(singleState, pLogger, pMachineModel);
        result.add(edge);
      } else {
        MultiConcreteState multiState = (MultiConcreteState) node;
        CFAEdgeWithAssignments edge = createCFAEdgeWithAssignment(multiState, pLogger, pMachineModel);
        result.add(edge);
      }
    }

    return new CFAPathWithAssignments(result);
  }

  @Deprecated
  public static CFAPathWithAssignments valueOf(ConcreteStatePath statePath,
      LogManager pLogger, MachineModel pMachineModel,
      Multimap<CFAEdge, AssignableTerm> usedAssignableTerms) {

    List<CFAEdgeWithAssignments> result = new ArrayList<>(statePath.size());

    for (ConcerteStatePathNode node : statePath) {
      if (node instanceof SingleConcreteState) {

        SingleConcreteState singleState = (SingleConcreteState) node;
        CFAEdgeWithAssignments edge = createCFAEdgeWithAssignment(singleState, pLogger, pMachineModel);
        result.add(edge);
      } else {
        MultiConcreteState multiState = (MultiConcreteState) node;
        CFAEdgeWithAssignments edge = createCFAEdgeWithAssignment(multiState, pLogger, pMachineModel);
        result.add(edge);
      }
    }

    return new CFAPathWithAssignments(result, usedAssignableTerms);
  }

  private static CFAEdgeWithAssignments createCFAEdgeWithAssignment(MultiConcreteState state,
      LogManager pLogger, MachineModel pMachineModel) {

    MultiEdge cfaEdge = state.getCfaEdge();
    List<CFAEdgeWithAssignments> pEdges = new ArrayList<>(cfaEdge.getEdges().size());

    for (SingleConcreteState node : state) {
      pEdges.add(createCFAEdgeWithAssignment(node, pLogger, pMachineModel));
    }

    CFAMultiEdgeWithAssignments edge = CFAMultiEdgeWithAssignments.valueOf(cfaEdge, pEdges);
    return edge;
  }

  private static CFAEdgeWithAssignments createCFAEdgeWithAssignment(
      SingleConcreteState state, LogManager pLogger, MachineModel pMachineModel) {

    CFAEdge cfaEdge = state.getCfaEdge();
    ConcreteState concreteState = state.getConcreteState();
    AssignmentToEdgeAllocator allocator = new AssignmentToEdgeAllocator(pLogger, cfaEdge, concreteState, pMachineModel);

    return allocator.allocateAssignmentsToEdge();
  }

  public boolean isEmpty() {
    return pathWithAssignments.isEmpty();
  }

  @Override
  public String toString() {
    return pathWithAssignments.toString();
  }

  public CFAEdge getCFAEdgeAtPosition(int index) {
    return pathWithAssignments.get(index).getCFAEdge();
  }

  public int size() {
    return pathWithAssignments.size();
  }

  @Override
  public Iterator<CFAEdgeWithAssignments> iterator() {
    return pathWithAssignments.iterator();
  }

  @Deprecated
  public Collection<AssignableTerm> getAllAssignedTerms(CFAEdge pEdge) {
    return allAssignableTerms.get(pEdge);
  }

  public void toJSON(Appendable sb, ARGPath argPath) throws IOException {
    List<Map<?, ?>> path = new ArrayList<>(this.size());


    if( argPath.size() != pathWithAssignments.size()) {
      argPath.toJSON(sb);
      return;
    }

    int index = 0;

    for (Pair<ARGState, CFAEdge> pair : Pair.zipWithPadding(argPath.asStatesList(), argPath.asEdgesList())) {

      Map<String, Object> elem = new HashMap<>();
      CFAEdgeWithAssignments edgeWithAssignment = pathWithAssignments.get(index);
      ARGState argelem = pair.getFirst();
      CFAEdge edge = pair.getSecond();

      if (edge == null) {
        continue; // in this case we do not need the edge
      }

      elem.put("argelem", argelem.getStateId());
      elem.put("source", edge.getPredecessor().getNodeNumber());
      elem.put("target", edge.getSuccessor().getNodeNumber());
      elem.put("desc", edge.getDescription().replaceAll("\n", " "));
      elem.put("val", edgeWithAssignment.prettyPrint());
      elem.put("line", edge.getFileLocation().getStartingLineNumber());
      elem.put("file", edge.getFileLocation().getFileName());
      path.add(elem);
      index++;
    }

    JSON.writeJSONString(path, sb);
  }
}