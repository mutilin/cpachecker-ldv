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
package org.sosy_lab.cpachecker.util.invariants.templates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.sosy_lab.cpachecker.util.invariants.Rational;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;

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
      for (TemplateSum sum : sums) {
        sum.alias(amap);
      }
    }
  }

  public void unalias() {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.unalias();
      }
    }
  }

  public boolean evaluate(Map<String, Rational> map) {
    boolean ans = true;
    if (sums != null) {
      for (TemplateSum sum : sums) {
        ans &= sum.evaluate(map);
      }
    }
    return ans;
  }

  public void unevaluate() {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.unevaluate();
      }
    }
  }

  public void postindex(Map<String, Integer> indices) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.postindex(indices);
      }
    }
  }

  public void preindex(Map<String, Integer> indices) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.preindex(indices);
      }
    }
  }

  public void unindex() {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.unindex();
      }
    }
  }

  public Purification purify(Purification pur) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        pur = sum.purify(pur);
      }
    }
    return pur;
  }

  public void unpurify() {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.unpurify();
      }
    }
  }

  public void generalize() {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.generalize();
      }
    }
  }

//------------------------------------------------------------------
// Other cascade methods

  public Set<TemplateVariable> getAllVariables() {
    HashSet<TemplateVariable> vars = new HashSet<>();
    if (sums != null) {
      for (TemplateSum sum : sums) {
        vars.addAll(sum.getAllVariables());
      }
    }
    return vars;
  }

  public Set<TemplateVariable> getAllParameters() {
    HashSet<TemplateVariable> params = new HashSet<>();
    if (sums != null) {
      for (TemplateSum sum : sums) {
        params.addAll(sum.getAllParameters());
      }
    }
    return params;
  }

  public HashMap<String, Integer> getMaxIndices(HashMap<String, Integer> map) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        map = sum.getMaxIndices(map);
      }
    }
    return map;
  }

  public TemplateVariableManager getVariableManager() {
    TemplateVariableManager tvm = new TemplateVariableManager();
    if (sums != null) {
      for (TemplateSum sum : sums) {
        tvm.merge(sum.getVariableManager());
      }
    }
    return tvm;
  }

  public void prefixVariables(String prefix) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.prefixVariables(prefix);
      }
    }
  }

//------------------------------------------------------------------
// Other

  public int size() {
    return sums.length;
  }

  public Iterator<TemplateSum> iterator() {
    Vector<TemplateSum> V = new Vector<>();
    for (TemplateSum sum : sums) {
      V.add(sum);
    }
    return V.iterator();
  }

  void writeAsForm(boolean b) {
    if (sums != null) {
      for (TemplateSum sum : sums) {
        sum.writeAsForm(b);
      }
    }
  }

  public String toString(String delim) {
    return toString(delim, VariableWriteMode.PLAIN);
  }

  public String toString(String delim, VariableWriteMode vwm) {
    String s = "";
    if (sums != null) {
      for (TemplateSum sum : sums) {
        s += delim;
        s += sum.toString(vwm);
      }
    }
    if (s.length() > 0) {
      s = s.substring(delim.length());
    }
    return s;
  }

}
