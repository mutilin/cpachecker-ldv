/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
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
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;

@Options(prefix="cpa.local")
public class LocalTransferRelation implements TransferRelation {

  @Option(name="allocatefunctions", description = "functions, which allocate new free memory")
  private Set<String> allocate;

  public LocalTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision,
      CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    LocalState LocalElement = (LocalState) pState;
    LocalState successor = LocalElement.clone();

    switch(pCfaEdge.getEdgeType()) {

      // declaration of a function pointer.
      case DeclarationEdge: {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        handleDeclaration(successor, declEdge);
        break;
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        handleStatement(successor, statementEdge.getStatement(), pCfaEdge);
        break;
      }

      case AssumeEdge: {
        //no attention to 'if (...)'
        break;
      }

      case FunctionCallEdge: {
        successor = handleFunctionCall(successor, (CFunctionCallEdge)pCfaEdge);
        break;
      }

      case FunctionReturnEdge: {
        successor = handleReturnEdge(successor, (CFunctionReturnEdge)pCfaEdge);
        break;
      }
      case ReturnStatementEdge: {
        handleReturnStatementEdge(successor, (CReturnStatementEdge)pCfaEdge);
        break;
      }
      case BlankEdge:
      case CallToReturnEdge: {
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private void handleReturnStatementEdge(LocalState pSuccessor, CReturnStatementEdge pCfaEdge) {
    pSuccessor.setReturnExpression(pCfaEdge.getExpression());
    //may be, this function will be bigger later...
  }

  private LocalState handleReturnEdge(LocalState pSuccessor, CFunctionReturnEdge pCfaEdge) throws HandleCodeException {
    CFunctionSummaryEdge summaryEdge  = pCfaEdge.getSummaryEdge();
    CFunctionCall exprOnSummary       = summaryEdge.getExpression();
    CExpression returnExpression = pSuccessor.getReturnExpression();
    LocalState newElement  = pSuccessor.getPreviousState();

    if (exprOnSummary instanceof CFunctionCallAssignmentStatement) {
      if (returnExpression != null) {

        CFunctionCallAssignmentStatement assignExp = ((CFunctionCallAssignmentStatement)exprOnSummary);
        CExpression op1 = assignExp.getLeftHandSide();
        //find type in old state...
        int dereference = findDereference(op1.getExpressionType());
        DataType returnType = findType(pSuccessor, returnExpression, dereference);
        //... and save it in new
        set(newElement, op1, returnType, dereference);
        pSuccessor.setReturnExpression(null);
      } else {
        //we don't know type
        //set(newElement, op1, returnType, dereference);
      }
    }

    return newElement;
  }

  private LocalState handleFunctionCall(LocalState pSuccessor, CFunctionCallEdge cfaEdge) throws HandleCodeException {
    CStatement statement = cfaEdge.getRawAST().get();
    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CRightHandSide right = ((CFunctionCallAssignmentStatement)statement).getRightHandSide();
      CExpression left = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();
      // expression - only name of function
      if (right instanceof CFunctionCallExpression && left.getExpressionType() instanceof CPointerType) {
        handleFunctionCallExpression(pSuccessor, left, (CFunctionCallExpression)right);

      }

    } else if (statement instanceof CFunctionCallStatement) {
      //nothing to save
      //handleFunctionCallExpression(pSuccessor, null, ((CFunctionCallStatement)statement).getFunctionCallExpression());

    } else {
      throw new HandleCodeException("No function found");
    }

    pSuccessor = createNewScopeInState(pSuccessor, cfaEdge);
    return pSuccessor;
  }

  private void handleFunctionCallExpression(LocalState pSuccessor, CExpression left, CFunctionCallExpression right) throws HandleCodeException {
    String funcName = right.getFunctionNameExpression().toASTString();
    if (allocate != null && allocate.contains(funcName) && left != null) {
      int dereference = findDereference(left.getExpressionType());
      if (checkId( createId(left, dereference)) != DataType.GLOBAL)
        set(pSuccessor, left, DataType.LOCAL, dereference);
    }
  }

  private LocalState createNewScopeInState(LocalState pSuccessor, CFunctionCallEdge callEdge) throws HandleCodeException {
    LocalState newState = new LocalState(pSuccessor);

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();
    List<String> paramNames = functionEntryNode.getFunctionParameterNames();
    List<CExpression> arguments = callEdge.getArguments();

    if (paramNames.size() == arguments.size()) {
      CExpression currentArgument;
      int dereference;
      for (int i = 0; i < arguments.size(); i++) {
        currentArgument = arguments.get(i);
        dereference = findDereference(currentArgument.getExpressionType());
        if (dereference > 0) {
          VarId id = new VarId(paramNames.get(i), dereference, null);
          newState.save(id, findType(pSuccessor, currentArgument, findDereference(currentArgument.getExpressionType())));
          //if (pointsToStruct(currentArgument.getExpressionType()))
            //System.out.println("Argument " + currentArgument.toASTString() + " is pointer of struct type("
         // + callEdge.getLineNumber() + ")");
        } else {
          //we give the value, so we don't save it.
        }
      }
    }
    // else, something like 'f(..)'. Now we can't do anything
    return newState;
  }

  /*private boolean pointsToStruct(CType pExpressionType) {
    if (pExpressionType instanceof CPointerType) {
      return pointsToStruct(((CPointerType)pExpressionType).getType());
    } else if (pExpressionType instanceof CCompositeType) {
      //TODO add struct check
      return true;
    } else if (pExpressionType instanceof CComplexType) {
      return true;
    }
    return false;
  }*/

  private void handleStatement(LocalState pSuccessor, CStatement pStatement, CFAEdge pCfaEdge) throws HandleCodeException {
    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment)pStatement;

      CExpression left = assignment.getLeftHandSide();
      CRightHandSide right = assignment.getRightHandSide();

      if (right instanceof CExpression && left.getExpressionType() instanceof CPointerType) {
        assume(pSuccessor, left, findDereference((left.getExpressionType())), (CExpression)right);
      } else if (right instanceof CFunctionCallExpression && left.getExpressionType() instanceof CPointerType) {
        handleFunctionCallExpression(pSuccessor, left, (CFunctionCallExpression)assignment.getRightHandSide());
      }

    } else if (pStatement instanceof CFunctionCallStatement) {
      //do nothing, because there isn't left side, and we won't enter into
      //handleFunctionCallExpression(pSuccessor, null, ((CFunctionCallStatement)pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      System.out.println("Do you know, how CExpressionStatement look like? This: " + pStatement);

    } else {
      throw new HandleCodeException("Unrecognized statement: " + pStatement.toASTString());
    }
  }

  private int findDereference(CType type) {
    if (type instanceof CPointerType) {
      CPointerType pointerType = (CPointerType) type;
      return (findDereference(pointerType.getType()) + 1);
    } else {
      return 0;
    }
  }

  private DataType checkId(Id id) throws HandleCodeException {
    //returns LOCAL or GLOBAL fo _variable_, not for memory location
    if (id instanceof VarId) {
      CSimpleDeclaration decl = ((VarId)id).decl;
      if (decl == null) {
        //'null' value is parsed, as CIdExpression without declaration...
        if (id.name.equals("null"))
          return DataType.LOCAL;
        //it may be, f.e., in case of parsing ((threadTCB *)0)->m_intermNode
        //we don't know decl of '0', but we need this id
        //so, return GLOBAL
        return DataType.GLOBAL;
      }

      if (decl instanceof CDeclaration) {
        if (((CDeclaration)decl).isGlobal())
          return DataType.GLOBAL;
        else
          return DataType.LOCAL;
      } else if (decl instanceof CParameterDeclaration) {
        return DataType.LOCAL;
      } else {
        throw new HandleCodeException("Unknown type of declaration: " + decl.toASTString());
      }
    } else if (id instanceof StructId) {
       return checkId(((StructId)id).getOwner());
    } else {
        //null
      //System.out.println("Null pointer in checkId()");
      return null;
    }
  }

  private DataType findType(LocalState pSuccessor, CExpression expression, int dereference) throws HandleCodeException {
    //p = expression
    //Where points 'expression'
    if (expression instanceof CArraySubscriptExpression) {
      // p = q[i] -> p = q
      return findType(pSuccessor, ((CArraySubscriptExpression)expression).getArrayExpression(), dereference);

    } else if (expression instanceof CBinaryExpression) {
      DataType firstType = findType(pSuccessor, ((CBinaryExpression)expression).getOperand1(), dereference);
      DataType secondType = findType(pSuccessor, ((CBinaryExpression)expression).getOperand2(), dereference);
      return DataType.max(firstType, secondType);

    } else if (expression instanceof CFieldReference) {
      Id struct = createId(expression, dereference);
      if (struct != null) {
        DataType type = checkId(struct);
        if (type == DataType.GLOBAL) {
          //struct is global, not memory location!!
          return DataType.GLOBAL;
        } else {
          return pSuccessor.getType(struct);
        }
      }
      else
        return null;

    } else if (expression instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();
      if (decl == null) {
        //'null' value is parsed, as CIdExpression without declaration...
        if (((CIdExpression)expression).getName().equals("null"))
          return DataType.LOCAL;
        //it may be, f.e., in case of parsing ((threadTCB *)0)->m_intermNode
        //we don't know decl of '0', but we need this id
        //so, return GLOBAL
        return DataType.GLOBAL;
      }
      if (decl instanceof CDeclaration) {
        if (((CDeclaration)decl).isGlobal())
          return DataType.GLOBAL;
        else if (dereference > 0)
          return pSuccessor.getType(new VarId(decl.getName(), dereference, decl));
        else
        //we have '&a' somewhere (a can be only local in this branch)
          return DataType.LOCAL;
      } else if (decl instanceof CParameterDeclaration) {
        return pSuccessor.getType(new VarId(decl.getName(), dereference, decl));
      } else {
        throw new HandleCodeException("Can't understand the type of declaration: " + decl.getName());
      }

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        return findType(pSuccessor, ((CUnaryExpression)expression).getOperand(), ++dereference);
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        return findType(pSuccessor, ((CUnaryExpression)expression).getOperand(),  --dereference);
      } else {
        return findType(pSuccessor, ((CUnaryExpression)expression).getOperand(), dereference);
      }

    } else if (expression instanceof CLiteralExpression) {
      return DataType.LOCAL;
    } else if (expression instanceof CCastExpression) {
      return findType(pSuccessor, ((CCastExpression)expression).getOperand(), dereference);
    } else if (expression instanceof CTypeIdExpression) {
      return DataType.LOCAL;
    } else {
      throw new HandleCodeException("Unknown type of expression: " + expression.toASTString());
    }
  }

  private void set(LocalState pSuccessor, CExpression expression, DataType type, int dereference) throws HandleCodeException {
    if (expression instanceof CArraySubscriptExpression) {
      // p = q[i] -> p = q
      set(pSuccessor, ((CArraySubscriptExpression)expression).getArrayExpression(), type, dereference);

    } else if (expression instanceof CBinaryExpression) {
      set(pSuccessor, ((CBinaryExpression)expression).getOperand1(), type, dereference);
      set(pSuccessor, ((CBinaryExpression)expression).getOperand2(), type, dereference);

    } else if (expression instanceof CFieldReference) {
      //CExpression owner = ((CFieldReference)expression).getFieldOwner();
      Id struct = createId(expression, dereference);
      if (struct != null)
        pSuccessor.set(struct, type);

    } else if (expression instanceof CIdExpression) {
      CSimpleDeclaration decl = ((CIdExpression)expression).getDeclaration();
      if (dereference >= 0) {
        //TODO closure: **p, ***p, ****p ...
        pSuccessor.set(new VarId(((CIdExpression)expression).getName(), dereference, decl), type);
      }
      else {
        //we have '&a' somewhere
        //TODO closure: ***p, ****p, ...
        if (pSuccessor.contains(new VarId(decl.getName(), 1, decl)))
          //we have 'p = &q', but q also points somewhere, so we set *q as 'type'
          pSuccessor.set(new VarId(((CIdExpression)expression).getName(), 1, decl), type);
      }

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        set(pSuccessor, ((CUnaryExpression)expression).getOperand(), type, ++dereference);
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        set(pSuccessor, ((CUnaryExpression)expression).getOperand(), type, --dereference);
      } else {
        set(pSuccessor, ((CUnaryExpression)expression).getOperand(), type, dereference);
      }

    } else if (expression instanceof CCastExpression) {
      set(pSuccessor, ((CCastExpression)expression).getOperand(), type, dereference);
    } else if (expression instanceof CLiteralExpression ||expression instanceof CTypeIdExpression) {
      //do nothing, because we can't set 'type' to literal or typeId
    } else {
      throw new HandleCodeException("Unknown type of expression: " + expression.toASTString());
    }
  }

  public void assume(LocalState pSuccessor, CExpression left, int leftDereference, CExpression right) throws HandleCodeException {
    //expr = expr
    if (left instanceof CIdExpression) {
       VarId id = new VarId(((CIdExpression)left).getName(), leftDereference,
          ((CIdExpression)left).getDeclaration());
      assume(pSuccessor, id, right);
    } else if (left instanceof CUnaryExpression) {
      if (((CUnaryExpression)left).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        assume(pSuccessor, ((CUnaryExpression)left).getOperand(), ++leftDereference, right);
      } else {
        throw new HandleCodeException("Strange left side: " + left.toASTString());
      }
    } else if (left instanceof CArraySubscriptExpression) {
      assume(pSuccessor, ((CArraySubscriptExpression)left).getArrayExpression(), leftDereference, right);
    } else if (left instanceof CFieldReference) {
      Id struct = createId(left, leftDereference);
      if (struct != null)
        assume(pSuccessor, struct, right);
    } else if (left instanceof CBinaryExpression) {
      CExpression op1 = ((CBinaryExpression)left).getOperand1();
      CExpression op2 = ((CBinaryExpression)left).getOperand2();
      if (op1 instanceof CLiteralExpression || op1 instanceof CTypeIdExpression
          || !(op1.getExpressionType() instanceof CPointerType)) {
        assume(pSuccessor, op2, leftDereference, right);
      } else if (op2 instanceof CLiteralExpression || op2 instanceof CTypeIdExpression
          || !(op2.getExpressionType() instanceof CPointerType)) {
        assume(pSuccessor, op1, leftDereference, right);
      } else {
        throw new HandleCodeException("Now we can't process such difficult left side: " + left.toASTString() +
            "(" + left.getFileLocation().getStartingLineNumber() + ")");
      }
    } else if (left instanceof CCastExpression) {
      assume(pSuccessor, ((CCastExpression)left).getOperand(), leftDereference, right);
    } else {
      throw new HandleCodeException("Now we can't process such difficult left side: " + left.toASTString()
          + "(" + left.getFileLocation().getStartingLineNumber() + ")");
    }
  }

  private Id createId(CExpression expression, int dereference) throws HandleCodeException {
    //return null, if we can't create id. Now it's because of (*(5 + sizeof(int)).x
    if (expression instanceof CArraySubscriptExpression) {
      // p = q[i] -> p = q
      return createId(((CArraySubscriptExpression)expression).getArrayExpression(), dereference);

    } else if (expression instanceof CBinaryExpression) {
      Id resultId1, resultId2;
      resultId1 = createId(((CBinaryExpression)expression).getOperand1(), dereference);
      resultId2 = createId(((CBinaryExpression)expression).getOperand2(), dereference);
      if (resultId1 == null && resultId2 == null)
        return null;
      else if (resultId1 == null)
        return resultId2;
      else if (resultId2 == null)
        return resultId1;
      else {
        //f.e. (a + b)->x;
        return null;
      }

    } else if (expression instanceof CFieldReference) {
      CExpression owner = ((CFieldReference)expression).getFieldOwner();
      Id ownerId = createId(owner, (((CFieldReference)expression).isPointerDereference() ? 1 : 0));
      StructId fullId = new StructId(ownerId, ((CFieldReference)expression).getFieldName(), dereference);
      return fullId;

    } else if (expression instanceof CIdExpression) {
      //we should check and now it can be only local
      if (dereference >= 0)
        return new VarId(((CIdExpression)expression).getName(), dereference,
            ((CIdExpression)expression).getDeclaration());
      else
        //we have '&a' somewhere (a can be only local in this branch)
        throw new HandleCodeException("Adress in field owner of structure " + expression.toASTString());

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        return createId(((CUnaryExpression)expression).getOperand(), ++dereference);
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        return createId(((CUnaryExpression)expression).getOperand(),  --dereference);
      } else {
        return createId(((CUnaryExpression)expression).getOperand(), dereference);
      }

    } else if (expression instanceof CLiteralExpression) {
      // f.e. ( ((threadTCB *)0)->m_intermNode). we shouldn't return null, because it is correct.
      // So, we create new id
      return new VarId(expression.toASTString(), dereference, null);
    } else if (expression instanceof CCastExpression) {
      return createId(((CCastExpression)expression).getOperand(), dereference);
    } else if (expression instanceof CTypeIdExpression) {
      return null;
    } else {
      throw new HandleCodeException("Unknown type of field owner: " + expression.toASTString());
    }
  }

  private void assume(LocalState pSuccessor, Id leftId, CExpression right) throws HandleCodeException {
    DataType type = checkId(leftId);
    if (type == DataType.GLOBAL) {
      //Variable is global, not memory location!
      //So, we should set the type of 'right' to global
      set(pSuccessor, right, DataType.GLOBAL, leftId.dereference);
    } else {
      type = findType(pSuccessor, right, leftId.dereference);
      if (type != null)
        pSuccessor.save(leftId, type);
    }
  }

  private void handleDeclaration(LocalState pSuccessor, CDeclarationEdge declEdge) throws HandleCodeException {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class)
      return;

    CDeclaration decl = declEdge.getDeclaration();
    /*if (decl.getFileLocation().getStartingLineNumber() == 333453)
      System.out.println("Declaration of FILE f");*/
    if (decl.getType() instanceof CPointerType) {
      //we don't save global variables
      if (!decl.isGlobal())
        pSuccessor.save(new VarId(decl.getName(), findDereference(decl.getType()), decl), DataType.LOCAL);
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }

}
