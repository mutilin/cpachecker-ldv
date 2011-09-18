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
package org.sosy_lab.cpachecker.util.invariants.balancer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.sosy_lab.cpachecker.util.invariants.templates.Purification;
import org.sosy_lab.cpachecker.util.invariants.templates.TemplateFormula;
import org.sosy_lab.cpachecker.util.invariants.templates.TemplateUIF;
import org.sosy_lab.cpachecker.util.invariants.templates.VariableWriteMode;

/**
 * Iterates through possible ordered subsets of axioms.
 */
public class UIFAxiomSet {

  Vector<UIFAxiom> axioms;

  int N;
  SubsetGenerator SG;
  PermutationGenerator PG;

  int k;
  Vector<UIFAxiom> axiom_subset;

  public UIFAxiomSet(Purification P, TemplateFormula[] F) {
    // Limit attention to those purification definitions for purification variables
    // that actually occur in any of the passed TemplateFormulas.
    // For example, F might give the pre, post, and trans formulas for a transition.

    // First get the set of all variables appearing in F
    Set<String> allVars = new HashSet<String>();
    for (int i = 0; i < F.length; i++) {
      allVars.addAll( F[i].getAllVariables(VariableWriteMode.PLAIN) );
    }

    // go through the value that 'name' maps to, searching for those
    // definitions that are of vars that are in allVars

    // take the resulting set, and form the set of all axioms out of it,
    // which means forming all unordered pairs, and turning them into actual
    // formulas that can be passed to redlog as UIF axioms.

    // add those axioms to the vector 'axioms'
    axioms = new Vector<UIFAxiom>();

    //iterate over function names known to the Purification P
    Collection<String> funcs = P.getFunctionNames();

    for (String name : funcs) {

      Vector<TemplateUIF> UIFs = new Vector<TemplateUIF>();
      //get set of fresh vars introduced for this function
      Set<String> varsForFunc = P.getVarsForFunction(name);
      for (String var : varsForFunc) {
        //check whether this var appears in the formulas we are working on
        if (allVars.contains(var)) {
          //if so, then add a new Term-UIF pair
          TemplateUIF U = P.getUIFByVarString(var);
          UIFs.add(U);
        }
      }

      //build axioms
      int m = UIFs.size();
      for (int i = 0; i < m-1; i++) {
        TemplateUIF U1 = UIFs.get(i);
        for (int j = i+1; j < m; j++) {
          TemplateUIF U2 = UIFs.get(j);
          UIFAxiom A = new UIFAxiom(U1,U2);
          axioms.add(A);
        }
      }

    }

    // Now let N be the size of the set of axioms.
    N = axioms.size();
    k = 0;
    // declare a subset generator for subsets of order k out of N
    SG = new SubsetGenerator(N,k);
    // we also need a permutation generator for k
    PG = new PermutationGenerator(k);

  }

  public boolean hasMore() {
    return ( k < N || SG.hasMore() || PG.hasMore() );
  }

  public Vector<UIFAxiom> getNext() {
    Vector<UIFAxiom> next = null;
    if (hasMore()) {

      if (!PG.hasMore()) {
        //we have done all the permutations of the current subset,
        //so we are done with this subset and should get the next one,
        //and start a new PG

        if (!SG.hasMore()) {
          //we have done all the subsets for the current k,
          //so we should increment k and make new SG
          k++;
          assert(k <= N);
          SG = new SubsetGenerator(N,k);
        }
        HashSet<Integer> index_subset = SG.getNext();

        //create the axiom subset
        axiom_subset = new Vector<UIFAxiom>();
        for (Integer I : index_subset) {
          axiom_subset.add( axioms.get(I.intValue()) );
        }
        PG = new PermutationGenerator(k);
      }

      int[] index_perm = PG.getNext();
      next = new Vector<UIFAxiom>();
      for (int i = 0; i < index_perm.length; i++) {
        next.add( axiom_subset.get( index_perm[i] ) );
      }

    }

    return next;
  }

}


















