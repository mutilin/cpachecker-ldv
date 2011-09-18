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
package org.sosy_lab.cpachecker.util.invariants.templates;

import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

public class TemplateNumber extends TemplateFormula {

  private String S = null;
  private Integer I = null;

  public TemplateNumber(int n) {
    I = new Integer(n);
    S = I.toString();
  }

  public TemplateNumber(String s) {
    S = s;
    I = new Integer(s);
  }

  @Override
  public TemplateNumber copy() {
    TemplateNumber n = new TemplateNumber(new String(S));
    return n;
  }

  @Override
  public void negate() {
    if (S!=null) {
      if (S.startsWith("-")) {
        S = S.substring(1);
      } else {
        S = "-"+S;
      }
    } else {
      I = new Integer( -I.intValue() );
    }
  }

  public static TemplateNumber multiply(TemplateNumber n1,
                      TemplateNumber n2) {
    int a1 = n1.intValue();
    int a2 = n2.intValue();
    return new TemplateNumber(a1*a2);
  }

  public static TemplateNumber add(TemplateNumber n1,
      TemplateNumber n2) {
    int a1 = n1.intValue();
    int a2 = n2.intValue();
    return new TemplateNumber(a1+a2);
  }

  public boolean equals(TemplateNumber n) {
    int a1 = this.intValue();
    int a2 = n.intValue();
    return (a1==a2);
  }

  public int intValue() {
    if (I==null) {
      I = new Integer(S);
    }
    return I.intValue();
  }

  @Override
  public Formula translate(FormulaManager fmgr) {
  	return fmgr.makeNumber(S);
  }


  @Override
  public String toString() {
    return Integer.toString(intValue());
  }
  /*
  public String toString() {
    String s = null;
    if (S!=null) {
      s = S;
    } else {
      s = I.toString();
    }
    return s;
  }
  */

}