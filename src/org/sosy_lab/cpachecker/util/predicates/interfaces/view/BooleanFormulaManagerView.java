/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.interfaces.view;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.interfaces.UnsafeFormulaManager;


public class BooleanFormulaManagerView extends BaseManagerView<BooleanFormula> implements BooleanFormulaManager {

  private BooleanFormulaManager manager;

  public BooleanFormulaManagerView(BooleanFormulaManager pManager) {
    this.manager = pManager;
  }

  public BooleanFormula makeVariable(String pVar, int pI) {
    return makeVariable(FormulaManagerView.makeName(pVar, pI));
  }

  @Override
  public BooleanFormula not(BooleanFormula pBits) {
    return wrapInView(manager.not(extractFromView(pBits)));
  }

  @Override
  public BooleanFormula and(BooleanFormula pBits1, BooleanFormula pBits2) {
    return wrapInView(manager.and(extractFromView(pBits1), extractFromView(pBits2)));
  }
  @Override
  public BooleanFormula or(BooleanFormula pBits1, BooleanFormula pBits2) {
    return wrapInView(manager.or(extractFromView(pBits1), extractFromView(pBits2)));
  }
  @Override
  public BooleanFormula xor(BooleanFormula pBits1, BooleanFormula pBits2) {
    return wrapInView(manager.xor(extractFromView(pBits1), extractFromView(pBits2)));
  }

  @Override
  public boolean isNot(BooleanFormula pBits) {
    return manager.isNot(extractFromView(pBits));
  }

  @Override
  public boolean isAnd(BooleanFormula pBits) {
    return manager.isAnd(extractFromView(pBits));
  }

  @Override
  public boolean isOr(BooleanFormula pBits) {
    return manager.isOr(extractFromView(pBits));
  }

  @Override
  public boolean isXor(BooleanFormula pBits) {
    return manager.isXor(extractFromView(pBits));
  }

  @Override
  public boolean isBoolean(Formula pF) {
    return pF instanceof BooleanFormula;
  }

  @Override
  public FormulaType<BooleanFormula> getFormulaType() {
    return manager.getFormulaType();
  }

  @Override
  public BooleanFormula makeBoolean(boolean pValue) {
    return wrapInView(manager.makeBoolean(pValue));
  }

  @Override
  public BooleanFormula makeVariable(String pVar) {
    return wrapInView(manager.makeVariable(pVar));
  }

  @Override
  public BooleanFormula equivalence(BooleanFormula pFormula1, BooleanFormula pFormula2) {
    return wrapInView(manager.equivalence(extractFromView(pFormula1), extractFromView(pFormula2)));
  }

  @Override
  public boolean isTrue(BooleanFormula pFormula) {
    return manager.isTrue(extractFromView(pFormula));
  }

  @Override
  public boolean isFalse(BooleanFormula pFormula) {
    return manager.isFalse(extractFromView(pFormula));
  }

  @Override
  public <T extends Formula> T ifThenElse(BooleanFormula pCond, T pF1, T pF2) {
    FormulaManagerView viewManager = getViewManager();
    return viewManager.wrapInView(manager.ifThenElse(extractFromView(pCond), viewManager.extractFromView(pF1), viewManager.extractFromView(pF2)));
  }

  @Override
  public <T extends Formula> boolean isIfThenElse(T pF) {
    FormulaManagerView viewManager = getViewManager();
    return manager.isIfThenElse(viewManager.extractFromView(pF));
  }

  public <T extends Formula> Triple<BooleanFormula, T, T> splitIfThenElse(T pF) {
    checkArgument(isIfThenElse(pF));

    FormulaManagerView fmgr = getViewManager();
    UnsafeFormulaManager unsafe = fmgr.getUnsafeFormulaManager();
    assert unsafe.getArity(pF) == 3;

    BooleanFormula cond = wrapInView(unsafe.typeFormula(FormulaType.BooleanType, unsafe.getArg(pF, 0)));
    T thenBranch = fmgr.wrapInView(unsafe.typeFormula(fmgr.getFormulaType(pF), unsafe.getArg(pF, 1)));
    T elseBranch = fmgr.wrapInView(unsafe.typeFormula(fmgr.getFormulaType(pF), unsafe.getArg(pF, 2)));

    return Triple.of(cond, thenBranch, elseBranch);
  }

