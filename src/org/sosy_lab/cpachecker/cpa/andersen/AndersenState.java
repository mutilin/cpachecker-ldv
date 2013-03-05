/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.andersen;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.andersen.util.BaseConstraint;
import org.sosy_lab.cpachecker.cpa.andersen.util.ComplexConstraint;
import org.sosy_lab.cpachecker.cpa.andersen.util.DirectedGraph;
import org.sosy_lab.cpachecker.cpa.andersen.util.SimpleConstraint;

public class AndersenState implements AbstractState, Cloneable {

  // ------- global constraint system -------
  private static final Set<BaseConstraint> gBaseConstraints = new HashSet<>();
  private static final Set<SimpleConstraint> gSimpleConstraints = new HashSet<>();
  private static final Set<ComplexConstraint> gComplexConstraints = new HashSet<>();

  private static final Map<String, String[]> gPointsToSets = new HashMap<>();

  private static boolean gChanged = false;

  // ------- local constraint system -------
  private final Set<BaseConstraint> lBaseConstraints = new HashSet<>();
  private final Set<SimpleConstraint> lSimpleConstraints = new HashSet<>();
  private final Set<ComplexConstraint> lComplexConstraints = new HashSet<>();

  private final Map<String, String[]> lPointsToSets = new HashMap<>();

  private boolean lChanged = false;

  public AndersenState() {}

  /**
   * Add a (new) {@link BaseConstraint} to this element.
   *
   * @param constr {@link BaseConstraint} that should be added.
   */
  void addConstraint(BaseConstraint constr) {

    lChanged |= lBaseConstraints.add(constr);
    gChanged |= gBaseConstraints.add(constr);
  }

  /**
   * Add a (new) {@link SimpleConstraint} to this element.
   *
   * @param constr {@link SimpleConstraint} that should be added.
   */
  void addConstraint(SimpleConstraint constr) {

    lChanged |= lSimpleConstraints.add(constr);
    gChanged |= gSimpleConstraints.add(constr);
  }

  /**
   * Add a (new) {@link ComplexConstraint} to this element.
   *
   * @param constr {@link ComplexConstraint} that should be added.
   */
  void addConstraint(ComplexConstraint constr) {

    lChanged |= lComplexConstraints.add(constr);
    gChanged |= gComplexConstraints.add(constr);
  }

  /**
   * Computes and returns the points-to sets for the local constraint system.
   *
   * @return points-to sets for the local constraint system.
   */
  public Map<String, String[]> getLocalPointsToSets() {

    if (lChanged) {
      computeDynTransitiveClosure(lBaseConstraints, lSimpleConstraints, lComplexConstraints, lPointsToSets);
      lChanged = false;
    }

    return lPointsToSets;
  }

  /**
   * Computes and returns the points-to sets for the global constraint system.
   *
   * @return points-to sets for the global constraint system.
   */
  public static Map<String, String[]> getGlobalPointsToSets() {

    if (gChanged) {
      computeDynTransitiveClosure(gBaseConstraints, gSimpleConstraints, gComplexConstraints, gPointsToSets);
      gChanged = false;
    }

    return gPointsToSets;
  }

