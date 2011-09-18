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
 *  Unless required by applicable law or agreed to in writing,
 *  software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.
 *  See the License for the specific language governing permissions
 *  and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util.invariants.templates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.sosy_lab.cpachecker.util.invariants.redlog.Rational;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaList;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

public class TemplateSumList extends TemplateFormulaList {

  private TemplateSum[] sums = null;

  public TemplateSumList(TemplateSum[] sums) {
    this.sums = sums;
  }

  public TemplateSumList(TemplateFormulaList TF) throws ClassCastException {
    // Attempts to cast each formula in TF as a TemplateSum, throwing
    // a ClassCastException if this ever fails.
    Formula[] F = TF.getFormulas();
    sums = new TemplateSum[F.length];
    for (int i = 0; i < F.length; i++) {
      sums[i] = (TemplateSum) F[i];
    }
  }

  public TemplateSumList copy() {
    TemplateSumList s = null;
    if (sums == null) {
      s = new TemplateSumList(new TemplateSum[0]);
    } else {
      TemplateSum[] sa = new TemplateSum[sums.length];
      for (int i = 0; i < sums.length; i++) {
        sa[i] = sums[i].copy();
      }
      s = new TemplateSumList(sa);
    }
    return s;
  }

//------------------------------------------------------------------
// Alter and Undo

  public void alias(AliasingMap amap) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].alias(amap);
      }
    }
  }

  public void unalias() {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].unalias();
      }
    }
  }

  public boolean evaluate(HashMap<String,Rational> map) {
    boolean ans = true;
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        ans &= sums[i].evaluate(map);
      }
    }
    return ans;
  }

  public void unevaluate() {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].unevaluate();
      }
    }
  }

  public void postindex(Map<String,Integer> indices) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].postindex(indices);
      }
    }
  }

  public void preindex(Map<String,Integer> indices) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].preindex(indices);
      }
    }
  }

  public void unindex() {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].unindex();
      }
    }
  }

  public Purification purify(Purification pur) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        pur = sums[i].purify(pur);
      }
    }
    return pur;
  }

  public void unpurify() {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].unpurify();
      }
    }
  }

  public void generalize() {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].generalize();
      }
    }
  }

//------------------------------------------------------------------
// Other cascade methods

  public Set<String> getAllVariables(VariableWriteMode vwm) {
    HashSet<String> vars = new HashSet<String>();
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        vars.addAll(sums[i].getAllVariables(vwm));
      }
    }
    return vars;
  }

  public Set<TemplateVariable> getAllParameters() {
    HashSet<TemplateVariable> params = new HashSet<TemplateVariable>();
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        params.addAll(sums[i].getAllParameters());
      }
    }
    return params;
  }

  public HashMap<String,Integer> getMaxIndices(HashMap<String,Integer> map) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        map = sums[i].getMaxIndices(map);
      }
    }
    return map;
  }

  public TemplateVariableManager getVariableManager() {
    TemplateVariableManager tvm = new TemplateVariableManager();
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        tvm.merge( sums[i].getVariableManager() );
      }
    }
    return tvm;
  }

  public void prefixVariables(String prefix) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].prefixVariables(prefix);
      }
    }
  }

  public FormulaList translate(FormulaManager fmgr) {
  	List<Formula> lf = new Vector<Formula>(sums.length);
  	for (int i = 0; i < sums.length; i++) {
  		lf.add( sums[i].translate(fmgr) );
  	}
  	return fmgr.makeList(lf);
  }

//------------------------------------------------------------------
// Other

  public Iterator<TemplateSum> iterator() {
    Vector<TemplateSum> V = new Vector<TemplateSum>();
    for (int i = 0; i < sums.length; i++) {
      V.add(sums[i]);
    }
    return V.iterator();
  }

  void writeAsForm(boolean b) {
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        sums[i].writeAsForm(b);
      }
    }
  }

  public String toString(String delim) {
    return toString(delim, VariableWriteMode.PLAIN);
  }

  public String toString(String delim, VariableWriteMode vwm) {
    String s = "";
    if (sums != null) {
      for (int i = 0; i < sums.length; i++) {
        s += delim;
        s += sums[i].toString(vwm);
      }
    }
    if (s.length() > 0) {
      s = s.substring(delim.length());
    }
    return s;
  }

}
