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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CNamedType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
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
  private Map<String, Set<ActionInfo>> GlobalLockStat;
  private Map<String, Set<ActionInfo>> LocalLockStat;
  private Map<String, String> NameToType;

  public LockStatisticsTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
    GlobalLockStat = new HashMap<String, Set<ActionInfo>>();
    LocalLockStat = new HashMap<String, Set<ActionInfo>>();
    NameToType = new HashMap<String, String>();
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision, CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsPrecision lockStatisticsPrecision = (LockStatisticsPrecision)pPrecision;

    LockStatisticsState successor;

    switch (cfaEdge.getEdgeType()) {

    case FunctionCallEdge:
      successor = lockStatisticsElement.clone();
      break;

    case FunctionReturnEdge:
      CFunctionReturnEdge functionReturnEdge = (CFunctionReturnEdge) cfaEdge;

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
      CAssumeEdge assumeEdge = (CAssumeEdge) cfaEdge;
      return handleAssumption(element, assumeEdge.getExpression(), cfaEdge, assumeEdge.getTruthAssumption(), precision);
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

  private LockStatisticsState handleAssumption(LockStatisticsState element, CExpression pExpression, CFAEdge cfaEdge,
      boolean pTruthValue, LockStatisticsPrecision pPrecision) {

    if (pExpression instanceof CBinaryExpression) {
      CExpression op1 = ((CBinaryExpression)pExpression).getOperand1();
      CExpression op2 = ((CBinaryExpression)pExpression).getOperand2();

      CheckVariableToSave(element, cfaEdge.getLineNumber(), op1, false, false);
      CheckVariableToSave(element, cfaEdge.getLineNumber(), op2, false, false);
    }
    else {
      CheckVariableToSave(element, cfaEdge.getLineNumber(), pExpression, false, false);
    }

    return element.clone();
  }

  private LockStatisticsState handleFunctionReturn(LockStatisticsState element, CFunctionReturnEdge functionReturnEdge)
    throws UnrecognizedCCodeException {
    CFunctionSummaryEdge summaryEdge    = functionReturnEdge.getSummaryEdge();
    CFunctionCall exprOnSummary  = summaryEdge.getExpression();

    LockStatisticsState newElement = element.clone();

    if(exprOnSummary instanceof CFunctionCallAssignmentStatement) {
      CFunctionCallAssignmentStatement assignExp = ((CFunctionCallAssignmentStatement)exprOnSummary);
      CExpression op1 = assignExp.getLeftHandSide();

      CheckVariableToSave(element, functionReturnEdge.getLineNumber(), op1, false, true);
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
      if (functionName == "mutex_lock_nested" ||
          functionName == "mutex_lock" ||
          functionName == "mutex_trylock") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
        newElement.add(paramName, cfaEdge.getLineNumber());
      }
      else if (functionName == "mutex_unlock") {
        assert !params.isEmpty();
        String paramName = params.get(0).toASTString();
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

    CheckVariableToSave(newElement, cfaEdge.getLineNumber(), op1, false, true);

    CRightHandSide op2 = assignExpression.getRightHandSide();

    CheckVariableToSave(newElement, cfaEdge.getLineNumber(), op2, false, false);

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
   */
  private boolean CheckVariableToSave(LockStatisticsState element, int line,
                                  CRightHandSide expression, boolean isKnownPointer,
                                  boolean isWrite)
  {
    if (expression instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();

      CType type = decl.getType();
      if (decl instanceof CDeclaration && type instanceof CPointerType) {
        //type = ((CPointerType)type).getType();
        /* //pointer to structure
        if (type instanceof CElaboratedType &&
           ((CElaboratedType)type).getKind() == ElaboratedType.STRUCT)*/
        addInfo(element, line, (CExpression)expression, isWrite);
      }
      else if (decl instanceof CDeclaration && isKnownPointer) {
        //if (type instanceof CElaboratedType && ((CElaboratedType)type).getKind() == ElaboratedType.STRUCT)
        addInfo(element, line, (CExpression)expression, isWrite);
      }
    }
    else if (expression instanceof CUnaryExpression && ((CUnaryExpression)expression).getOperator() == UnaryOperator.STAR)
    //*a
      CheckVariableToSave(element, line, ((CUnaryExpression)expression).getOperand(), true, isWrite);
    else if (expression instanceof CFieldReference && ((CFieldReference)expression).isPointerDereference())
    //a->b
      CheckVariableToSave(element, line, ((CFieldReference)expression).getFieldOwner(), true, isWrite);
    else if (expression instanceof CFieldReference) {
    // it can be smth like (*a).b
      CExpression tmpExpression = ((CFieldReference)expression).getFieldOwner();

      if (tmpExpression instanceof CUnaryExpression &&
         ((CUnaryExpression)tmpExpression).getOperator() == UnaryOperator.STAR)
        CheckVariableToSave(element, line, ((CUnaryExpression)tmpExpression).getOperand(), true, isWrite);
    }
    else if (expression instanceof CBinaryExpression) {
      CheckVariableToSave(element, line, ((CBinaryExpression)expression).getOperand1(), isKnownPointer, isWrite);
      CheckVariableToSave(element, line, ((CBinaryExpression)expression).getOperand2(), isKnownPointer, isWrite);
    }

    return true;
  }

  private void addInfo (LockStatisticsState element, int line, CExpression op, boolean isWrite) {
    String name = op.toASTString();

    if (op instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)op).getDeclaration();


      if (!NameToType.containsKey(name))
        NameToType.put(name, decl.getType().toASTString(name));
      if (((CDeclaration)decl).isGlobal())
        addGlobalInfo(element, line, name, isWrite);
      else
        addLocalInfo(element, line, name, isWrite);
    }
    //TODO else?
  }
  private void addGlobalInfo(LockStatisticsState element, int line, String name, boolean isWrite) {
    boolean isThere = false;

    if (GlobalLockStat.containsKey(name)) {
      Set<ActionInfo> tmpActions = GlobalLockStat.get(name);

      for (ActionInfo action : tmpActions) {
        if (action.getLocks().equals(element.getLocks()) && isWrite == action.isWrite() &&
            action.getLine() == line) {
          isThere = true;
          break;
        }
      }

      if (!isThere) {
        ActionInfo action = new ActionInfo(line, element.getLocks(), isWrite);
        tmpActions.add(action);
      }
    }
    else {
      ActionInfo action = new ActionInfo(line, element.getLocks(), isWrite);
      Set<ActionInfo> tmpActions = new HashSet<ActionInfo>();

      tmpActions.add(action);
      GlobalLockStat.put(name, tmpActions);
    }
  }

  private void addLocalInfo (LockStatisticsState element, int line, String name, boolean isWrite) {
    boolean isThere = false;

    if (LocalLockStat.containsKey(name)) {
      Set<ActionInfo> tmpActions = LocalLockStat.get(name);

      for (ActionInfo action : tmpActions) {
        if (action.getLocks().equals(element.getLocks()) && isWrite == action.isWrite() &&
            action.getLine() == line) {
          isThere = true;
          break;
        }
      }

      if (!isThere) {
        ActionInfo action = new ActionInfo(line, element.getLocks(), isWrite);
        tmpActions.add(action);
      }
    }
    else {
      ActionInfo action = new ActionInfo(line, element.getLocks(), isWrite);
      Set<ActionInfo> tmpActions = new HashSet<ActionInfo>();
      tmpActions.add(action);
      LocalLockStat.put(name, tmpActions);
    }
  }

  public Map<String, Set<ActionInfo>> getGlobalLockStatistics() {
    return GlobalLockStat;
  }
  public Map<String, Set<ActionInfo>> getLocalLockStatistics() {
    return LocalLockStat;
  }
  public Map<String, String> getNameToType() {
    return NameToType;
  }
}
