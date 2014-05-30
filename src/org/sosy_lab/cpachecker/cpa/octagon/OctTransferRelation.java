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
package org.sosy_lab.cpachecker.cpa.octagon;

import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Iterables.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CTypedefType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.octagon.OctState.Type;
import org.sosy_lab.cpachecker.cpa.octagon.coefficients.IOctCoefficients;
import org.sosy_lab.cpachecker.cpa.octagon.coefficients.OctEmptyCoefficients;
import org.sosy_lab.cpachecker.cpa.octagon.coefficients.OctIntervalCoefficients;
import org.sosy_lab.cpachecker.cpa.octagon.coefficients.OctNumericValue;
import org.sosy_lab.cpachecker.cpa.octagon.coefficients.OctSimpleCoefficients;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.InvalidCFAException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCCodeException;
import org.sosy_lab.cpachecker.util.CFAUtils.Loop;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

@Options(prefix="cpa.octagon")
public class OctTransferRelation extends ForwardingTransferRelation<Set<OctState>, OctState, OctPrecision> {

  private static final String FUNCTION_RETURN_VAR = "___cpa_temp_result_var_";
  private static final String TEMP_VAR_PREFIX = "___cpa_temp_var_";

  /**
   * counter for temporary variables which should be increased after every
   * declaration of a new temporary variable
   */
  private static int temporaryVariableCounter = 0;

  /**
   * set of functions that may not appear in the source code
   * the value of the map entry is the explanation for the user
   */
  private static final Map<String, String> UNSUPPORTED_FUNCTIONS
      = ImmutableMap.of("pthread_create", "threads");

  private final boolean handleFloats;

  private final LogManager logger;

  private final Map<CFAEdge, Loop> loopEntryEdges;

  /**
   * Class constructor.
   * @throws InvalidCFAException
   * @throws InvalidConfigurationException
   */
  public OctTransferRelation(LogManager log, CFA cfa, boolean handleFloats) throws InvalidCFAException {
    logger = log;

    if (!cfa.getLoopStructure().isPresent()) {
      throw new InvalidCFAException("OctagonCPA does not work without loop information!");
    }

    Multimap<String, Loop> loops = cfa.getLoopStructure().get();
    Map<CFAEdge, Loop> entryEdges = new HashMap<>();

    for (Loop l : loops.values()) {
      // function edges do not count as incoming/outgoing edges
      Iterable<CFAEdge> incomingEdges = filter(l.getIncomingEdges(),
                                               not(instanceOf(CFunctionReturnEdge.class)));

      for (CFAEdge e : incomingEdges) {
          entryEdges.put(e, l);
      }
    }
    loopEntryEdges = Collections.unmodifiableMap(entryEdges);

    this.handleFloats = handleFloats;
  }

  @Override
  public Collection<OctState> getAbstractSuccessors(
      final AbstractState abstractState, final Precision abstractPrecision, final CFAEdge cfaEdge)
      throws CPATransferException {

    setInfo(abstractState, abstractPrecision, cfaEdge);

    final Collection<OctState> preCheck = preCheck();
    if (preCheck != null) { return preCheck; }

    final Collection<OctState> successors = new ArrayList<>();

    switch (cfaEdge.getEdgeType()) {

    case AssumeEdge:
      final AssumeEdge assumption = (AssumeEdge) cfaEdge;
      successors.addAll(handleAssumption(assumption, assumption.getExpression(), assumption.getTruthAssumption()));
      break;

    case FunctionCallEdge:
      final FunctionCallEdge fnkCall = (FunctionCallEdge) cfaEdge;
      final FunctionEntryNode succ = fnkCall.getSuccessor();
      final String calledFunctionName = succ.getFunctionName();
      successors.addAll(handleFunctionCallEdge(fnkCall, fnkCall.getArguments(),
          succ.getFunctionParameters(), calledFunctionName));
      break;

    case FunctionReturnEdge:
      final String callerFunctionName = cfaEdge.getSuccessor().getFunctionName();
      final FunctionReturnEdge fnkReturnEdge = (FunctionReturnEdge) cfaEdge;
      final FunctionSummaryEdge summaryEdge = fnkReturnEdge.getSummaryEdge();
      successors.addAll(handleFunctionReturnEdge(fnkReturnEdge,
          summaryEdge, summaryEdge.getExpression(), callerFunctionName));

      break;

    case MultiEdge:
      successors.addAll(handleMultiEdge((MultiEdge) cfaEdge));
      break;

    default:
      successors.addAll(handleSimpleEdge(cfaEdge));
    }

    assert !successors.removeAll(Collections.singleton(null));

    // remove all states whose constraints cannot be satisfied
    Iterator<OctState> states = successors.iterator();
    while (states.hasNext()) {
      OctState st = states.next();
      if (st.isEmpty()) {
        states.remove();
        logger.log(Level.FINER, "removing state because of unsatisfiable constraints:\n" +
                                 st + "________________\nEdge was:\n" + cfaEdge.getDescription());
      }
    }

    Set<OctState> cleanedUpStates = new HashSet<>();
    // TODO overapproximation here, we should not need to remove those vars
    // instead it would be much better if we could omit creating them, p.e. through
    // creating the temporary vars in the cfa, before analyzing the program
    for (OctState st : successors) {
      cleanedUpStates.add(st.removeTempVars(functionName, TEMP_VAR_PREFIX));
    }

    if (loopEntryEdges.get(cfaEdge) != null) {
      Set<OctState> newStates = new HashSet<>();
      for (OctState s : cleanedUpStates) {
        newStates.add(new OctState(s.getOctagon(),
                                   s.getVariableToIndexMap(),
                                   s.getVariableToTypeMap(),
                                   new OctState.Block(),
                                   logger, handleFloats));
      }
      cleanedUpStates = newStates;
    }

    resetInfo();

    return cleanedUpStates;
  }

