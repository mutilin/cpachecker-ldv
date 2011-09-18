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
package org.sosy_lab.cpachecker.util.invariants.redlog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;

public class ParameterManager {

  private final EAPair EAP;
  private String[] params = {};
  private HashMap<String,ParameterAssignment> PAmap = null;

  public ParameterManager(EAPair EAP) {
    this.EAP = EAP;
    PAmap = new HashMap<String, ParameterAssignment>();
  }

  public ParameterManager(EAPair EAP, String[] params) {
    this.EAP = EAP;
    PAmap = new HashMap<String, ParameterAssignment>();
    this.params = params;
    makePAs();
  }

  public ParameterManager(EAPair EAP, List<String> pParams) {
    this.EAP = EAP;
    PAmap = new HashMap<String, ParameterAssignment>();
    this.params = new String[pParams.size()];
    for (int i = 0; i < pParams.size(); i++) {
      this.params[i] = pParams.get(i);
    }
    makePAs();
  }

  public void setParameters(String[] params) {
    // List the names of the parameters for which we need to
    // determine values.
    this.params = params;
  }

  public ParameterAssignment getParameterAssignment(String param) {
    // For a single parameter name, we return the
    // ParameterAssignment object that it got.
    return PAmap.get(param);
  }

  public HashMap<String,Rational> getRationalValueMap() {
    // Returns a mapping from parameter names to (constant)
    // rational values, where those parameters that did not get a
    // constant will map to null.
    HashMap<String,Rational> map = new HashMap<String, Rational>();
    if (PAmap != null) {
      Rational R;
      String a;
      for (int i = 0; i < params.length; i++) {
        a = params[i];
        R = PAmap.get(a).getValue();
        map.put(a,R);
      }
    }
    return map;
  }

  public HashSet<String> getAllVars() {
    // Return a set of the names of all variables occurring in the
    // values of all parameters.
    HashSet<String> vars = new HashSet<String>();
    Iterator<ParameterAssignment> PAs = PAmap.values().iterator();
    ParameterAssignment PA;
    HashSet<String> pavars;
    while (PAs.hasNext()) {
      PA = PAs.next();
      pavars = PA.getVars();
      vars.addAll(pavars);
    }
    return vars;
  }

  public boolean allAreConstant() {
    // Say whether all the parameters got a constant value.
    boolean ans = true;
    if (PAmap == null) {
      ans = false;
    }

    int n = params.length;
    int i = 0;
    String a;
    ParameterAssignment PA;
    while (ans==true && i < n) {
      a = params[i];
      PA = PAmap.get(a);
      ans &= (PA.hasValue());
      i += 1;
    }

    return ans;
  }

  public void makePAs() {
    // Go through all equations in EAP, looking for those that
    // name any of our parameters on the LHS, and construct
    // ParameterAssignment objects accordingly (even for those
    // parameters that have no equation).
    // Store the results in the private global field PAmap.

    // Initialize the set of parameters.
    HashSet<String> waitlist = new HashSet<String>();
    for (int i = 0; i < params.length; i++) {
      waitlist.add(params[i]);
    }

    // Go through the equations in EAP.
    Iterator<Equation> eqnit = EAP.equationIterator();
    IASTExpression LHS, RHS;
    IASTIdExpression ID;
    Equation eqn;
    String a;
    ParameterAssignment PA;
    while (eqnit.hasNext()) {
      eqn = eqnit.next();
      LHS = eqn.getLeftHandSide();
      ID = (IASTIdExpression) LHS;
      a = ID.getName().toString();
      if (waitlist.contains(a)) {
        RHS = eqn.getRightHandSide();
        PA = new ParameterAssignment(a,RHS);
        PAmap.put(a,PA);
        waitlist.remove(a);
      }
    }

    // For any parameters that didn't have an equation, put a
    // dummy ParameterAssignment.
    // These will be of PAType NONE, but their Rational value will
    // not be null (it will be 0/1).
    Iterator<String> it = waitlist.iterator();
    String p;
    ParameterAssignment dummyPA;
    while (it.hasNext()) {
      p = it.next();
      dummyPA = new ParameterAssignment(p);
      PAmap.put(p,dummyPA);
    }
  }

}