  /**
   * Computes the dynamic trasitive closure of the given constraint system and writes the resulting
   * points-to sets to the {@link Map} <code>ptSets</code>.
   *
   * @param bConstr
   *        {@link Set} of {@link BaseConstraint}s in the constraint system.
   * @param sConstr
   *        {@link Set} of {@link SimpleConstraint}s in the constraint system.
   * @param cConstr
   *        {@link Set} of {@link ComplexConstraint}s in the constraint system.
   * @param ptSets
   *        Writes all found points-to relations to this {@link Map}.<br>
   *        <i>Note:</i> The map is cleared, before the results are written.
   */
  private static void computeDynTransitiveClosure(Set<BaseConstraint> bConstr, Set<SimpleConstraint> sConstr,
      Set<ComplexConstraint> cConstr, Map<String, String[]> ptSets) {

    // build initial graph
    DirectedGraph g = new DirectedGraph();

    buildGraph(bConstr, sConstr, cConstr, g);

    HashSet<DirectedGraph.Node> workset = new HashSet<>();

    // add all nodes in graph to the initial workset
    for (Map.Entry<String, DirectedGraph.Node> entry : g.getNameMappings()) {
      workset.add(entry.getValue());
    }

    HashSet<DirectedGraph.Edge> tested = new HashSet<>();

    // dynamic transitive closure
    while (!workset.isEmpty()) {

      DirectedGraph.Node n = workset.iterator().next();
      workset.remove(n);
      if (!n.isValid()) {
        // node is invalid, if it was merged into another one
        continue;
      }

      // two lines for HCD
      if (n.mergePts != null) {
        g.mergeNodes(n.mergePts, n.getPointsToNodesSet());
      }

      for (DirectedGraph.Node v : n.getPointsToNodesSet()) {

        for (String aStr : n.complexConstrMeSub) {
          DirectedGraph.Node a = g.getNode(aStr);

          if (!v.isSuccessor(a)) {
            g.addEdge(v, a);
            workset.add(v);
          }
        }

        for (String bStr : n.complexConstrMeSuper) {
          DirectedGraph.Node b = g.getNode(bStr);

          if (!b.isSuccessor(v)) {
            g.addEdge(b, v);
            workset.add(b);
          }
        }

      } // for (String vStr : n.pointsToSet)

      for (DirectedGraph.Node z : n.getSuccessors()) {

        // LCD code
        DirectedGraph.Edge edge = g.new Edge(n, z);
        if (z.getPointsToSet().equals(n.getPointsToSet()) && !tested.contains(edge)) {
          tested.add(edge);
          DirectedGraph.Node merged = g.detectAndCollapseCycleContainingEdge(edge);

          if (merged != null) {
            workset.add(merged);
            break;
          }

        } else /* END LCD code */if (n.propagatePointerTargetsTo(z)) {
          workset.add(z);
        }
      }

    } // while (!workset.isEmpty())

    // clear result map
    ptSets.clear();

    // write results to map
    for (Map.Entry<String, DirectedGraph.Node> e : g.getNameMappings()) {

      Collection<String> ptSetNode = e.getValue().getPointsToSet();
      ptSets.put(e.getKey(), ptSetNode.toArray(new String[ptSetNode.size()]));
    }
  }

  /**
   * Constructs the online graph for the analysis. Additionally an offline graph for HCD is
   * constructed to speed up the computation of the dynamic transitive closure with it.
   *
   * @param bConstr
   *        List of all {@link BaseConstraint}s that should be considered.<br>
   *        A {@link BaseConstraint} leads to an entry in a nodes points-to set.
   * @param sConstr
   *        List of all {@link SimpleConstraint}s that should be considered.<br>
   *        A {@link SimpleConstraint} represents an edge in the graph.
   * @param cConstr
   *        List of all {@link ComplexConstraint}s that should be considered.<br>
   *        {@link ComplexConstraint}s are stored in nodes, so they can be accessed faster when
   *        computing the dynamic transitive closure.
   * @param g
   *        The resulting graph. Should be empty.
   */
  private static void buildGraph(Set<BaseConstraint> bConstr, Set<SimpleConstraint> sConstr,
      Set<ComplexConstraint> cConstr, DirectedGraph g) {

    // HCD offline - build offline graph
    List<List<String>> sccs = buildOfflineGraphAndFindSCCs(sConstr, cConstr);

    // build online graph
    for (BaseConstraint bc : bConstr) {

      DirectedGraph.Node n = g.getNode(bc.getSuperVar());
      n.addPointerTarget(bc.getSubVar());
    }

    for (SimpleConstraint sc : sConstr) {

      DirectedGraph.Node src = g.getNode(sc.getSubVar());
      DirectedGraph.Node dest = g.getNode(sc.getSuperVar());
      g.addEdge(src, dest);
    }

    for (ComplexConstraint cc : cConstr) {

      DirectedGraph.Node n;

      if (cc.isSubDerefed()) {

        n = g.getNode(cc.getSubVar());
        n.complexConstrMeSub.add(cc.getSuperVar());

      } else {

        n = g.getNode(cc.getSuperVar());
        n.complexConstrMeSuper.add(cc.getSubVar());

      }
    }

    // for HCD
    mergeOrMarkSCCs(g, sccs); // ... in online graph
  }