  @Override
  protected Set<OctState> handleBlankEdge(BlankEdge cfaEdge) throws CPATransferException{
    return Collections.singleton(state);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected Set<OctState> handleAssumption(CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
      throws CPATransferException {

    // Binary operation
    if (expression instanceof CBinaryExpression) {
      return handleBinaryBooleanExpression((CBinaryExpression) expression, truthAssumption,state);

      // Unary operation
    } else if (expression instanceof CUnaryExpression) {
      CUnaryExpression unaryExp = ((CUnaryExpression) expression);

      switch (unaryExp.getOperator()) {
        // do not change anything besides the expression, minus has no effect
        // on the == 0 equality check
      case MINUS:
        return handleAssumption(cfaEdge, unaryExp.getOperand(), truthAssumption);

        // TODO check if some cases could be handled
      case SIZEOF:
      case TILDE:
      case AMPER:
        return Collections.singleton(state);
      default:
        throw new CPATransferException("Unhandled case: " + unaryExp.getOperator());
      }

      // An expression which cannot be simplified anymore
    } else if (expression instanceof CIdExpression
        || expression instanceof CFieldReference
        || (expression instanceof CPointerExpression && ((CPointerExpression) expression).getOperand() instanceof CIdExpression)) {
      if (isHandleableVariable(expression)) {
        String varName = buildVarName((CLeftHandSide) expression, functionName);
        return handleSingleBooleanExpression(varName, truthAssumption, state);
      } else {
        return Collections.singleton(state);
      }

      // A constant value
    } else if (expression instanceof CLiteralExpression) {
      if (expression instanceof CIntegerLiteralExpression) {
        return handleLiteralBooleanExpression(((CIntegerLiteralExpression) expression).asLong(), truthAssumption, state);

      } else if (expression instanceof CCharLiteralExpression) {
        return handleLiteralBooleanExpression(((CCharLiteralExpression) expression).getCharacter(), truthAssumption, state);

      } else if (expression instanceof CFloatLiteralExpression) {
        // only when the float is exactly zero the condition is wrong, for all other float values it is true
        int val = Math.abs(((CFloatLiteralExpression)expression).getValue().signum());
        return handleLiteralBooleanExpression(val, truthAssumption, state);
      } else {
        return Collections.singleton(state);
      }

      // a cast, we ignore this cast and call this method again with the casts operand
    } else if (expression instanceof CCastExpression) {
      return handleAssumption(cfaEdge, ((CCastExpression) expression).getOperand(), truthAssumption);
    }

    else {
      throw new UnrecognizedCCodeException("Unknown expression type in assumption", cfaEdge, expression);
    }
  }

  /**
   * If only one literal is the complete boolean expression, we only need to check
   * this literal if it is equal to zero, depending on the truth assumption we
   * either return the unchanged state or null if the following branch is not reachable.
   *
   * @param value The long value of the CLiteralExpression
   * @param truthAssumption
   * @return an OctState or null
   */
  private Set<OctState> handleLiteralBooleanExpression(long value, boolean truthAssumption, OctState state) {
    if (value == 0) {
      if (truthAssumption) {
        return Collections.emptySet();
      } else {
        return Collections.singleton(state);
      }
    } else {
      if (truthAssumption) {
        return Collections.singleton(state);
      } else {
        return Collections.emptySet();
      }
    }
  }

  private OctState.Type getCorrespondingOctStateType(CType type) {
    if (handleFloats && type instanceof CSimpleType &&
        (((CSimpleType)type).getType() == CBasicType.FLOAT
          || ((CSimpleType)type).getType() == CBasicType.DOUBLE)) {
      return Type.FLOAT;
    } else {
      return Type.INT;
    }
  }

  /**
   * This method handles all binary boolean expressions.
   */
  private Set<OctState> handleBinaryBooleanExpression(CBinaryExpression binExp, boolean truthAssumption, OctState state) throws CPATransferException {

    // IMPORTANT: for this switch we assume that in each conditional statement, there is only one
    // condition, (this simplification is added in the cfa creation phase)
    switch (binExp.getOperator()) {
    // TODO check which cases can be handled
    case BINARY_AND:
    case BINARY_OR:
    case BINARY_XOR:
    case SHIFT_LEFT:
    case SHIFT_RIGHT:
    case DIVIDE:
    case MODULO:
    case MULTIPLY:
      return Collections.singleton(state);

    // for the following cases we first create a temporary variable where
    // the result of the operation is saved, afterwards, the equality with == 0
    // is checked
    case MINUS:
    case PLUS:
      String tempVarName = buildVarName(functionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
      temporaryVariableCounter++;
      COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, functionName);
      Set<Pair<IOctCoefficients, OctState>> coeffsList = binExp.accept(coeffVisitor);
      Set<OctState> possibleStates = new HashSet<>();
      for (Pair<IOctCoefficients, OctState> pairs : coeffsList) {
        IOctCoefficients coeffs = pairs.getFirst();

        // we have an undefined value, so there is no need to make any assumptions about it
        if (coeffs.equals(OctEmptyCoefficients.INSTANCE)) {
          return Collections.singleton(state);
        }

        OctState tmp = pairs.getSecond().declareVariable(tempVarName,
                                                         getCorrespondingOctStateType(binExp.getExpressionType()));
        tmp = tmp.makeAssignment(tempVarName, coeffs.expandToSize(tmp.sizeOfVariables(), tmp));
        possibleStates.addAll(handleSingleBooleanExpression(tempVarName,
                                                         truthAssumption,
                                                         tmp));
      }
      return possibleStates;

      // in the following cases we have to check left and right part of the binary
      // expression, when they are not single variables but contain for example
      // another binary expression we have to create some temporary variables again
      // which will be compared afterwards
    case EQUALS:
    case NOT_EQUALS:
    case GREATER_EQUAL:
    case GREATER_THAN:
    case LESS_EQUAL:
    case LESS_THAN:
      CExpression left = binExp.getOperand1();
      CExpression right = binExp.getOperand2();
      BinaryOperator op = binExp.getOperator();

      if (!isHandleableVariable(left) || !isHandleableVariable(right)) {
        return Collections.singleton(state);
      }

      if (left instanceof CLiteralExpression || right instanceof CLiteralExpression) {
        return handleBinaryAssumptionWithLiteral(left, right, op, truthAssumption, state);
      } else {
        return handleBinaryAssumptionWithoutLiteral(binExp, truthAssumption, left, right, state);
      }

    default:
      throw new CPATransferException("Unhandled case: " + binExp.getOperator());
    }
  }

  /**
   * This method is a helper method for handleBinaryBooleanExpression. It handles
   * all Assumptions with one literal and one variable or with two literals.
   * (p.e. a < 4; 4 < a; 3 < 4)
   */
  private Set<OctState> handleBinaryAssumptionWithLiteral(CExpression left, CExpression right, BinaryOperator op,
      boolean truthAssumption, OctState state) throws CPATransferException {

    // we cannot cope with string literals so we do not know anything about the assumption
    // => just return the previous state
    if (left instanceof CStringLiteralExpression || right instanceof CStringLiteralExpression) { return Collections.singleton(state); }

    // both are literals
    if (left instanceof CLiteralExpression && right instanceof CLiteralExpression) {
      return handleBinaryAssumptionWithTwoLiterals((CLiteralExpression)left, (CLiteralExpression)right, op, truthAssumption);
    } else if (left instanceof CLiteralExpression) {
      //change operator so we can call handleBinaryAssumptionWithOneLiteral
      switch(op) {
      case GREATER_EQUAL:
        op = BinaryOperator.LESS_EQUAL;
        break;
      case GREATER_THAN:
        op = BinaryOperator.LESS_THAN;
        break;
      case LESS_EQUAL:
        op = BinaryOperator.GREATER_EQUAL;
        break;
      case LESS_THAN:
        op = BinaryOperator.GREATER_THAN;
      }
      return handleBinaryAssumptionWithOneLiteral(right, (CLiteralExpression) left, op, truthAssumption, state);

      // literal is on the right position, variable on the left;
    } else if (right instanceof CLiteralExpression) {
      return handleBinaryAssumptionWithOneLiteral(left, (CLiteralExpression) right, op, truthAssumption, state);
    }

    // if we did not return anything up to now we were not able to handle it
    // => just return the previous state
    return Collections.singleton(state);
  }

  private boolean isHandleableVariable(CExpression var) {
    if (var instanceof CArraySubscriptExpression
        || var instanceof CFieldReference
        || var instanceof CPointerExpression
        || (!handleFloats && var instanceof CFloatLiteralExpression)
        || (var instanceof CFieldReference && ((CFieldReference)var).isPointerDereference())) {
      return false;
    }
    return isHandleAbleType(var.getExpressionType());
  }

  private boolean isHandleAbleType(CType type) {
    type = type.getCanonicalType();
    while (type instanceof CTypedefType) {
      type = ((CTypedefType) type).getRealType();
    }
    if (type instanceof CPointerType
        || type instanceof CCompositeType
        || type instanceof CArrayType) {
      return false;
    }

    return true;
  }

  /**
   * This method assumes the literal is on the righthandside! So take care while
   * calling this method, and if necessary change the operator to its opposite.
   * (p.e. a < 4)
   */
  private Set<OctState> handleBinaryAssumptionWithOneLiteral(CExpression left, CLiteralExpression right, BinaryOperator op,
      boolean truthAssumption, OctState state) throws CPATransferException {

    // we cannot handle pointers, so just ignore them
    if (left.getExpressionType() instanceof CPointerType
        || (left instanceof CFieldReference && ((CFieldReference) left).isPointerDereference())) {
      return Collections.singleton(state);
    }

    String leftVarName = null;
    List<OctState> states = new ArrayList<>();
    states.add(state);

    // check left side
    if (left instanceof CIdExpression || left instanceof CFieldReference) {
      leftVarName = buildVarName((CLeftHandSide) left, functionName);

      // create a temp var for the left side of the expression
    } else {
      COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, functionName);
      Set<Pair<IOctCoefficients, OctState>> coeffsLeft = left.accept(coeffVisitor);

      String tempLeft = buildVarName(functionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
      temporaryVariableCounter++;
      List<OctState> tmpList = new ArrayList<>();
      for (Pair<IOctCoefficients, OctState> pairs : coeffsLeft) {
        IOctCoefficients coeffs = pairs.getFirst();

        // we cannot do any comparison with an unknown value, so just quit here
        if (coeffs.equals(OctEmptyCoefficients.INSTANCE)) {
          return Collections.singleton(state);
        }

        OctState tmpState = pairs.getSecond().declareVariable(tempLeft, getCorrespondingOctStateType(left.getExpressionType()));
        tmpList.add(tmpState.makeAssignment(tempLeft, coeffs.expandToSize(tmpState.sizeOfVariables(), tmpState)));
      }
      states = tmpList;
      leftVarName = tempLeft;
    }

    OctNumericValue rightVal = OctNumericValue.ZERO;
    if (right instanceof CIntegerLiteralExpression) {
      rightVal = new OctNumericValue(((CIntegerLiteralExpression) right).asLong());
    } else if (right instanceof CCharLiteralExpression) {
      rightVal = new OctNumericValue(((CCharLiteralExpression) right).getCharacter());
    } else if (right instanceof CFloatLiteralExpression && handleFloats) {
      rightVal = new OctNumericValue(((CFloatLiteralExpression) right).getValue());

    // we cannot handle strings, so just return the previous state
    } else {
      return Collections.singleton(state);
    }

    Set<OctState> possibleStates = new HashSet<>();
    for (OctState actState : states) {
      switch (op) {
      case EQUALS:
        if (truthAssumption) {
          possibleStates.add(actState.addEqConstraint(leftVarName, rightVal));
        } else {
          possibleStates.addAll(actState.addIneqConstraint(leftVarName, rightVal));
        }
        break;

      case NOT_EQUALS:
        if (truthAssumption) {
          possibleStates.addAll(actState.addIneqConstraint(leftVarName, rightVal));
        } else {
          possibleStates.add(actState.addEqConstraint(leftVarName, rightVal));
        }
        break;

      case LESS_EQUAL:
        if (truthAssumption) {
          possibleStates.add(actState.addSmallerEqConstraint(leftVarName, rightVal));
        } else {
          possibleStates.add(actState.addGreaterConstraint(leftVarName, rightVal));
        }
        break;

      case LESS_THAN:
        if (truthAssumption) {
          possibleStates.add(actState.addSmallerConstraint(leftVarName, rightVal));
        } else {
          possibleStates.add(actState.addGreaterEqConstraint(leftVarName, rightVal));
        }
        break;

      case GREATER_EQUAL:
        if (truthAssumption) {
          possibleStates.add(actState.addGreaterEqConstraint(leftVarName, rightVal));
        } else {
          possibleStates.add(actState.addSmallerConstraint(leftVarName, rightVal));
        }
        break;

      case GREATER_THAN:
        if (truthAssumption) {
          possibleStates.add(actState.addGreaterConstraint(leftVarName, rightVal));
        } else {
          possibleStates.add(actState.addSmallerEqConstraint(leftVarName, rightVal));
        }
        break;

      default:
        throw new CPATransferException("Unhandled case statement: " + op);
      }
    }

    return possibleStates;
  }

  /**
   * This method handles binary assumptions with two literals (p.e. 1 < 3). As
   * this method is only a submethod of handleBinaryAssumptionWithOneLiteral it
   * assumes that the literal is eiter a CIntegerLiteralExpression or
   * a CCharLiteralExpression.
   */
  private Set<OctState> handleBinaryAssumptionWithTwoLiterals(CLiteralExpression left, CLiteralExpression right, BinaryOperator op,
      boolean truthAssumption) throws CPATransferException {
    OctNumericValue leftVal = OctNumericValue.ZERO;
    if (left instanceof CIntegerLiteralExpression) {
      leftVal = new OctNumericValue(((CIntegerLiteralExpression) left).asLong());
    } else if (left instanceof CCharLiteralExpression) {
      leftVal = new OctNumericValue(((CCharLiteralExpression) left).getCharacter());
    } else if (left instanceof CFloatLiteralExpression) {
      leftVal = new OctNumericValue(((CFloatLiteralExpression)left).getValue());
    }

    OctNumericValue rightVal = OctNumericValue.ZERO;
    if (right instanceof CIntegerLiteralExpression) {
      rightVal = new OctNumericValue(((CIntegerLiteralExpression) right).asLong());
    } else if (right instanceof CCharLiteralExpression) {
      rightVal = new OctNumericValue(((CCharLiteralExpression) right).getCharacter());
    } else if (right instanceof CFloatLiteralExpression) {
      rightVal = new OctNumericValue(((CFloatLiteralExpression)right).getValue());
    }

    switch (op) {
    case EQUALS:
      if (truthAssumption) {
        if (leftVal.equals(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      } else {
        if (leftVal.equals(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      }
    case GREATER_EQUAL:
      if (truthAssumption) {
        if (leftVal.greaterEqual(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      } else {
        if (leftVal.greaterEqual(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      }
    case GREATER_THAN:
      if (truthAssumption) {
        if (leftVal.greaterThan(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      } else {
        if (leftVal.greaterThan(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      }
    case LESS_EQUAL:
      if (truthAssumption) {
        if (leftVal.lessEqual(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      } else {
        if (leftVal.lessEqual(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      }
    case LESS_THAN:
      if (truthAssumption) {
        if (leftVal.lessThan(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      } else {
        if (leftVal.lessThan(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      }
    case NOT_EQUALS:
      if (truthAssumption) {
        if (leftVal.equals(rightVal)) {
          return Collections.emptySet();
        } else {
          return Collections.singleton(state);
        }
      } else {
        if (leftVal.equals(rightVal)) {
          return Collections.singleton(state);
        } else {
          return Collections.emptySet();
        }
      }
    default:
      throw new CPATransferException("Unhandled case: " + op);
    }
  }

  /**
   * This method handles all binary assumptions without literals (p.e. a < b)
    */
  private Set<OctState> handleBinaryAssumptionWithoutLiteral(CBinaryExpression binExp, boolean truthAssumption,
      CExpression left, CExpression right, OctState state)
      throws CPATransferException {
    CBinaryExpression.BinaryOperator op = binExp.getOperator();
    String leftVarName = null;
    String rightVarName = null;

    // we cannot handle pointers, so just ignore them
    // TODO make program unsafe?
    if (!isHandleableVariable(left) || !isHandleableVariable(right)) {
      return Collections.singleton(state);
    }

    Set<OctState> states = new HashSet<>();
    states.add(state);

    // check left side
    if (left instanceof CIdExpression || left instanceof CFieldReference) {
      leftVarName = buildVarName((CLeftHandSide) left, functionName);

      // create a temp var for the left side of the expression
    } else {
      COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, functionName);
      Set<Pair<IOctCoefficients, OctState>> coeffsLeft = left.accept(coeffVisitor);

      String tempLeft = buildVarName(functionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
      temporaryVariableCounter++;
      Set<OctState> tmpSet = new HashSet<>();
      for (Pair<IOctCoefficients, OctState> pairs : coeffsLeft) {
        IOctCoefficients coeffs = pairs.getFirst();

        // we cannot do any comparison with an unknown value, so just quit here
        if (coeffs.equals(OctEmptyCoefficients.INSTANCE)) {
          return Collections.singleton(state);
        }

        OctState tmp = pairs.getSecond().declareVariable(tempLeft, getCorrespondingOctStateType(left.getExpressionType()));
        tmpSet.add(tmp.makeAssignment(tempLeft, coeffs.expandToSize(tmp.sizeOfVariables(), tmp)));
      }
      states = tmpSet;
      leftVarName = tempLeft;
    }

    // check right side
    if (right instanceof CIdExpression || right instanceof CFieldReference) {
      rightVarName = buildVarName((CLeftHandSide) right, functionName);

      // create a temp var for the right side of the expression
    } else {

      // create the temp var name for the right side of the expression before the loop
      // so we have the same name everywhere for the variable
      String tempRight = buildVarName(functionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
      temporaryVariableCounter++;
      Set<OctState> tmpSet = new HashSet<>();

      for (OctState st : states) {
        COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(st, functionName);
        Set<Pair<IOctCoefficients, OctState>> coeffsRight = right.accept(coeffVisitor);

        for (Pair<IOctCoefficients, OctState> pairs : coeffsRight) {
          IOctCoefficients coeffs = pairs.getFirst();

          // we cannot do any comparison with an unknown value, so just quit here
          if (coeffs.equals(OctEmptyCoefficients.INSTANCE)) {
            return Collections.singleton(state);
          }

          OctState tmp = pairs.getSecond().declareVariable(tempRight, getCorrespondingOctStateType(right.getExpressionType()));
          tmpSet.add(tmp.makeAssignment(tempRight, coeffs.expandToSize(tmp.sizeOfVariables(), tmp)));
        }
      }
      states = tmpSet;
      rightVarName = tempRight;
    }

    Set<OctState> possibleStates = new HashSet<>();
    // Comparison part, left and right are now definitely available
    for (OctState actState : states) {
      switch (op) {
      case EQUALS:
        if (truthAssumption) {
          possibleStates.add(actState.addEqConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.addAll(actState.addIneqConstraint(rightVarName, leftVarName));
        }
        break;

      case GREATER_EQUAL:
        if (truthAssumption) {
          possibleStates.add(actState.addGreaterEqConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.add(actState.addSmallerConstraint(rightVarName, leftVarName));
        }
        break;

      case GREATER_THAN:
        if (truthAssumption) {
          possibleStates.add(actState.addGreaterConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.add(actState.addSmallerEqConstraint(rightVarName, leftVarName));
        }
        break;

      case LESS_EQUAL:
        if (truthAssumption) {
          possibleStates.add(actState.addSmallerEqConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.add(actState.addGreaterConstraint(rightVarName, leftVarName));
        }
        break;

      case LESS_THAN:
        if (truthAssumption) {
          possibleStates.add(actState.addSmallerConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.add(actState.addGreaterEqConstraint(rightVarName, leftVarName));
        }
        break;

      case NOT_EQUALS:
        if (truthAssumption) {
          possibleStates.addAll(actState.addIneqConstraint(rightVarName, leftVarName));
        } else {
          possibleStates.add(actState.addEqConstraint(rightVarName, leftVarName));
        }
        break;

      default:
        throw new CPATransferException("Unhandled case: " + binExp.getOperator());
      }
    }

    return possibleStates;
  }

  /**
   * This method handles all expressions which are assumptions without beeing
   * binary expressions (p.e if(1) or if(1+2) or if (a))
   */
  private Set<OctState> handleSingleBooleanExpression(String variableName, boolean truthAssumption, OctState state) {
    // if (a)
    if (truthAssumption) {
      return state.addIneqConstraint(variableName, OctNumericValue.ZERO);

      // if (!a)
    } else {
      return Collections.singleton(state.addEqConstraint(variableName, OctNumericValue.ZERO));
    }
  }

  @Override
  protected Set<OctState> handleFunctionCallEdge(CFunctionCallEdge cfaEdge,
      List<CExpression> arguments, List<CParameterDeclaration> parameters,
      String calledFunctionName) throws CPATransferException {

    CFunctionEntryNode functionEntryNode = cfaEdge.getSuccessor();

    List<String> paramNames = functionEntryNode.getFunctionParameterNames();

    if (!cfaEdge.getSuccessor().getFunctionDefinition().getType().takesVarArgs()) {
      assert parameters.size() == arguments.size();
    } else {
      assert parameters.size() <= arguments.size();
    }

    Set<OctState> possibleStates = new HashSet<>();
    state = state.declareVariable(buildVarName(calledFunctionName, FUNCTION_RETURN_VAR),
                          getCorrespondingOctStateType(cfaEdge.getSuccessor().getFunctionDefinition().getType().getReturnType()));

    List<Pair<String, CExpression>> handleAbleParams = new LinkedList<>();

    // declare all parameters as variables
    for (int i = 0; i < parameters.size(); i++) {
      if (!isHandleAbleType(parameters.get(i).getType())) {
        continue;
      }

      String nameOfParam = paramNames.get(i);
      String formalParamName = buildVarName(calledFunctionName, nameOfParam);

      if (!precision.isTracked(formalParamName, parameters.get(i).getType())
          || !isHandleAbleType(parameters.get(i).getType())) {
        continue;
      }

      state = state.declareVariable(formalParamName, getCorrespondingOctStateType(parameters.get(i).getType()));
      handleAbleParams.add(Pair.of(formalParamName, arguments.get(i)));
    }

    possibleStates.add(state);
    for (Pair<String, CExpression> pair : handleAbleParams) {
      String paramName = pair.getFirst();
      CExpression argument = pair.getSecond();

      Set<OctState> newPossibleStates = new HashSet<>();
      for (OctState st : possibleStates) {

        // create the value assigned to the parameter
        COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(st, calledFunctionName);
        Set<Pair<IOctCoefficients, OctState>> coeffsList = argument.accept(coeffVisitor);

        // create new states for all possible parameter values
        for (Pair<IOctCoefficients, OctState> pairs : coeffsList) {
          newPossibleStates.add(pairs.getSecond().makeAssignment(paramName, pairs.getFirst()));
        }
      }
      possibleStates = newPossibleStates;
    }

    return possibleStates;
  }

  @Override
  protected Set<OctState> handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge,
      CFunctionSummaryEdge fnkCall, CFunctionCall summaryExpr, String callerFunctionName)
      throws CPATransferException {
    CFunctionCall exprOnSummary = fnkCall.getExpression();

    String calledFunctionName = cfaEdge.getPredecessor().getFunctionName();

    // expression is an assignment operation, e.g. a = g(b);
    if (exprOnSummary instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement binExp = ((CFunctionCallAssignmentStatement) exprOnSummary);
      CLeftHandSide op1 = binExp.getLeftHandSide();
      String assignedVarName = buildVarName(op1, callerFunctionName);

      // we do not know anything about pointers, so assignments to pointers
      // are not possible for us
      if (!isHandleableVariable(op1)
          || !precision.isTracked(assignedVarName, op1.getExpressionType())) {
        return Collections.singleton(state.removeLocalVars(calledFunctionName));
      }

      String returnVarName = buildVarName(calledFunctionName, FUNCTION_RETURN_VAR);

      IOctCoefficients right = new OctSimpleCoefficients(state.sizeOfVariables(), state.getVariableIndexFor(returnVarName), OctNumericValue.ONE, state);

      state = state.makeAssignment(assignedVarName, right);


    // g(b), do nothing
    } else if (exprOnSummary instanceof CFunctionCallStatement) {

    } else {
      throw new UnrecognizedCCodeException("on function return", cfaEdge, exprOnSummary);
    }

    return Collections.singleton(state.removeLocalVars(calledFunctionName));
  }

  @Override
  protected Set<OctState> handleDeclarationEdge(CDeclarationEdge cfaEdge, CDeclaration decl)
      throws CPATransferException {
    if (cfaEdge.getDeclaration() instanceof CVariableDeclaration) {
      CVariableDeclaration declaration = (CVariableDeclaration) decl;

      // get the variable name in the declarator
      String variableName = declaration.getName();

      // TODO check other types of variables later - just handle primitive
      // types for the moment
      // don't add pointeror struct variables to the list since we don't track them
      if (!isHandleAbleType(declaration.getType())) { return Collections.singleton(state); }

      // make the fullyqualifiedname
      if (!decl.isGlobal()) {
        variableName = buildVarName(functionName, variableName);
      }

      if (!precision.isTracked(variableName, declaration.getType())) {
        return Collections.singleton(state);
      }

      CInitializer init = declaration.getInitializer();

      // for global declarations, there may be forwards declarations, so we do
      // not need to declarate them a second time, but if there is an initializer
      // we assign it to the before declared variable
      if (!state.existsVariable(variableName) && (init == null || init instanceof CInitializerExpression)) {
        state = state.declareVariable(variableName, getCorrespondingOctStateType(declaration.getType()));
      }

      Set<OctState> possibleStates = new HashSet<>();

      if (init != null) {
        if (init instanceof CInitializerExpression) {
          CExpression exp = ((CInitializerExpression) init).getExpression();

          COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, functionName);
          Set<Pair<IOctCoefficients, OctState>> initCoeffs = exp.accept(coeffVisitor);

          for (Pair<IOctCoefficients, OctState> pairs : initCoeffs) {
            possibleStates.add(pairs.getSecond().makeAssignment(variableName, pairs.getFirst()));
          }

          // if there is an initializerlist, the variable is either an array or a struct/union
          // we cannot handle them, so simply return the previous state
        } else if (init instanceof CInitializerList) {
            return Collections.singleton(state);

        } else {
          throw new AssertionError("Unhandled Expression Type: " + init.getClass());
        }

        // global variables without initializer are set to 0 in C
      } else if (decl.isGlobal()) {
        possibleStates.add(state.makeAssignment(variableName, new OctSimpleCoefficients(state.sizeOfVariables(), state).expandToSize(state.sizeOfVariables(), state)));
      }

      if (possibleStates.isEmpty()) {
        possibleStates.add(state);
      }

      return possibleStates;

    } else if (cfaEdge.getDeclaration() instanceof CTypeDeclaration
        || cfaEdge.getDeclaration() instanceof CFunctionDeclaration) { return Collections.singleton(state); }

    throw new AssertionError(cfaEdge.getDeclaration() + " (" + cfaEdge.getDeclaration().getClass() + ")");
  }

  @Override
  protected Set<OctState> handleStatementEdge(CStatementEdge cfaEdge, CStatement statement)
      throws CPATransferException {
    // check if there are functioncalls we cannot handle
    if (statement instanceof CFunctionCall) {
      CExpression fn = ((CFunctionCall)statement).getFunctionCallExpression().getFunctionNameExpression();
      if (fn instanceof CIdExpression) {
        String func = ((CIdExpression)fn).getName();
        if (UNSUPPORTED_FUNCTIONS.containsKey(func)) {
          throw new UnsupportedCCodeException(UNSUPPORTED_FUNCTIONS.get(func), cfaEdge, fn);
        }
      }
    }

    // expression is a binary operation, e.g. a = b;
    if (statement instanceof CAssignment) {
      CLeftHandSide left = ((CAssignment) statement).getLeftHandSide();
      CRightHandSide right = ((CAssignment) statement).getRightHandSide();

      String variableName = buildVarName(left, functionName);

      // as pointers do not get declarated in the beginning we can just
      // ignore them here
      if (!isHandleableVariable(left)
          || !precision.isTracked(variableName, left.getExpressionType())) {
        assert !state.existsVariable(variableName) : "variablename '" + variableName + "' is in map although it can not be handled";
        return Collections.singleton(state);
      } else {
        COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, functionName);
        Set<Pair<IOctCoefficients, OctState>> coeffsList = right.accept(coeffVisitor);

        Set<OctState> possibleStates = new HashSet<>();
        for (Pair<IOctCoefficients, OctState> pairs : coeffsList) {
          possibleStates.add(pairs.getSecond().makeAssignment(variableName, pairs.getFirst()));
        }

        return possibleStates;
      }

      // external function call, or p.e. a;
      // => do nothing
    } else if (statement instanceof CFunctionCallStatement
        || statement instanceof CExpressionStatement) {
      return Collections.singleton(state);

    }

    throw new UnrecognizedCCodeException("unknown statement", cfaEdge, statement);
  }

  private String buildVarName(CLeftHandSide left, String functionName) {
    String variableName = null;
    if (left instanceof CArraySubscriptExpression) {
      variableName = ((CArraySubscriptExpression) left).getArrayExpression().toASTString();

      if (!isGlobal(((CArraySubscriptExpression) left).getArrayExpression())) {
        variableName = buildVarName(functionName, variableName);
      }
    } else if (left instanceof CPointerExpression) {
      variableName = ((CPointerExpression) left).getOperand().toASTString();

      if (!isGlobal(((CPointerExpression) left).getOperand())) {
        variableName = buildVarName(functionName, variableName);
      }
    } else if (left instanceof CFieldReference) {
      variableName = ((CFieldReference) left).getFieldOwner().toASTString();

      if (!isGlobal(((CFieldReference) left).getFieldOwner())) {
        variableName = buildVarName(functionName, variableName);
      }
    } else {
      variableName = ((CIdExpression) left).toASTString();

      if (!isGlobal(left)) {
        variableName = buildVarName(functionName, variableName);
      }
    }

    return variableName;
  }

  /**
   * This is a return statement in a function
   */
  @Override
  protected Set<OctState> handleReturnStatementEdge(CReturnStatementEdge cfaEdge, @Nullable CExpression expression)
      throws CPATransferException {

    // this is for functions without return value, which just have returns
    // in them to end the function
    if (expression == null) {
      return Collections.singleton(state);
    }

    String tempVarName = buildVarName(cfaEdge.getPredecessor().getFunctionName(), FUNCTION_RETURN_VAR);

    // main function has no __cpa_temp_result_var as the result of the main function
    // is not important for us, we skip here
    if (!state.existsVariable(tempVarName)) {
      return Collections.singleton(state);
    }

    Set<OctState> possibleStates = new HashSet<>();
    COctagonCoefficientVisitor coeffVisitor = new COctagonCoefficientVisitor(state, cfaEdge.getPredecessor().getFunctionName());
    Set<Pair<IOctCoefficients, OctState>> coeffsList = expression.accept(coeffVisitor);

    for (Pair<IOctCoefficients, OctState> pairs : coeffsList) {
        possibleStates.add(pairs.getSecond().makeAssignment(tempVarName, pairs.getFirst()));
    }

    return possibleStates;
  }

  /**
   * This edge is the return edge from a function to the caller
   */
  @Override
  protected Set<OctState> handleFunctionSummaryEdge(CFunctionSummaryEdge cfaEdge) throws CPATransferException {
    return Collections.emptySet();
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }

  class COctagonCoefficientVisitor extends DefaultCExpressionVisitor<Set<Pair<IOctCoefficients, OctState>>, CPATransferException>
      implements CRightHandSideVisitor<Set<Pair<IOctCoefficients, OctState>>, CPATransferException> {

    private OctState visitorState;
    private String visitorFunctionName;

    /**
     * This method creates the Visitor, which evaluates all coefficients for a given
     * Expression.
     *
     * @param state
     */
    public COctagonCoefficientVisitor(OctState pState, String pFunctionName) {
      visitorState = pState;
      visitorFunctionName = pFunctionName;
    }

    public OctState getState() {
      return visitorState;
    }


    @Override
    protected Set<Pair<IOctCoefficients, OctState>> visitDefault(CExpression pExp) throws CPATransferException {
      return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CBinaryExpression e) throws CPATransferException {
      Set<Pair<IOctCoefficients, OctState>> left = e.getOperand1().accept(this);
      Set<Pair<IOctCoefficients, Set<Pair<IOctCoefficients, OctState>>>> right = new HashSet<>();


      int origSize = left.size();
      left = FluentIterable.from(left).filter(new NotInstanceOfEmptyCoefficients()).toSet();

      if (left.isEmpty() || origSize != left.size()) {
        return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
      } else {
        for (Pair<IOctCoefficients, OctState> pair : left) {
          Set<Pair<IOctCoefficients, OctState>> tmpRight = e.getOperand2().accept(new COctagonCoefficientVisitor(pair.getSecond(), visitorFunctionName));
          origSize = tmpRight.size();
          tmpRight = FluentIterable.from(tmpRight).filter(new NotInstanceOfEmptyCoefficients()).toSet();
          if (tmpRight.isEmpty() || origSize != tmpRight.size()) {
            return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
          } else {
            right.add(Pair.of(pair.getFirst(), tmpRight));
          }
        }
      }

      switch (e.getOperator()) {
      case BINARY_AND:
      case BINARY_OR:
      case BINARY_XOR:
      case SHIFT_LEFT:
      case SHIFT_RIGHT:
      case MODULO:
        return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
      case EQUALS:
      case GREATER_EQUAL:
      case GREATER_THAN:
      case LESS_EQUAL:
      case LESS_THAN:
      case NOT_EQUALS: {
        Set<Pair<IOctCoefficients, OctState>> returnCoefficients = new HashSet<>(2);
        String tempVarLeft = buildVarName(visitorFunctionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
        temporaryVariableCounter++;

        for (Pair<IOctCoefficients, Set<Pair<IOctCoefficients, OctState>>> pairs : right) {
          IOctCoefficients leftCoeffs = pairs.getFirst();
          for (Pair<IOctCoefficients, OctState> rightPair : pairs.getSecond()) {

            OctState visitorState = rightPair.getSecond().declareVariable(tempVarLeft, getCorrespondingOctStateType(e.getOperand1().getExpressionType()));
            IOctCoefficients rightCoeffs = rightPair.getFirst();
            visitorState = visitorState.makeAssignment(tempVarLeft, leftCoeffs.expandToSize(visitorState.sizeOfVariables(), visitorState));

            rightCoeffs = rightCoeffs.expandToSize(visitorState.sizeOfVariables(), visitorState);
            OctState tmpState;
            switch (e.getOperator()) {
            case EQUALS:
              tmpState = visitorState.addEqConstraint(tempVarLeft, rightCoeffs);
              if (tmpState.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

                // just because we know the value may be equal to the rightcoeffs, it does not
                // have to be equal, so we need to check on smaller/greater rightCoeffs
                // and eventually return more states
                OctState smaller = visitorState.addSmallerConstraint(tempVarLeft, rightCoeffs);
                if (!smaller.isEmpty()) {
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(smaller.sizeOfVariables(), smaller), smaller));
                } else {
                  OctState greater = visitorState.addGreaterConstraint(tempVarLeft, rightCoeffs);
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(greater.sizeOfVariables(), greater), greater));
                }
              }
              break;
            case GREATER_EQUAL:
              tmpState = visitorState.addGreaterEqConstraint(tempVarLeft, rightCoeffs);
              if (tmpState.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));


                // just because we know the value may be greater equal than the rightcoeffs, it does not
                // have to be greater equal, so we need to check on smaller rightCoeffs
                // and eventually return more states
                OctState smaller = visitorState.addSmallerConstraint(tempVarLeft, rightCoeffs);
                if (!smaller.isEmpty()) {
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(smaller.sizeOfVariables(), smaller), smaller));
                }
              }
              break;
            case GREATER_THAN:
              tmpState = visitorState.addGreaterConstraint(tempVarLeft, rightCoeffs);
              if (tmpState.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

                // just because we know the value may be greater than the rightcoeffs, it does not
                // have to be greater, so we need to check on smaller equal rightCoeffs
                // and eventually return more states
                OctState smaller = visitorState.addSmallerEqConstraint(tempVarLeft, rightCoeffs);
                if (!smaller.isEmpty()) {
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(smaller.sizeOfVariables(), smaller), smaller));
                }
              }
              break;
            case LESS_EQUAL:
              tmpState = visitorState.addSmallerEqConstraint(tempVarLeft, rightCoeffs);
              if (tmpState.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

                // just because we know the value may be smaller equal than the rightcoeffs, it does not
                // have to be smaller equal, so we need to check on greater rightCoeffs
                // and eventually return more states
                OctState greater = visitorState.addGreaterConstraint(tempVarLeft, rightCoeffs);
                if (!greater.isEmpty()) {
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(greater.sizeOfVariables(), greater), greater));
                }
              }
              break;
            case LESS_THAN:
              tmpState = visitorState.addSmallerConstraint(tempVarLeft, rightCoeffs);
              if (tmpState.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

                // just because we know the value may be smaller than the rightcoeffs, it does not
                // have to be smaller, so we need to check on greater equal rightCoeffs
                // and eventually return more states
                OctState greater = visitorState.addGreaterEqConstraint(tempVarLeft, rightCoeffs);
                if (!greater.isEmpty()) {
                  returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(greater.sizeOfVariables(), greater), greater));
                }
              }
              break;
            case NOT_EQUALS:
              OctState smaller = visitorState.addSmallerConstraint(tempVarLeft, rightCoeffs);
              OctState bigger = visitorState.addGreaterConstraint(tempVarLeft, rightCoeffs);
              OctState equal = visitorState.addEqConstraint(tempVarLeft, rightCoeffs);
              if ((!smaller.isEmpty() || !bigger.isEmpty()) && equal.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else if ((!smaller.isEmpty() || !bigger.isEmpty()) && !equal.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              } else {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
              }

              if (!smaller.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(smaller.sizeOfVariables(), smaller), smaller));
              }

              if (!bigger.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(bigger.sizeOfVariables(), bigger), bigger));
              }

              if (!equal.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(equal.sizeOfVariables(), equal), equal));
              }
              break;
            }
          }
        }
        return returnCoefficients;
      }
      case DIVIDE:
      case MULTIPLY:
      case MINUS:
      case PLUS: {
        Set<Pair<IOctCoefficients, OctState>> returnCoefficients = new HashSet<>();
        for (Pair<IOctCoefficients, Set<Pair<IOctCoefficients, OctState>>> pairs : right) {
          IOctCoefficients leftCoeffs = pairs.getFirst();
          for (Pair<IOctCoefficients, OctState> rightPair : pairs.getSecond()) {
            IOctCoefficients rightCoeffs = rightPair.getFirst();
            OctState visitorState = rightPair.getSecond();

            if (leftCoeffs.size() < rightCoeffs.size()) {
              leftCoeffs = leftCoeffs.expandToSize(rightCoeffs.size(), visitorState);
            } else {
              rightCoeffs = rightCoeffs.expandToSize(leftCoeffs.size(), visitorState);
            }
            if (e.getOperator() == BinaryOperator.MINUS) {
              returnCoefficients.add(Pair.of(leftCoeffs.sub(rightCoeffs), visitorState));
            } else if (e.getOperator() == BinaryOperator.PLUS) {
              returnCoefficients.add(Pair.of(leftCoeffs.add(rightCoeffs), visitorState));

              // TODO these are some more or less untested optimizations which should mostly
              // be necessary for floats, after some testing this should be enabled by default
            } else if (e.getOperator() == BinaryOperator.MULTIPLY) {
              if (!handleFloats) {
                return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, this.visitorState));
              }

              returnCoefficients.add(Pair.of(leftCoeffs.mul(rightCoeffs), visitorState));

            } else if (e.getOperator() == BinaryOperator.DIVIDE) {
              if(!handleFloats) {
                return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, this.visitorState));
              }

              returnCoefficients.add(Pair.of(leftCoeffs.div(rightCoeffs), visitorState));
            }
          }
        }

        return returnCoefficients;
      }
      default:
        throw new AssertionError("Unhandled case statement");
      }
    }

    /**
     * Only unpack the cast and continue with the casts operand
     */
    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CCastExpression e) throws CPATransferException {
      return e.getOperand().accept(this);
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CIdExpression e) throws CPATransferException {
      String varName = buildVarName(e, functionName);
      Integer varIndex = visitorState.getVariableIndexFor(varName);
      // TODO following if-part may be imprecise, check again
      if (varIndex == -1) {
        varName = buildVarName(e, visitorFunctionName);
        varIndex = visitorState.getVariableIndexFor(varName);
      }

      if (varIndex == -1) { return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState)); }
      return Collections.singleton(Pair.of((IOctCoefficients)new OctSimpleCoefficients(visitorState.sizeOfVariables(), varIndex, OctNumericValue.ONE, visitorState), visitorState));
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CCharLiteralExpression e) throws CPATransferException {
      return Collections.singleton(Pair.of((IOctCoefficients)new OctSimpleCoefficients(visitorState.sizeOfVariables(), new OctNumericValue(e.getValue()), visitorState), visitorState));
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CFloatLiteralExpression e) throws CPATransferException {
      // only handle floats when specified in the configuration
      if (handleFloats) {
        return Collections.singleton(Pair.of((IOctCoefficients)new OctSimpleCoefficients(visitorState.sizeOfVariables(), new OctNumericValue(e.getValue()), visitorState), visitorState));
      } else {
        return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
      }
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CIntegerLiteralExpression e) throws CPATransferException {
      return Collections.singleton(Pair.of((IOctCoefficients)new OctSimpleCoefficients(visitorState.sizeOfVariables(), new OctNumericValue(e.asLong()), visitorState), visitorState));
    }

    @SuppressWarnings("deprecation")
    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CUnaryExpression e) throws CPATransferException {
      Set<Pair<IOctCoefficients, OctState>> operand = e.getOperand().accept(this);

      operand = FluentIterable.from(operand).filter(new NotInstanceOfEmptyCoefficients()).toSet();

      if (operand.isEmpty()) {
        return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
      }

      switch (e.getOperator()) {
      case AMPER:
      case SIZEOF:
      case TILDE:
        return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
      case MINUS:
        final Set<Pair<IOctCoefficients, OctState>> returnCoefficients = new HashSet<>();

        // we filter out all coefficients which do not only have a constant value
        // and at the same time we add the returnCoefficients for those who only have
        // a constant value
        operand = FluentIterable.from(operand).filter(new Predicate<Pair<IOctCoefficients, OctState>>() {
          @Override
          public boolean apply(Pair<IOctCoefficients, OctState> pair) {
            IOctCoefficients coeffs = pair.getFirst();
            if (coeffs.hasOnlyConstantValue()) {
              if (coeffs instanceof OctSimpleCoefficients) {
                returnCoefficients.add(Pair.of((IOctCoefficients)new OctSimpleCoefficients(coeffs.size(), ((OctSimpleCoefficients) coeffs).getConstantValue().mul(-1), pair.getSecond()), pair.getSecond()));
                return false;
              } else if (coeffs instanceof OctIntervalCoefficients) {
                Pair<Pair<OctNumericValue, Boolean>, Pair<OctNumericValue, Boolean>> bounds = ((OctIntervalCoefficients) coeffs).getConstantValue();
                returnCoefficients.add(Pair.of((IOctCoefficients)new OctIntervalCoefficients(coeffs.size(),
                                                                   bounds.getSecond().getFirst().mul(-1),
                                                                   bounds.getFirst().getFirst().mul(-1),
                                                                   bounds.getSecond().getSecond(),
                                                                   bounds.getFirst().getSecond(),
                                                                   pair.getSecond()),
                                                pair.getSecond()));
                return false;
              }
            }
            return true;
          }
        }).toSet();

        if (operand.isEmpty()) {
          return returnCoefficients;
        }

        String tempVar = buildVarName(visitorFunctionName, TEMP_VAR_PREFIX + temporaryVariableCounter + "_");
        temporaryVariableCounter++;

        for (Pair<IOctCoefficients, OctState> pair : operand) {
          IOctCoefficients coeffs = pair.getFirst();
          OctState visitorState = pair.getSecond();
          visitorState = visitorState.declareVariable(tempVar, getCorrespondingOctStateType(e.getExpressionType()));
          visitorState = visitorState.makeAssignment(tempVar, coeffs.expandToSize(visitorState.sizeOfVariables(), visitorState));

          if (e.getOperator() == UnaryOperator.MINUS) {
            returnCoefficients.add(Pair.of((IOctCoefficients)new OctSimpleCoefficients(visitorState.sizeOfVariables(),
                                                                                       visitorState.getVariableIndexFor(tempVar),
                                                                                       new OctNumericValue(-1), visitorState),
                                            visitorState));
          } else {

            OctState tmpState = visitorState.addEqConstraint(tempVar, OctNumericValue.ZERO);
            if (tmpState.isEmpty()) {
              returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

            } else {
              returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolTRUECoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));

              // just because we know the value may be zero, it does not
              // have to be zero, so we need to check on smaller/greater zero
              // and eventually return more states
              OctState smaller = visitorState.addSmallerConstraint(tempVar, OctNumericValue.ZERO);
              if (!smaller.isEmpty()) {
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(smaller.sizeOfVariables(), smaller), smaller));
              } else {
                OctState greater = visitorState.addGreaterConstraint(tempVar, OctNumericValue.ZERO);
                returnCoefficients.add(Pair.of((IOctCoefficients)OctSimpleCoefficients.getBoolFALSECoeffs(greater.sizeOfVariables(), greater), greater));
              }
            }
          }
        }
        return returnCoefficients;
      default:
        throw new AssertionError("Unhandled case in switch clause.");
      }
    }

    @Override
    public Set<Pair<IOctCoefficients, OctState>> visit(CFunctionCallExpression e) throws CPATransferException {
      if (e.getFunctionNameExpression() instanceof CIdExpression) {
        String functionName = ((CIdExpression)e.getFunctionNameExpression()).getName();
        if (functionName.equals("__VERIFIER_nondet_uint")) {
          return Collections.singleton(Pair.of((IOctCoefficients)OctIntervalCoefficients.getNondetUIntCoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
        } else if (functionName.equals("__VERIFIER_nondet_bool")) {
          return Collections.singleton(Pair.of((IOctCoefficients)OctIntervalCoefficients.getNondetBoolCoeffs(visitorState.sizeOfVariables(), visitorState), visitorState));
        }
      }
      return Collections.singleton(Pair.of((IOctCoefficients)OctEmptyCoefficients.INSTANCE, visitorState));
    }
  }

  /**
   * Predicate implementation which filters out OctEmptyCoefficients of a given fluentiterable.
   */
  static class NotInstanceOfEmptyCoefficients implements Predicate<Pair<IOctCoefficients, OctState>> {
    @Override
    public boolean apply(Pair<IOctCoefficients, OctState> pInput) {
      return !(pInput.getFirst() instanceof OctEmptyCoefficients);
    }
  }
}
