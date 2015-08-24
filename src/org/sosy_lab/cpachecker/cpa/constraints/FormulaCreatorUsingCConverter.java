/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.constraints;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.rationals.Rational;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.Constraint;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.IdentifierAssignment;
import org.sosy_lab.cpachecker.cpa.constraints.constraint.SymbolicExpressionTransformer;
import org.sosy_lab.cpachecker.cpa.value.symbolic.type.SymbolicIdentifier.Converter;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.solver.AssignableTerm;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.FloatingPointFormula;
import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.solver.api.FormulaType;
import org.sosy_lab.solver.api.NumeralFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.NumeralFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.FormulaEncodingOptions;

import com.google.common.base.Optional;

/**
 * Creator for {@link Formula}s using a given {@link CtoFormulaConverter} for creating
 * {@link BooleanFormula}s out of {@link Constraint}s.
 *
 * The properties responsible for the behaviour of PredicateCPA's formula handling influence the
 * behaviour of this class, too.
 * A number of important properties can be found in the classes {@link FormulaEncodingOptions}
 * and {@link FormulaManagerView}.
 */
public class FormulaCreatorUsingCConverter implements FormulaCreator {

  private final FormulaManagerView formulaManager;
  private final CtoFormulaConverter toFormulaTransformer;

  private final String functionName;

  public FormulaCreatorUsingCConverter(
      final FormulaManagerView pFormulaManager,
      final CtoFormulaConverter pConverter,
      final String pFunctionName
  ) {
    formulaManager = pFormulaManager;
    toFormulaTransformer = pConverter;
    functionName = pFunctionName;
  }

  @Override
  public BooleanFormula createFormula(final Constraint pConstraint)
      throws UnrecognizedCCodeException, InterruptedException {

    return createFormula(pConstraint, new IdentifierAssignment());
  }


  @Override
  public BooleanFormula createFormula(
      final Constraint pConstraint,
      final IdentifierAssignment pDefiniteAssignment
  ) throws UnrecognizedCCodeException, InterruptedException {

    final SymbolicExpressionTransformer toExpressionTransformer =
        new SymbolicExpressionTransformer(pDefiniteAssignment);

    CExpression constraintExpression = pConstraint.accept(toExpressionTransformer);

    return toFormulaTransformer.makePredicate(
        constraintExpression, getDummyEdge(), functionName, getSsaMapBuilder());
  }

  @Override
  public BooleanFormula transformAssignment(
      final AssignableTerm pTerm,
      final Object termAssignment,
      final VariableMap pVariables
  ) {
    Formula variable = getVariableForTerm(pTerm, pVariables);
    FormulaType<?> variableType = formulaManager.getFormulaType(variable);
    Formula rightFormula = null;

    final NumeralFormulaManagerView<NumeralFormula, NumeralFormula.RationalFormula>
        rationalFormulaManager = formulaManager.getRationalFormulaManager();

    if (termAssignment instanceof Number) {

      BigInteger integerValue = null;
      BigDecimal decimalValue = null;

      if (termAssignment instanceof Long) {
        integerValue = BigInteger.valueOf((long) termAssignment);

      } else if (termAssignment instanceof BigInteger) {
        integerValue = (BigInteger) termAssignment;

      } else if (termAssignment instanceof BigDecimal) {
        decimalValue = (BigDecimal) termAssignment;

      } else if (termAssignment instanceof Float || termAssignment instanceof Double) {
        assert variableType.isFloatingPointType();
        final FloatingPointFormula variableAsFloat = (FloatingPointFormula)variable;
        final Double assignmentAsDouble;

        if (termAssignment instanceof Float) {
          assignmentAsDouble = ((Float)termAssignment).doubleValue();
        } else {
          assignmentAsDouble = (Double)termAssignment;
        }

        if (assignmentAsDouble.isNaN()) {
          return getNanFormula(variableAsFloat);

        } else if (assignmentAsDouble.equals(Double.POSITIVE_INFINITY)) {
          return getPositiveInfinityFormula(variableAsFloat);

        } else if (assignmentAsDouble.equals(Double.NEGATIVE_INFINITY)) {
          return getNegativeInfinityFormula(variableAsFloat);

        } else {
          decimalValue = BigDecimal.valueOf(assignmentAsDouble);
        }

      } else if (termAssignment instanceof Rational) {
        rightFormula = rationalFormulaManager.makeNumber((Rational) termAssignment);
      } else {
        throw new AssertionError("Unhandled assignment number " + termAssignment);
      }

      if (integerValue != null) {
        rightFormula = formulaManager.makeNumber(variableType, integerValue);

      } else if (decimalValue != null) {

        if (variableType.isRationalType()) {
          rightFormula = rationalFormulaManager.makeNumber(decimalValue);
        } else {
          assert variableType.isFloatingPointType();
          FormulaType.FloatingPointType variableTypeCastToFloatType =
              (FormulaType.FloatingPointType) variableType;

          rightFormula =
              formulaManager.getFloatingPointFormulaManager().makeNumber(
                  decimalValue, variableTypeCastToFloatType);
        }
      }

    } else {
      throw new AssertionError("Unhandled assignment object " + termAssignment);
    }

    assert rightFormula != null;
    return formulaManager.makeEqual(variable, rightFormula);
  }