  @Override
  public boolean isEquivalence(BooleanFormula pFormula) {
    return manager.isEquivalence(extractFromView(pFormula));
  }

  public BooleanFormula conjunction(List<BooleanFormula> f) {
    BooleanFormula result = manager.makeBoolean(true);
    for (BooleanFormula formula : f) {
      result = manager.and(result, extractFromView(formula));
    }
    return wrapInView(result);
  }

  public BooleanFormula implication(BooleanFormula p, BooleanFormula q) {
    return or(not(p), q);
  }


  public BooleanFormula notEquivalence(BooleanFormula p, BooleanFormula q) {
    return not(equivalence(p, q));
  }

  public static abstract class BooleanFormulaVisitor<R> {

    private final FormulaManagerView fmgr;
    private final BooleanFormulaManagerView bfmgr;
    private final UnsafeFormulaManager unsafe;

    protected BooleanFormulaVisitor(FormulaManagerView pFmgr) {
      fmgr = pFmgr;
      bfmgr = fmgr.getBooleanFormulaManager();
      unsafe = fmgr.getUnsafeFormulaManager();
    }

    public final R visit(BooleanFormula f) {
      if (bfmgr.isTrue(f)) {
        return visitTrue();
      }

      if (bfmgr.isFalse(f)) {
        return visitFalse();
      }

      if (unsafe.isAtom(fmgr.extractFromView(f))) {
        return visitAtom(f);
      }

      if (bfmgr.isNot(f)) {
        return visitNot(getArg(f, 0));
      }

      if (bfmgr.isAnd(f)) {
        return visitAnd(getArg(f, 0), getArg(f, 1));
      }
      if (bfmgr.isOr(f)) {
        return visitOr(getArg(f, 0), getArg(f, 1));
      }

      if (bfmgr.isEquivalence(f)) {
        return visitEquivalence(getArg(f, 0), getArg(f, 1));
      }

      if (bfmgr.isIfThenElse(f)) {
        return visitIfThenElse(getArg(f, 0), getArg(f, 1), getArg(f, 2));
      }

      throw new UnsupportedOperationException();
    }

    private final BooleanFormula getArg(BooleanFormula pF, int i) {
      return unsafe.typeFormula(FormulaType.BooleanType, unsafe.getArg(pF, i));
    }

    protected abstract R visitTrue();
    protected abstract R visitFalse();
    protected abstract R visitAtom(BooleanFormula atom);
    protected abstract R visitNot(BooleanFormula operand);
    protected abstract R visitAnd(BooleanFormula operand1, BooleanFormula operand2);
    protected abstract R visitOr(BooleanFormula operand1, BooleanFormula operand2);
    protected abstract R visitEquivalence(BooleanFormula operand1, BooleanFormula operand2);
    protected abstract R visitIfThenElse(BooleanFormula condition, BooleanFormula thenFormula, BooleanFormula elseFormula);
  }

  public static abstract class DefaultBooleanFormulaVisitor<R> extends BooleanFormulaVisitor<R> {

    protected DefaultBooleanFormulaVisitor(FormulaManagerView pFmgr) {
      super(pFmgr);
    }

    @Override
    protected R visitTrue() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitFalse() {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitAtom(BooleanFormula pAtom) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitNot(BooleanFormula pOperand) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitAnd(BooleanFormula pOperand1, BooleanFormula pOperand2) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitOr(BooleanFormula pOperand1, BooleanFormula pOperand2) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitEquivalence(BooleanFormula pOperand1, BooleanFormula pOperand2) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected R visitIfThenElse(BooleanFormula pCondition, BooleanFormula pThenFormula, BooleanFormula pElseFormula) {
      throw new UnsupportedOperationException();
    }
  }
}
