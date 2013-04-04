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
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.BinaryIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralLocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.VariableIdentifier;

@Options(prefix="cpa.local")
public class LocalTransferRelation implements TransferRelation {

  @Option(name="allocatefunctions", description = "functions, which allocate new free memory")
  private Set<String> allocate;

  private IdentifierCreator idCreator = new IdentifierCreator();

  public LocalTransferRelation(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision,
      CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    LocalState LocalElement = (LocalState) pState;
    LocalState successor = LocalElement.clone();
    switch(pCfaEdge.getEdgeType()) {

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
        CType type = op1.getExpressionType();
        //find type in old state...
        if (type instanceof CComplexType) {
          //sometimes, it's easy to look at returned expression
          type = returnExpression.getExpressionType();
        }
        int dereference = findDereference(type);
        AbstractIdentifier returnId = createId(returnExpression, dereference);
        DataType returnType = pSuccessor.getType(returnId);
        returnId = createId(op1, dereference);
        //check, if it
        newElement.set(returnId, returnType);
        handleFunctionCallExpression(newElement, returnId, assignExp.getRightHandSide());
        //... and save it in new
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
      CRightHandSide right = ((CFunctionCallAssignmentStatement)statement).getRightHandSide();
      CExpression left = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();
      // expression - only name of function
      if (right instanceof CFunctionCallExpression && left.getExpressionType() instanceof CPointerType) {
        //AbstractIdentifier leftId = createId(left, findDereference(left.getExpressionType()));
        //handleFunctionCallExpression(pSuccessor, leftId, (CFunctionCallExpression)right);
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

  private boolean handleFunctionCallExpression(LocalState pSuccessor, AbstractIdentifier leftId, CFunctionCallExpression right) throws HandleCodeException {
    String funcName = right.getFunctionNameExpression().toASTString();
    if (allocate != null && allocate.contains(funcName) && leftId != null) {
      if (!leftId.isGlobal()) {
        pSuccessor.set(leftId, DataType.LOCAL);
        return true;
      }
    }
    return false;
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
        AbstractIdentifier previousId;
        int previousDeref = 0;
        while (dereference > 0) {
          LocalVariableIdentifier id = new GeneralLocalVariableIdentifier(paramNames.get(i), dereference);
          previousId = createId(currentArgument, previousDeref);
          if (previousId.getDereference() < 0) {
            if (previousId.isGlobal()) {
              newState.set(id, DataType.GLOBAL);
            } else {
              newState.set(id, DataType.LOCAL);
            }
          } else if (previousId.getDereference() >= 0) {
            previousId = createId(currentArgument, dereference - previousId.getDereference());
            newState.set(id, pSuccessor.getType(previousId));
          }
          dereference--;
          previousDeref++;
        }
      }
    }
    // else, something like 'f(..)'. Now we can't do anything
    //TODO Do something!
    return newState;
  }

  private void handleStatement(LocalState pSuccessor, CStatement pStatement, CFAEdge pCfaEdge) throws HandleCodeException {
    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment)pStatement;
      CExpression left = assignment.getLeftHandSide();

      if (left.getExpressionType() instanceof CPointerType) {
        CRightHandSide right = assignment.getRightHandSide();
        AbstractIdentifier leftId = createId(left, findDereference(left.getExpressionType()));

        if (right instanceof CExpression) {
          assume(pSuccessor, leftId, (CExpression)right);
        } else if (right instanceof CFunctionCallExpression) {
          if (pSuccessor.getType(leftId) == DataType.LOCAL) {
            //reset it
            pSuccessor.set(leftId, null);
          }
          handleFunctionCallExpression(pSuccessor, leftId, (CFunctionCallExpression)assignment.getRightHandSide());
        }
      }

    } else if (pStatement instanceof CFunctionCallStatement) {
      //do nothing, because there isn't left side, and we won't enter into
      //handleFunctionCallExpression(pSuccessor, null, ((CFunctionCallStatement)pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      System.err.println("Do you know, how CExpressionStatement look like? This: " + pStatement);

    } else {
      throw new HandleCodeException("Unrecognized statement: " + pStatement.toASTString());
    }
  }

  public static int findDereference(CType type) {
    if (type instanceof CPointerType) {
      CPointerType pointerType = (CPointerType) type;
      return (findDereference(pointerType.getType()) + 1);
    } else {
      return 0;
    }
  }

  private AbstractIdentifier createId(CExpression expression, int dereference) throws HandleCodeException {
    idCreator.setDereference(dereference);
    return expression.accept(idCreator);
  }

  private void assume(LocalState pSuccessor, AbstractIdentifier leftId, CExpression right) throws HandleCodeException {
    if (leftId instanceof ConstantIdentifier)
      //Can't assume to constant, but this situation can occur, if we have *(a + b)...
      return;
    /*else if (leftId instanceof BinaryIdentifier) {
      //TODO may be, it should be changed...
      //assume(pSuccessor, ((BinaryIdentifier)leftId).getIdentifier1(), right);
      //assume(pSuccessor, ((BinaryIdentifier)leftId).getIdentifier2(), right);
      pSuccessor.set(leftId, type)
      //System.out.println("Binary assumption: " + leftId.toString() + ":" + right.getFileLocation().getStartingLineNumber());
      return;
    }*/
    //AbstractIdentifier left = (AbstractIdentifier) leftId;
    AbstractIdentifier rightId = createId(right, leftId.getDereference());
    if (leftId.isGlobal()) {
      //Variable is global, not memory location!
      //So, we should set the type of 'right' to global
      pSuccessor.set(rightId, DataType.GLOBAL);
    } else {
      DataType type = pSuccessor.getType(rightId);
      pSuccessor.set(leftId, type);
      if (!pSuccessor.contains(rightId) && type != null) {
        //In some cases we know the type of right expression,
        //but haven't got it in successor's state
        //It can be, f.e., in structure processing:
        //We saved the name of structure, but didn't save its field
        //So, save it now

        //We shouldn't save such identifiers, as &t.
        //But we save all structures
        if (rightId instanceof VariableIdentifier && rightId.getDereference() <= 0)
          return;

        //Only for debug! <Delete>
        if (rightId instanceof BinaryIdentifier)
          return;
        //</Delete>

        pSuccessor.set(rightId, type);
      }
    }
  }

  private void handleDeclaration(LocalState pSuccessor, CDeclarationEdge declEdge) throws HandleCodeException {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class)
      return;

    CDeclaration decl = declEdge.getDeclaration();
    if (decl.getType() instanceof CPointerType && !decl.isGlobal()) {
      //we don't save global variables
      pSuccessor.set(new GeneralLocalVariableIdentifier(decl.getName(), findDereference(decl.getType())), DataType.LOCAL);

    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }

}
