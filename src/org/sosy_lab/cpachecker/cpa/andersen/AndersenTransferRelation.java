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
package org.sosy_lab.cpachecker.cpa.andersen;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.IASTAssignment;
import org.sosy_lab.cpachecker.cfa.ast.IASTCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IASTStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.IASTVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.andersen.util.BaseConstraint;
import org.sosy_lab.cpachecker.cpa.andersen.util.ComplexConstraint;
import org.sosy_lab.cpachecker.cpa.andersen.util.SimpleConstraint;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

@Options(prefix = "cpa.pointerA")
public class AndersenTransferRelation implements TransferRelation {

  public AndersenTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  @Override
  public Collection<AbstractState> getAbstractSuccessors(AbstractState element, Precision pPrecision,
      CFAEdge cfaEdge)
      throws CPATransferException {

    AbstractState successor = null;
    AndersenElement andersenElement = (AndersenElement) element;

    // check the type of the edge
    switch (cfaEdge.getEdgeType()) {

    // if edge is a statement edge, e.g. a = b + c
    case StatementEdge:
      StatementEdge statementEdge = (StatementEdge) cfaEdge;
      successor = handleStatement(andersenElement, statementEdge.getStatement(), cfaEdge);
      break;

    // edge is a declaration edge, e.g. int a;
    case DeclarationEdge:
      DeclarationEdge declarationEdge = (DeclarationEdge) cfaEdge;
      successor = handleDeclaration(andersenElement, declarationEdge);
      break;

    // this is an assumption, e.g. if(a == b)
    case AssumeEdge:
      successor = andersenElement.clone();
      break;

    case BlankEdge:
      successor = andersenElement.clone();
      break;

    default:
      printWarning(cfaEdge);
    }

    if (successor == null)
      return Collections.emptySet();

    else
      return Collections.singleton(successor);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements,
      CFAEdge cfaEdge, Precision precision)
      throws UnrecognizedCCodeException {

    return null;
  }

  private AndersenElement handleStatement(AndersenElement element, IASTStatement expression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException {

    // e.g. a = b;
    if (expression instanceof IASTAssignment)
      return handleAssignment(element, (IASTAssignment) expression, cfaEdge);

    // external function call - do nothing
    else if (expression instanceof IASTFunctionCallStatement)
      return element.clone();

    else if (expression instanceof IASTExpressionStatement)
      return element.clone();

    else
      throw new UnrecognizedCCodeException(cfaEdge, expression);
  }

  private AndersenElement handleAssignment(AndersenElement element, IASTAssignment assignExpression, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException {

    IASTExpression op1 = assignExpression.getLeftHandSide();
    IASTRightHandSide op2 = assignExpression.getRightHandSide();

    if (op1 instanceof IASTIdExpression) {

      // a = ...

      return handleAssignmentTo(op1.toASTString(), op2, element, cfaEdge);

    } else if (op1 instanceof IASTUnaryExpression && ((IASTUnaryExpression) op1).getOperator() == UnaryOperator.STAR
        && op2 instanceof IASTIdExpression) {

      // *a = b; complex constraint

      op1 = ((IASTUnaryExpression) op1).getOperand();

      if (op1 instanceof IASTIdExpression) {

        AndersenElement succ = element.clone();
        succ.addConstraint(new ComplexConstraint(op2.toASTString(), op1.toASTString(), false));
        return succ;

      } else
        throw new UnrecognizedCCodeException("not supported", cfaEdge, op2);

    } else
      throw new UnrecognizedCCodeException("not supported", cfaEdge, op1);
  }

  /**
   * Handles an assignement of the form <code>op1 = ...</code> to a given variable <code>op1</code>.
   *
   * @param op1
   *        Name of the lefthandside variable in the assignement <code>op1 = ...</code>.
   * @param op2
   *        Righthandside of the assignement.
   * @param element
   *        Predecessor of this assignement's AndersonElement.
   * @param cfaEdge
  *          Corresponding edge of the CFA.
   * @return <code>element</code>'s successor.
   *
   * @throws UnrecognizedCCodeException
   */
  private AndersenElement handleAssignmentTo(String op1, IASTRightHandSide op2, AndersenElement element, CFAEdge cfaEdge)
      throws UnrecognizedCCodeException {

    // unpack cast if necessary
    while (op2 instanceof IASTCastExpression)
      op2 = ((IASTCastExpression) op2).getOperand();

    if (op2 instanceof IASTIdExpression) {

      // a = b; simple constraint

      AndersenElement succ = element.clone();
      succ.addConstraint(new SimpleConstraint(op2.toASTString(), op1));
      return succ;

    } else if (op2 instanceof IASTUnaryExpression && ((IASTUnaryExpression) op2).getOperator() == UnaryOperator.AMPER) {

      // a = &b; base constraint

      op2 = ((IASTUnaryExpression) op2).getOperand();

      if (op2 instanceof IASTIdExpression) {

        AndersenElement succ = element.clone();
        succ.addConstraint(new BaseConstraint(op2.toASTString(), op1));
        return succ;

      } else
        throw new UnrecognizedCCodeException("not supported", cfaEdge, op2);

    } else if (op2 instanceof IASTUnaryExpression && ((IASTUnaryExpression) op2).getOperator() == UnaryOperator.STAR) {

      // a = *b; complex constraint

      op2 = ((IASTUnaryExpression) op2).getOperand();

      if (op2 instanceof IASTIdExpression) {

        AndersenElement succ = element.clone();
        succ.addConstraint(new ComplexConstraint(op2.toASTString(), op1, true));
        return succ;

      } else
        throw new UnrecognizedCCodeException("not supported", cfaEdge, op2);

    } else if (op2 instanceof IASTFunctionCallExpression
        && "malloc".equals(((IASTFunctionCallExpression) op2).getFunctionNameExpression().toASTString())) {

      AndersenElement succ = element.clone();
      succ.addConstraint(new BaseConstraint("malloc-" + cfaEdge.getLineNumber(), op1));
      return succ;

    }

    // not implemented, or not interessing
    printWarning(cfaEdge);
    return element.clone();
  }

  private AndersenElement handleDeclaration(AndersenElement element, DeclarationEdge declarationEdge)
      throws UnrecognizedCCodeException {

    if (!(declarationEdge.getDeclaration() instanceof IASTVariableDeclaration)) {
      // nothing interesting to see here, please move along
      return element.clone();
    }

    IASTVariableDeclaration decl = (IASTVariableDeclaration) declarationEdge.getDeclaration();

    // get the variable name in the declarator
    String varName = decl.getName();

    // get initial value
    IASTInitializer init = decl.getInitializer();
    if (init instanceof IASTInitializerExpression) {

      IASTRightHandSide exp = ((IASTInitializerExpression) init).getExpression();

      return handleAssignmentTo(varName, exp, element, declarationEdge);
    }

    return element.clone();
  }

  /**
   * Prints a warning to System.err that the statement corresponding to the given
   * <code>cfaEdge</code> was not handled.
   */
  private void printWarning(CFAEdge cfaEdge) {

    StackTraceElement[] trace = Thread.currentThread().getStackTrace();

    System.err.println("Warning! CFA Edge \"" + cfaEdge.getRawStatement() + "\" (line: " + cfaEdge.getLineNumber()
        + ") not handled. [Method: " + trace[2].toString() + ']');
  }
}