  private BooleanFormula getNanFormula(FloatingPointFormula pFormula) {
    return formulaManager.getFloatingPointFormulaManager().isNaN(pFormula);
  }

  private BooleanFormula getPositiveInfinityFormula(FloatingPointFormula pFormula) {
    FormulaType.FloatingPointType formulaType =
        (FormulaType.FloatingPointType) formulaManager.getFormulaType(pFormula);
    Formula infinityFormula =
        formulaManager.getFloatingPointFormulaManager().makePlusInfinity(formulaType);
    return formulaManager.makeEqual(pFormula, infinityFormula);
  }

  private BooleanFormula getNegativeInfinityFormula(FloatingPointFormula pFormula) {
    FormulaType.FloatingPointType formulaType =
        (FormulaType.FloatingPointType) formulaManager.getFormulaType(pFormula);
    Formula infinityFormula =
        formulaManager.getFloatingPointFormulaManager().makeMinusInfinity(formulaType);

    return formulaManager.makeEqual(pFormula, infinityFormula);
  }

  private CFAEdge getDummyEdge() {
    return DummyEdge.getInstance(functionName);
  }

  private SSAMap.SSAMapBuilder getSsaMapBuilder() {
    return SSAMap.emptySSAMap().builder();
  }

  /**
   * Returns a variable of the given {@link VariableMap} representing the given term.
   * A fitting variable has to be present in the {@link VariableMap}.
   *
   * @param pTerm the term to get a corresponding variable for
   * @param pVariables the map of possible variables
   * @return a variable representing the given term, in form of a {@link Formula}
   */
  private Formula getVariableForTerm(AssignableTerm pTerm, VariableMap pVariables) {
    final Converter symIdConverter = Converter.getInstance();

    final String name = symIdConverter.normalizeStringEncoding(pTerm.getName());

    return pVariables.get(name);
  }

  private static class DummyEdge implements CFAEdge {

    private static final String UNKNOWN = "unknown";
    private static final FileLocation DUMMY_LOCATION = new FileLocation(0, UNKNOWN, 0, 0, 0);

    private static Map<String, DummyEdge> existingEdges = new HashMap<>();

    private final CFANode dummyNode;

    private DummyEdge(String pFunctionName) {
      dummyNode = new CFANode(pFunctionName);
    }

    public static DummyEdge getInstance(String pFunctionName) {
      DummyEdge edge = existingEdges.get(pFunctionName);

      if (edge == null) {
        edge = new DummyEdge(pFunctionName);
        existingEdges.put(pFunctionName, edge);
      }

      return edge;
    }

    @Override
    public CFAEdgeType getEdgeType() {
      return CFAEdgeType.BlankEdge;
    }

    @Override
    public CFANode getPredecessor() {
      return dummyNode;
    }

    @Override
    public CFANode getSuccessor() {
      return dummyNode;
    }

    @Override
    public Optional<? extends AAstNode> getRawAST() {
      return Optional.absent();
    }

    @Override
    public int getLineNumber() {
      return 0;
    }

    @Override
    public FileLocation getFileLocation() {
      return DUMMY_LOCATION;
    }

    @Override
    public String getRawStatement() {
      return UNKNOWN;
    }

    @Override
    public String getCode() {
      return UNKNOWN;
    }

    @Override
    public String getDescription() {
      return UNKNOWN;
    }
  }
}
