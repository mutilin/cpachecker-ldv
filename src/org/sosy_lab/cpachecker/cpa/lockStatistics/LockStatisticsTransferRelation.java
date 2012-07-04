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
package org.sosy_lab.cpachecker.cpa.lockStatistics;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.IASTArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTAssignment;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.IASTVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.MultiEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  private final Set<String> globalMutex = new HashSet<String>();

  PrintWriter zzz = null;
  FileOutputStream file = null;

  public LockStatisticsTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    try {
      file = new FileOutputStream ("output/01.txt");
      zzz = new PrintWriter(file);
      zzz.close();
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла 01.txt");
      System.exit(0);
    }
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision, CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsPrecision lockStatisticsPrecision = (LockStatisticsPrecision)pPrecision;

    LockStatisticsState successor;

    switch (cfaEdge.getEdgeType()) {

    case FunctionCallEdge:
      lockStatisticsElement.setGlobalLocks();
      successor = lockStatisticsElement.clone();
      break;

    case FunctionReturnEdge:
      FunctionReturnEdge functionReturnEdge = (FunctionReturnEdge) cfaEdge;
      lockStatisticsElement.setLocalLocks();
      successor = handleFunctionReturn(lockStatisticsElement, functionReturnEdge);
      break;

    default:
      successor = handleSimpleEdge(lockStatisticsElement, lockStatisticsPrecision, cfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, LockStatisticsPrecision precision, CFAEdge cfaEdge)
        throws CPATransferException {

    switch(cfaEdge.getEdgeType()) {
    case StatementEdge:
      StatementEdge statementEdge = (StatementEdge) cfaEdge;
      return handleStatement(element, statementEdge.getStatement(), cfaEdge, precision);

    case BlankEdge:
    case ReturnStatementEdge:
      return element;

    case AssumeEdge:
      return element;
      //throw new UnrecognizedCFAEdgeException(cfaEdge);

    case DeclarationEdge:
      DeclarationEdge declarationEdge = (DeclarationEdge) cfaEdge;
      return handleDeclaration(element, declarationEdge, precision, cfaEdge.getLineNumber());

    case MultiEdge:
      for (CFAEdge edge : (MultiEdge)cfaEdge) {
        return handleSimpleEdge(element, precision, edge);
      }
      break;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
    return element;
  }

  private LockStatisticsState handleFunctionReturn(LockStatisticsState element, FunctionReturnEdge functionReturnEdge)
    throws UnrecognizedCCodeException {
    CallToReturnEdge summaryEdge    = functionReturnEdge.getSuccessor().getEnteringSummaryEdge();
    IASTFunctionCall exprOnSummary  = summaryEdge.getExpression();

    if(exprOnSummary instanceof IASTFunctionCallAssignmentStatement) {
      IASTFunctionCallAssignmentStatement assignExp = ((IASTFunctionCallAssignmentStatement)exprOnSummary);
      IASTExpression op1 = assignExp.getLeftHandSide();

      printStat (element, functionReturnEdge.getLineNumber(), op1.toASTString());
    }

    return element;
  }

  private LockStatisticsState handleStatement(LockStatisticsState newElement, IASTStatement expression, CFAEdge cfaEdge, LockStatisticsPrecision precision)
    throws UnrecognizedCCodeException {

    if (expression instanceof IASTAssignment) {
      return handleAssignment(newElement, (IASTAssignment)expression, cfaEdge, precision);

    } else if (expression instanceof IASTFunctionCallStatement) {

      String functionName = ((IASTFunctionCallStatement) expression).getFunctionCallExpression().getFunctionNameExpression().toASTString();
      List <IASTExpression> params = ((IASTFunctionCallStatement) expression).getFunctionCallExpression().getParameterExpressions();
      if (functionName == "mutex_lock_nested") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
        newElement.add(paramName);
        if (globalMutex.contains(paramName))
          newElement.addGlobal(paramName);

      }
      else if (functionName == "mutex_unlock") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
        newElement.delete(paramName);
        if (globalMutex.contains(paramName))
          newElement.deleteGlobal(paramName);
      }
      return newElement;

    } else if (expression instanceof IASTExpressionStatement) {
      return newElement;
    } else {
      throw new UnrecognizedCCodeException(cfaEdge, expression);
    }
  }

  private LockStatisticsState handleAssignment(LockStatisticsState newElement, IASTAssignment assignExpression, CFAEdge cfaEdge, LockStatisticsPrecision precision)
    throws UnrecognizedCCodeException {
    IASTExpression op1    = assignExpression.getLeftHandSide();

    if(op1 instanceof IASTIdExpression ||
       op1 instanceof IASTUnaryExpression && ((IASTUnaryExpression)op1).getOperator() == UnaryOperator.STAR ||
       op1 instanceof IASTFieldReference ||
       op1 instanceof IASTArraySubscriptExpression) {

      printStat (newElement, cfaEdge.getLineNumber(), op1.toASTString());
      return newElement;
    } else {
      throw new UnrecognizedCCodeException("left operand of assignment has to be a variable", cfaEdge, op1);
    }
  }

  private LockStatisticsState handleDeclaration(LockStatisticsState newElement, DeclarationEdge declarationEdge, LockStatisticsPrecision precision, int line)
      throws UnrecognizedCCodeException {

      if (!(declarationEdge.getDeclaration() instanceof IASTVariableDeclaration)) {
        return newElement;
      }

      IASTVariableDeclaration decl = (IASTVariableDeclaration)declarationEdge.getDeclaration();

      String varName = decl.getName();
      String typeName = decl.getDeclSpecifier().toASTString(null);

      if(decl.isGlobal() && typeName.contentEquals("mutex null")) {
        globalMutex.add(varName);
        return newElement;
      }

      IASTInitializer init = decl.getInitializer();
      if(init instanceof IASTInitializerExpression) {
        printStat (newElement, line, varName);
      }

      return newElement;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {
    return null;
  }

  private void printStat (LockStatisticsState element, int line, String name) {
    try {
      file = new FileOutputStream ("output/01.txt", true);
      zzz = new PrintWriter(file);
      zzz.print(line);
      zzz.print(": ");
      zzz.print(name);
      zzz.print("  ");
      zzz.println(element.print());
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла 01.txt");
      System.exit(0);
    } finally {
      if(file != null)
          zzz.close();
    }
  }
}
