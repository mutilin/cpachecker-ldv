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
package org.sosy_lab.cpachecker.util.predicates.princess;

import ap.basetypes.IdealInt;
import ap.parser.IAtom;
import ap.parser.IBinFormula;
import ap.parser.IBinJunctor;
import ap.parser.IBoolLit;
import ap.parser.IConstant;
import ap.parser.IExpression;
import ap.parser.IFormula;
import ap.parser.IFormulaITE;
import ap.parser.IFunApp;
import ap.parser.IIntLit;
import ap.parser.INot;
import ap.parser.ITerm;
import ap.parser.ITermITE;
import scala.Enumeration;
import scala.collection.Iterator;
import scala.collection.JavaConversions;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** This is a Class similiar to Mathsat-NativeApi,
 *  it contains some useful static functions. */
class PrincessUtil {
  private PrincessUtil() { }

  /** ITerm is the arithmetic subclass of IExpression. */
  public static ITerm castToTerm(IExpression e) {
    return (ITerm) e;
  }

  /** IFormula is the boolean subclass of IExpression. */
  public static IFormula castToFormula(IExpression e) {
    return (IFormula) e;
  }

  /** A Term is an Atom, iff its function is no element of {"And", "Or", "Not"}.*/
  public static boolean isAtom(IExpression t) {
    boolean is = !isAnd(t) && !isOr(t) && !isNot(t) && !isImplication(t) && !isIfThenElse(t);
    assert is || isBoolean(t);
    return is;
  }

  public static boolean isVariable(IExpression t) {
    return t instanceof IAtom || t instanceof IConstant;
    // wrong for ints: return !isTrue(t) && !isFalse(t) && t.length() == 0;
  }

  public static boolean isUIF(IExpression t) {
    return (t instanceof IFunApp);
  }

  /** check for ConstantTerm with Number or
   * ApplicationTerm with negative Number */
  public static boolean isNumber(IExpression t) {
    return t instanceof IIntLit;
    // todo negative Number --> "-123"
  }

  /** converts a term to a number,
   * currently only Double is supported. */
  public static double toNumber(IExpression t) {
    assert isNumber(t) : "term is not a number: " + t;

    // ConstantTerm with Number --> "123"
    if (t instanceof IIntLit) {
      IdealInt value = ((IIntLit) t).value();
      return value.longValue();
      // todo negative Number --> "-123"
    }

    throw new NumberFormatException("unknown format of numeric term: " + t);
  }

  public static boolean isBoolean(IExpression t) {
    return t instanceof IFormula;
  }

  /** t1 and t2 */
  public static boolean isAnd(IExpression t) {
    return isBinaryFunction(t, IBinJunctor.And());
  }

  /** t1 or t2 */
  public static boolean isOr(IExpression t) {
    return isBinaryFunction(t, IBinJunctor.Or());
  }

  /** not t */
  public static boolean isNot(IExpression t) {
    return t instanceof INot;
  }

  /** t1 => t2 */
  public static boolean isImplication(IExpression t) {
    // Princess does not support implication.
    // Formulas are converted from "a=>b" to "!a||b".
    return false;
  }

  /** t1 or t2 */
  public static boolean isXor(IExpression t) {
    // Princess does not support Xor.
    // Formulas are converted from "a^b" to "!(a<=>b)".
    return false;
  }

  /** (ite t1 t2 t3) */
  public static boolean isIfThenElse(IExpression t) {
    return t instanceof IFormulaITE // boolean args
        || t instanceof ITermITE;   // arithmetic args
  }

  /** t1 = t2 */
  public static boolean isEqual(IExpression t) {
    return isBinaryFunction(t, IBinJunctor.Eqv());
  }

  private static boolean isBinaryFunction(IExpression t, Enumeration.Value val) {
    return (t instanceof IBinFormula)
            && val == ((IBinFormula) t).j(); // j is the operator and Scala is evil!
  }

  public static int getArity(IExpression t) {
    return t.length();
  }

  public static IExpression getArg(IExpression t, int i) {
    assert i < getArity(t) : "index out of bounds";
    return t.apply(i);
    /*
    if (t instanceof IBinFormula) {
      return ((IBinFormula) t).apply(i);
    } else {
      return null;
    }
     */
  }

  public static boolean isTrue(IExpression t) {
    return t instanceof IBoolLit && ((IBoolLit)t).value();
  }

  public static boolean isFalse(IExpression t) {
    return t instanceof IBoolLit && !((IBoolLit)t).value();
  }

  /** this function creates a new Term with the same function and new parameters. */
  public static IExpression replaceArgs(PrincessEnvironment env, IExpression t, List<IExpression> newParams) {

    return t.update(JavaConversions.asScalaBuffer(newParams));

    /*
    if (t instanceof INot) {
      assert newParams.size() == 1;
      INot tt = (INot) t;
      assert tt.subformula().getClass() == newParams.get(0).getClass();
      return new INot((IFormula)newParams.get(0));

    } else if (t instanceof IBinFormula) {
      assert newParams.size() == 2;
      IBinFormula tt = (IBinFormula) t;
      assert tt.f1().getClass() == newParams.get(0).getClass();
      assert tt.f2().getClass() == newParams.get(1).getClass();
      return new IBinFormula(tt.j(), (IFormula)newParams.get(0), (IFormula)newParams.get(1));

    } else if (t instanceof IFormulaITE) {
      assert newParams.size() == 3;
      IFormulaITE tt = (IFormulaITE) t;
      assert tt.cond().getClass() == newParams.get(0).getClass();
      assert tt.left().getClass() == newParams.get(1).getClass();
      assert tt.right().getClass() == newParams.get(2).getClass();
      return new IFormulaITE((IFormula)newParams.get(0), (IFormula)newParams.get(1), (IFormula)newParams.get(2));

    } else if (t instanceof IPlus) {
      assert newParams.size() == 2;
      IPlus tt = (IPlus) t;
      assert tt.t1().getClass() == newParams.get(0).getClass();
      assert tt.t2().getClass() == newParams.get(1).getClass();
      return new IPlus((ITerm)newParams.get(0), (ITerm)newParams.get(1));

    } else {
      return t;
    }
    */
  }

  /** this function returns all variables in the terms.
   * Doubles are removed. */
  public static Set<IExpression> getVars(Collection<IExpression> exprList) {
    Set<IExpression> vars = new HashSet<>();
    Set<IExpression> seen = new HashSet<>();
    Deque<IExpression> todo = new ArrayDeque<>(exprList);

    while (!todo.isEmpty()) {
      IExpression t = todo.removeLast();
      if (!seen.add(t)) {
        continue;
      }

      if (isVariable(t)) {
        vars.add(t);
      } else if (t.length() > 0) {
        Iterator<IExpression> it = t.iterator();
        while (it.hasNext()) {
          todo.add(it.next());
        }
      }
    }
    return vars;
  }

  /** this function can be used to print a bigger term */
  public static String prettyPrint(IExpression t) {
    return t.toString();
  }
}
