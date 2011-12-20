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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class Monomial {

  private Map<Variable,Integer> vars = new HashMap<Variable,Integer>();

  public Monomial() {
    vars = new HashMap<Variable,Integer>();
  }

  public Monomial(Map<Variable,Integer> v) {
    vars = v;
  }

  public Monomial(Variable v) {
    vars = new HashMap<Variable,Integer>();
    vars.put(v, new Integer(1));
  }

  public boolean divides(Term t) {
    return this.divides(t.getMonomial());
  }

  public boolean divides(Monomial m) {
    boolean answer = true;
    for (Variable v : vars.keySet()) {
      if (!m.vars.containsKey(v)) {
        answer = false;
        break;
      }
      else {
        Integer e = vars.get(v);
        Integer f = m.vars.get(v);
        if (f.compareTo(e) < 0) {
          answer = false;
          break;
        }
      }
    }
    return answer;
  }

  /*
   * Create the result of dividing m by n.
   */
  public static Monomial divide(Monomial m, Monomial n) {
    Map<Variable,Integer> a = m.vars;
    Map<Variable,Integer> b = n.vars;
    Map<Variable,Integer> d = new HashMap<Variable,Integer>();
    // First copy a.
    for (Variable v : a.keySet()) {
      d.put(v, a.get(v));
    }
    // Now divide by b.
    for (Variable v : b.keySet()) {
      int ea;
      if (a.containsKey(v)) {
        ea = a.get(v).intValue();
      } else {
        ea = 0;
      }
      d.put( v, new Integer( ea - b.get(v).intValue() ) );
    }
    return new Monomial(d);
  }

  public static Monomial gcd(Monomial... ma) {
    List<Monomial> ml = new Vector<Monomial>(ma.length);
    for (Monomial m : ma) {
      ml.add(m);
    }
    return gcd(ml);
  }

  public static Monomial gcd(List<Monomial> mlist) {
    int N = mlist.size();
    // If list is length zero, return the Monomial 1, a dummy response.
    if (N == 0) {
      return new Monomial();
    }
    // If exactly 1, then it is its own gcd.
    else if (N == 1) {
      return mlist.get(0);
    }
    // If more than 2, then "divide and conquer".
    else if (N > 2) {
      int L = N/2;
      Monomial a = gcd(mlist.subList(0, L));
      Monomial b = gcd(mlist.subList(L, N));
      Monomial d = gcd(a,b);
      return d;
    }
    // If exactly 2:
    else {
      Map<Variable,Integer> a = mlist.get(0).vars;
      Map<Variable,Integer> b = mlist.get(1).vars;
      Map<Variable,Integer> d = new HashMap<Variable,Integer>();
      for (Variable v : a.keySet()) {
        if (b.containsKey(v)) {
          Integer ea = a.get(v);
          Integer eb = b.get(v);
          Integer min = (ea.compareTo(eb) < 0 ? ea : eb);
          d.put(v, min);
        }
      }
      return new Monomial(d);
    }
  }

  /*
   * This method multiplies monomials, by merging the maps that represent them.
   */
  public static Monomial multiply(Monomial m1, Monomial m2) {
    Monomial m3 = new Monomial();
    for (Variable v : m1.vars.keySet()) {
      m3.vars.put(v, m1.vars.get(v));
    }
    for (Variable v : m2.vars.keySet()) {
      if (m3.vars.containsKey(v)) {
        int p1 = m3.vars.get(v).intValue();
        int p2 = m2.vars.get(v).intValue();
        m3.vars.put(v, new Integer(p1+p2));
      } else {
        m3.vars.put(v, m2.vars.get(v));
      }
    }
    return m3;
  }


  public boolean isConstant() {
    // To be constant is to have no variables, or to have all exponents 0.
    if (vars.keySet().size() == 0) {
      // then we have no variables.
      return true;
    }
    Collection<Integer> exps = vars.values();
    for (Integer e : exps) {
      if (e.intValue() != 0) {
        return false;
      }
    }
    return true;
  }

  public void setMonomial(Map<Variable,Integer> m) {
    vars = m;
  }

  public Map<Variable,Integer> getMonomial() {
    return vars;
  }

  public void setPower(Variable v, int n) {
    vars.put(v, new Integer(n));
  }

  @Override
  public String toString() {
    return toString(true);
  }

  public String toString(boolean sortAlpha) {
    String s = "";
    List<Variable> varlist = new Vector<Variable>(vars.keySet());
    if (sortAlpha) {
      Collections.sort(varlist);
    }
    for (Variable v : varlist) {
      Integer pow = vars.get(v);
      int p = pow.intValue();
      // If power is zero, then don't write anything.
      if (p == 0) {
        continue;
      }
      // Else, write variable.
      String t = v.toString();
      // Write power, and parenthesize, if power not unity.
      if (p != 1) {
        t = "("+t+"^"+pow.toString()+")";
      }
      s += t;
    }
    if (s.length() == 0) {
      s = "1";
    }
    return s;
  }


}
