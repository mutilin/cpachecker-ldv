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
package org.sosy_lab.cpachecker.util.predicates.pathformula.withUF;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCCodeException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ErrorConditions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.Constraints;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.ExternModelLoader;
import org.sosy_lab.cpachecker.util.predicates.pathformula.withUF.Expression.Value;


class StatementToFormulaWithUFVisitor extends ExpressionToFormulaWithUFVisitor
                                             implements CStatementVisitor<BooleanFormula, UnrecognizedCCodeException>,
                                                        CRightHandSideVisitor<Expression, UnrecognizedCCodeException> {

  public StatementToFormulaWithUFVisitor(final CToFormulaWithUFConverter cToFormulaConverter,
                                         final CFAEdge cfaEdge,
                                         final String function,
                                         final SSAMapBuilder ssa,
                                         final Constraints constraints,
                                         final ErrorConditions errorConditions,
                                         final PointerTargetSetBuilder pts) {
    super(cToFormulaConverter, cfaEdge, function, ssa, constraints, errorConditions, pts);
  }

  @Override
  public BooleanFormula visit(final CExpressionAssignmentStatement e) throws UnrecognizedCCodeException {
    return visit((CAssignment) e);
  }

  @Override
  public BooleanFormula visit(final CFunctionCallAssignmentStatement e) throws UnrecognizedCCodeException {
    return visit((CAssignment) e);
  }

  @Override
  public BooleanFormula visit(CExpressionStatement s) {
    return conv.bfmgr.makeBoolean(true);
  }

  @Override
  public BooleanFormula visit(CFunctionCallStatement exp) throws UnrecognizedCCodeException {
    // this is an external call
    // visit expression in order to print warnings if necessary
    visit(exp.getFunctionCallExpression());
    return conv.bfmgr.makeBoolean(true);
  }

  private BooleanFormula visit(final CAssignment e) throws UnrecognizedCCodeException {
    AssignmentHandler assignmentHandler = new AssignmentHandler(conv, edge, function, ssa, pts, constraints, errorConditions);
    return assignmentHandler.handleAssignment(e.getLeftHandSide(), e.getRightHandSide(), false, null);
  }

  public BooleanFormula visitAssume(final CExpression e, final boolean truthAssumtion)
  throws UnrecognizedCCodeException {
    final CType expressionType = CTypeUtils.simplifyType(e.getExpressionType());
    BooleanFormula result = conv.toBooleanFormula(asValueFormula(e.accept(this),
                                                                 expressionType));

    if (conv.options.deferUntypedAllocations()) {
      DynamicMemoryHandler memoryHandler = new DynamicMemoryHandler(conv, edge, ssa, pts, constraints, errorConditions);
      memoryHandler.handleDeferredAllocationsInAssume(e, getUsedDeferredAllocationPointers());
    }

    if (!truthAssumtion) {
      result = conv.bfmgr.not(result);
    }

    pts.addEssentialFields(getInitializedFields());
    pts.addEssentialFields(getUsedFields());
    return result;
  }

  @Override
  public Value visit(final CFunctionCallExpression e) throws UnrecognizedCCodeException {
    final CExpression functionNameExpression = e.getFunctionNameExpression();
    final CType returnType = CTypeUtils.simplifyType(e.getExpressionType());
    final List<CExpression> parameters = e.getParameterExpressions();

    // First let's handle special cases such as assumes, allocations, nondets, external models, etc.
    final String functionName;
    if (functionNameExpression instanceof CIdExpression) {
      functionName = ((CIdExpression) functionNameExpression).getName();
      if (functionName.equals(CToFormulaWithUFConverter.ASSUME_FUNCTION_NAME) && parameters.size() == 1) {
        final BooleanFormula condition = visitAssume(parameters.get(0), true);
        constraints.addConstraint(condition);
        return Value.ofValue(conv.makeFreshVariable(functionName, returnType, ssa));

      } else if (conv.options.isDynamicMemoryFunction(functionName)) {
        DynamicMemoryHandler memoryHandler = new DynamicMemoryHandler(conv, edge, ssa, pts, constraints, errorConditions);
        return memoryHandler.handleDynamicMemoryFunction(e, functionName, this);

      } else if (conv.options.isNondetFunction(functionName)) {
        return Value.nondetValue();

      } else if (conv.options.isExternModelFunction(functionName)) {
        ExternModelLoader loader = new ExternModelLoader(conv.typeHandler, conv.bfmgr, conv.fmgr);
        BooleanFormula result = loader.handleExternModelFunction(e, parameters, ssa);
        FormulaType<?> returnFormulaType = conv.getFormulaTypeFromCType(e.getExpressionType());
        return Value.ofValue(conv.ifTrueThenOneElseZero(returnFormulaType, result));

      } else if (CtoFormulaConverter.UNSUPPORTED_FUNCTIONS.containsKey(functionName)) {
        throw new UnsupportedCCodeException(CtoFormulaConverter.UNSUPPORTED_FUNCTIONS.get(functionName), edge, e);

      } else if (!CtoFormulaConverter.PURE_EXTERNAL_FUNCTIONS.contains(functionName)) {
        if (parameters.isEmpty()) {
          // function of arity 0
          conv.logger.logfOnce(Level.INFO,
                               "Assuming external function %s to be a constant function.",
                               functionName);
        } else {
          conv.logger.logfOnce(Level.INFO,
                               "Assuming external function %s to be a pure function.",
                               functionName);
        }
      }
    } else {
      conv.logger.logfOnce(Level.WARNING,
                           "Ignoring function call through function pointer %s",
                           e);
      functionName = "<func>{" +
                     CtoFormulaConverter.scoped(CtoFormulaConverter.exprToVarName(functionNameExpression),
                                                function) +
                     "}";
    }

    // Pure functions returning composites are unsupported, return a nondet value
    final CType resultType = CTypeUtils.simplifyType(conv.getReturnType(e, edge));
    if (resultType instanceof CCompositeType ||
        CTypeUtils.containsArray(resultType)) {
      conv.logger.logfOnce(Level.WARNING,
                           "Pure function %s returning a composite is treated as nondet.", e);
      return Value.nondetValue();
    }

    // Now let's handle "normal" functions assumed to be pure
    if (parameters.isEmpty()) {
      // This is a function of arity 0 and we assume its constant.
      return Value.ofValue(conv.makeConstant(CToFormulaWithUFConverter.UF_NAME_PREFIX + functionName, returnType));
    } else {
      final CFunctionDeclaration functionDeclaration = e.getDeclaration();
      if (functionDeclaration == null) {
        if (functionNameExpression instanceof CIdExpression) {
          // This happens only if there are undeclared functions.
          conv.logger.logfOnce(Level.WARNING, "Cannot get declaration of function %s, ignoring calls to it.",
                               functionNameExpression);
        }
        return Value.nondetValue();
      }

      if (functionDeclaration.getType().takesVarArgs()) {
        // Return nondet instead of an UF for vararg functions.
        // This is sound but slightly more imprecise (we loose the UF axioms).
        return Value.nondetValue();
      }

      final List<CType> formalParameterTypes = functionDeclaration.getType().getParameters();
      // functionName += "{" + parameterTypes.size() + "}";
      // add #arguments to function name to cope with vararg functions
      // TODO: Handled above?
      if (formalParameterTypes.size() != parameters.size()) {
        throw new UnrecognizedCCodeException("Function " + functionDeclaration + " received " +
                                             parameters.size() + " parameters instead of the expected " +
                                             formalParameterTypes.size(),
                                             edge,
                                             e);
      }

      final List<Formula> arguments = new ArrayList<>(parameters.size());
      final Iterator<CType> formalParameterTypesIterator = formalParameterTypes.iterator();
      final Iterator<CExpression> parametersIterator = parameters.iterator();
      while (formalParameterTypesIterator.hasNext() && parametersIterator.hasNext()) {
        final CType formalParameterType = CTypeUtils.simplifyType(formalParameterTypesIterator.next());
        CExpression parameter = parametersIterator.next();
        parameter = conv.makeCastFromArrayToPointerIfNecessary(parameter, formalParameterType);

        final CType actualParameterType = CTypeUtils.simplifyType(parameter.getExpressionType());
        final Formula argument = asValueFormula(parameter.accept(this), actualParameterType);
        arguments.add(conv.makeCast(actualParameterType, formalParameterType, argument, edge));
      }
      assert !formalParameterTypesIterator.hasNext() && !parametersIterator.hasNext();

      final FormulaType<?> resultFormulaType = conv.getFormulaTypeFromCType(resultType);
      return Value.ofValue(conv.ffmgr.createFuncAndCall(CToFormulaWithUFConverter.UF_NAME_PREFIX + functionName,
                                                        resultFormulaType,
                                                        arguments));
    }
  }
}