  /**
   * Builds an offline graph for HCD and finds all SCCs in it.<br>
   * For the offline version {@link BaseConstraint}s are not relevant and {@link SimpleConstraint}s
   * and {@link ComplexConstraint}s represents an edge in graph.
   *
   * @param sConstr
   *        List of all {@link SimpleConstraint}s that should be considered.
   * @param cConstr
   *        List of all {@link ComplexConstraint}s that should be considered.
   * @return a list of all SCCs. One SCC is represented as a list of all variables it contains.
   */
  private static List<List<String>> buildOfflineGraphAndFindSCCs(Collection<SimpleConstraint> sConstr,
      Collection<ComplexConstraint> cConstr) {

    HashSet<DirectedGraph.Node> workset = new HashSet<>();
    DirectedGraph g = new DirectedGraph();

    HashMap<DirectedGraph.Node, String> nodeStrMap = new HashMap<>();

    for (SimpleConstraint sc : sConstr) {

      String srcStr = sc.getSubVar();
      String destStr = sc.getSuperVar();

      DirectedGraph.Node src = g.getNode(srcStr);
      DirectedGraph.Node dest = g.getNode(destStr);
      g.addEdge(src, dest);

      workset.add(src);
      workset.add(dest);

      nodeStrMap.put(src, srcStr);
      nodeStrMap.put(dest, destStr);
    }

    for (ComplexConstraint cc : cConstr) {

      String srcStr, destStr;

      if (cc.isSubDerefed()) {

        srcStr = '*' + cc.getSubVar();
        destStr = cc.getSuperVar();

      } else {

        srcStr = cc.getSubVar();
        destStr = '*' + cc.getSuperVar();

      }

      DirectedGraph.Node src = g.getNode(srcStr);
      DirectedGraph.Node dest = g.getNode(destStr);
      g.addEdge(src, dest);

      workset.add(src);
      workset.add(dest);

      nodeStrMap.put(src, srcStr);
      nodeStrMap.put(dest, destStr);
    }

    // find strongly-connected components (using tarjans linear algorithm)
    int maxdfs = 1;
    LinkedList<DirectedGraph.Node> stack = new LinkedList<>();
    List<List<String>> sccs = new LinkedList<>();
    while (!workset.isEmpty()) {

      DirectedGraph.Node n = workset.iterator().next();

      maxdfs = tarjan(maxdfs, n, workset, stack, nodeStrMap, sccs);
    }

    return sccs;
  }

  /**
   * Recursive part of tarjans algorithm to find strongly connected components. Algorithm is e.g.
   * described in
   * <a href="http://en.wikipedia.org/wiki/Tarjan's_strongly_connected_components_algorithm">wikipedia</a>
   *
   * @param maxdfs
   *        The current value for maxdfs.
   * @param v
   *        The current Node to process.
   * @param workset
   *        Set of all unreached Nodes.
   * @param stack
   *        Temporary datastructure for algorithm.
   * @param nodeStrMap
   *        Because the sccs are returned as a list of variables (i.e. Strings), the mapping from
   *        Node to String must be given.
   * @param sccs
   *        All found strongly connected components are added to this list.
   * @return an updated value for maxdfs.
   */
  private static int tarjan(int maxdfs, DirectedGraph.Node v, Set<DirectedGraph.Node> workset,
      LinkedList<DirectedGraph.Node> stack, Map<DirectedGraph.Node, String> nodeStrMap, List<List<String>> sccs) {

    v.dfs = maxdfs;
    v.lowlink = maxdfs;
    maxdfs++;
    stack.push(v);
    workset.remove(v);

    for (DirectedGraph.Node succ : v.getSuccessors()) {
      if (workset.contains(succ)) {
        maxdfs = tarjan(maxdfs, succ, workset, stack, nodeStrMap, sccs);
        v.lowlink = Math.min(v.lowlink, succ.lowlink);
      } else if (succ.dfs > 0) { // <==> stack.contains(succ)
        v.lowlink = Math.min(v.lowlink, succ.dfs);
      }
    }

    if (v.lowlink == v.dfs) {

      DirectedGraph.Node succ;
      LinkedList<String> scc = new LinkedList<>();

      do {
        succ = stack.pop();
        succ.dfs = -succ.dfs;
        scc.add(nodeStrMap.get(succ));
      } while (!succ.equals(v));

      if (scc.size() > 1) {
        sccs.add(scc);
      }
    }

    return maxdfs;
  }

