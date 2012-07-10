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
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CNamedType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType.ElaboratedType;
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
      file = new FileOutputStream ("output/race_results.txt");
      zzz = new PrintWriter(file);
      zzz.close();
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла race_results.txt");
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
      CFunctionReturnEdge functionReturnEdge = (CFunctionReturnEdge) cfaEdge;
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
      CStatementEdge statementEdge = (CStatementEdge) cfaEdge;
      return handleStatement(element, statementEdge.getStatement(), cfaEdge, precision);

    case BlankEdge:
    case ReturnStatementEdge:
      return element.clone();

    case AssumeEdge:
      //TODO it can be pointer here!
      return element.clone();
      //throw new UnrecognizedCFAEdgeException(cfaEdge);

    case DeclarationEdge:
      CDeclarationEdge declarationEdge = (CDeclarationEdge) cfaEdge;
      return handleDeclaration(element.clone(), declarationEdge, precision, cfaEdge.getLineNumber());

    case MultiEdge:
      LockStatisticsState tmpElement = element.clone();

      for (CFAEdge edge : (MultiEdge)cfaEdge) {
        tmpElement = handleSimpleEdge(tmpElement, precision, edge);
      }
      return tmpElement;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
  }

  private LockStatisticsState handleFunctionReturn(LockStatisticsState element, CFunctionReturnEdge functionReturnEdge)
    throws UnrecognizedCCodeException {
    CFunctionSummaryEdge summaryEdge    = functionReturnEdge.getSummaryEdge();
    CFunctionCall exprOnSummary  = summaryEdge.getExpression();

    LockStatisticsState newElement = element.clone();

    if(exprOnSummary instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement assignExp = ((CFunctionCallAssignmentStatement)exprOnSummary);
      CExpression op1 = assignExp.getLeftHandSide();

      printStat (newElement, functionReturnEdge.getLineNumber(), op1.toASTString());
    }

    return newElement;
  }

  private LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression, CFAEdge cfaEdge, LockStatisticsPrecision precision)
    throws UnrecognizedCCodeException {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      return handleAssignment(newElement, (CAssignment)expression, cfaEdge, precision);
    }
    else if (expression instanceof CFunctionCallStatement) {

      String functionName = ((CFunctionCallStatement) expression).getFunctionCallExpression().getFunctionNameExpression().toASTString();
      List <CExpression> params = ((CFunctionCallStatement) expression).getFunctionCallExpression().getParameterExpressions();
      //System.out.print(functionName + "\n");
      if (functionName == "mutex_lock_nested") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
        newElement.add(paramName);
        if (globalMutex.contains(paramName))
          newElement.addGlobal(paramName);
        else
        //add mutex as local
          newElement.add(paramName);
      }
      else if (functionName == "mutex_unlock") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
        newElement.delete(paramName);
        if (globalMutex.contains(paramName))
          newElement.deleteGlobal(paramName);
        else
        //delete local mutex
          newElement.delete(paramName);
      }
      return newElement;

    } else if (expression instanceof CExpressionStatement) {
      return newElement;
    } else {
      throw new UnrecognizedCCodeException(cfaEdge, expression);
    }
  }

  private LockStatisticsState handleAssignment(LockStatisticsState newElement, CAssignment assignExpression, CFAEdge cfaEdge, LockStatisticsPrecision precision)
    throws UnrecognizedCCodeException {
    CExpression op1    = assignExpression.getLeftHandSide();

    if (CheckVariableToSave(op1, false))
      printStat (newElement, cfaEdge.getLineNumber(), op1.toASTString());

    CRightHandSide op2    = assignExpression.getRightHandSide();

    if (CheckVariableToSave(op2, false))
      printStat (newElement, cfaEdge.getLineNumber(), op2.toASTString());

    return newElement;
  }

  private LockStatisticsState handleDeclaration(LockStatisticsState newElement, CDeclarationEdge declarationEdge, LockStatisticsPrecision precision, int line)
      throws UnrecognizedCCodeException {

      if (!(declarationEdge.getDeclaration() instanceof CVariableDeclaration)) {
        return newElement;
      }

      CVariableDeclaration decl = (CVariableDeclaration)declarationEdge.getDeclaration();

      String varName = decl.getName();
      CType type = decl.getType();

      //TODO only mutex?
       if(decl.isGlobal() && type instanceof CNamedType &&
          ((CNamedType)type).getName().contentEquals("mutex")) {
        globalMutex.add(varName);
        return newElement;
      }

      //Do we need initialization to analyze?
      /*CInitializer init = decl.getInitializer();

      if(init instanceof CInitializerExpression) {
        printStat (newElement, line, varName);
      }*/

      return newElement;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {
    return null;
  }

  /**Checks expression if we need to save it in statistics. We need only global variables with pointers;
   *
   * @param expression for check
   * @param isKnownPointer define if we already know this variable as pointer
   * @return true if we need to save this variable
   */
  private boolean CheckVariableToSave(CRightHandSide expression, boolean isKnownPointer)
  {
    if (expression instanceof CIdExpression)
    {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();

      CType type = decl.getType();

      //Global pointer!
      if (decl instanceof CDeclaration && ((CDeclaration)decl).isGlobal() &&
          type instanceof CPointerType)
      {
        type = ((CPointerType)type).getType();
        //pointer to structure
        if (type instanceof CElaboratedType && ((CElaboratedType)type).getKind() == ElaboratedType.STRUCT)
          return true;
        else
          return false;
      }
      else if (decl instanceof CDeclaration && ((CDeclaration)decl).isGlobal() &&
          isKnownPointer)
      {
        if (type instanceof CElaboratedType && ((CElaboratedType)type).getKind() == ElaboratedType.STRUCT)
          return true;
        else
          return false;
      }
      else if (decl instanceof CDeclaration)
        return false;
      else if (!(type instanceof CPointerType))
        return false;
      else
        //just for some case
        return true;
    }
    else if (expression instanceof CUnaryExpression && ((CUnaryExpression)expression).getOperator() == UnaryOperator.STAR)
    //*a
      return CheckVariableToSave(((CUnaryExpression)expression).getOperand(), true);
    else if (expression instanceof CFieldReference && ((CFieldReference)expression).isPointerDereference())
    //a->b
      return CheckVariableToSave(((CFieldReference)expression).getFieldOwner(), true);
    /*else if (expression instanceof CArraySubscriptExpression)
    //a[i]
      return CheckVariableToSave(((CArraySubscriptExpression)expression).getArrayExpression());*/
    else if (expression instanceof CBinaryExpression)
    //TODO do it more carefully!
      return (CheckVariableToSave(((CBinaryExpression)expression).getOperand1(), isKnownPointer) &&
              CheckVariableToSave(((CBinaryExpression)expression).getOperand2(), isKnownPointer));
    else if (expression instanceof CCastExpression ||
             expression instanceof CFunctionCallExpression ||
             expression instanceof CIntegerLiteralExpression ||
             expression instanceof CStringLiteralExpression ||
             expression instanceof CUnaryExpression)
    //we dont't need to write it into statistics: only pointers to structures
      return false;
    else
      return false;
  }

  private void printStat (LockStatisticsState element, int line, String name) {
    try {
      file = new FileOutputStream ("output/race_results.txt", true);
      zzz = new PrintWriter(file);
      zzz.print(line);
      zzz.print(": ");
      zzz.print(name);
      zzz.print("  ");
      zzz.println(element.print());
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла race_results.txt");
      System.exit(0);
    } finally {
      if(file != null)
          zzz.close();
    }
  }
}
