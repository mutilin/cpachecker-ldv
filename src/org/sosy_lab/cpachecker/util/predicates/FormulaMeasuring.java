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
package org.sosy_lab.cpachecker.util.predicates;

import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.BooleanFormulaManagerView.RecursiveBooleanFormulaVisitor;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;

import com.google.common.collect.ImmutableSortedSet;


public class FormulaMeasuring {

  public static class FormulaMeasures {
    private int trues = 0;
    private int falses = 0;
    private int conjunctions = 0;
    private int disjunctions = 0;
    private int negations = 0;
    private int atoms = 0;
    private final Set<String> variables = new HashSet<>();

    public int getAtoms() { return atoms; }
    public int getConjunctions() { return conjunctions; }
    public int getDisjunctions() { return disjunctions; }
    public int getFalses() { return falses; }
    public int getNegations() { return negations; }
    public int getTrues() { return trues; }
    public ImmutableSortedSet<String> getVariables() { return ImmutableSortedSet.copyOf(this.variables); }
  }

  private final FormulaManagerView managerView;

  public FormulaMeasuring(FormulaManagerView pManagerView) {
    this.managerView = pManagerView;
  }

  public FormulaMeasures measure(BooleanFormula formula) {
    FormulaMeasures result = new FormulaMeasures();
    new FormulaMeasuringVisitor(managerView, result).visit(formula);
    return result;
  }

  private static class FormulaMeasuringVisitor extends RecursiveBooleanFormulaVisitor {

    private final FormulaMeasures measures;
    private final FormulaManagerView fmgr;

    FormulaMeasuringVisitor(FormulaManagerView pFmgr, FormulaMeasures pMeasures) {
      super(pFmgr);
      measures = pMeasures;
      fmgr = pFmgr;
    }

    @Override
    protected Void visitFalse() {
      measures.falses++;
      return null;
    }

    @Override
    protected Void visitTrue() {
      measures.trues++;
      return null;
    }

    @Override
    protected Void visitAtom(BooleanFormula pAtom) {
      measures.atoms++;

      BooleanFormula atom = fmgr.uninstantiate(pAtom);
      measures.variables.addAll(fmgr.extractVariableNames(atom));
      return null;
    }

    @Override
    protected Void visitNot(BooleanFormula pOperand) {
      measures.negations++;
      return super.visitNot(pOperand);
    }

    @Override
    protected Void visitAnd(BooleanFormula... pOperands) {
      measures.conjunctions++;
      return super.visitAnd(pOperands);
    }

    @Override
    protected Void visitOr(BooleanFormula... pOperand) {
      measures.disjunctions++;
      return super.visitOr(pOperand);
    }

    @Override
    protected Void visitEquivalence(BooleanFormula pOperand1, BooleanFormula pOperand2) {
      // TODO count?
      return super.visitEquivalence(pOperand1, pOperand2);
    }

    @Override
    protected Void visitIfThenElse(BooleanFormula pCondition, BooleanFormula pThenFormula, BooleanFormula pElseFormula) {
      // TODO count?
      return super.visitIfThenElse(pCondition, pThenFormula, pElseFormula);
    }

    @Override
    protected Void visitImplication(BooleanFormula pOperand1, BooleanFormula pOperand2) {
      // TODO count?
      return super.visitImplication(pOperand1, pOperand2);
    }
  }
}