  /**
   * Merges all non-ref nodes in an SCC. For every ref-node the last remaining non-ref Node is
   * stored.
   *
   * @param g
   *        The (online) points-to graph for the analysis.
   * @param sccs
   *        List of all found SCCs in the offline version of the graph.
   */
  private static void mergeOrMarkSCCs(DirectedGraph g, List<List<String>> sccs) {

    for (List<String> scc : sccs) {

      LinkedList<DirectedGraph.Node> refNodes = new LinkedList<>();
      LinkedList<DirectedGraph.Node> normNodes = new LinkedList<>();

      for (String n : scc) {

        // translate to new node
        if (n.charAt(0) == '*') {
          refNodes.add(g.getNode(n.substring(1)));
        } else {
          normNodes.add(g.getNode(n));
        }
      }

      DirectedGraph.Node merged = g.mergeNodes(normNodes.poll(), normNodes);

      for (DirectedGraph.Node n : refNodes) {
        n.mergePts = merged;
      }
    }
  }

  @Override
  public boolean equals(Object other) {

    if (this == other) {
      return true;
    }

    if (other == null || !this.getClass().equals(other.getClass())) {
      return false;
    }

    AndersenState oEl = (AndersenState) other;

    return (this.lBaseConstraints.equals(oEl.lBaseConstraints)
        && this.lSimpleConstraints.equals(oEl.lSimpleConstraints)
        && this.lComplexConstraints.equals(oEl.lComplexConstraints));
  }

  @Override
  public AndersenState clone() {

    // super.clone() is not possible for final attributes... and the list interface also doesn't
    // provide a clone() method
    AndersenState clone = new AndersenState();

    clone.lBaseConstraints.addAll(this.lBaseConstraints);
    clone.lSimpleConstraints.addAll(this.lSimpleConstraints);
    clone.lComplexConstraints.addAll(this.lComplexConstraints);

    clone.lPointsToSets.putAll(this.lPointsToSets);

    clone.lChanged = this.lChanged;

    return clone;
  }

  @Override
  public int hashCode() {

    int hash = 51;
    hash = 31 * hash + this.lBaseConstraints.hashCode();
    hash = 31 * hash + this.lSimpleConstraints.hashCode();
    hash = 31 * hash + this.lComplexConstraints.hashCode();

    return hash;
  }

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder();
    sb.append('[').append('\n');

    for (BaseConstraint bc : this.lBaseConstraints) {
      sb.append('{').append(bc.getSubVar()).append("} \u2286 ");
      sb.append(bc.getSuperVar()).append('\n');
    }

    for (SimpleConstraint bc : this.lSimpleConstraints) {
      sb.append(bc.getSubVar()).append(" \u2286 ");
      sb.append(bc.getSuperVar()).append('\n');
    }

    for (ComplexConstraint bc : this.lComplexConstraints) {
      sb.append(bc.getSubVar()).append(" \u2286 ");
      sb.append(bc.getSuperVar()).append('\n');
    }

    int size = this.lBaseConstraints.size() + this.lSimpleConstraints.size() + this.lComplexConstraints.size();

    sb.append("] size->  ").append(size);

    // points-to sets
    sb.append('\n');
    sb.append('[').append('\n');

    Map<String, String[]> ptSet = getLocalPointsToSets();
    for (String key : ptSet.keySet()) {

      sb.append(key).append(" -> {");
      String[] vals = ptSet.get(key);

      for (String val : vals) {
        sb.append(val).append(',');
      }

      if (vals.length > 0) {
        sb.setLength(sb.length() - 1);
      }

      sb.append('}').append('\n');
    }

    sb.append(']').append('\n');

    return sb.toString();
  }
}
