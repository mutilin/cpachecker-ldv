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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.usagestatistics")
class UsageStatisticsTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private final UsageStatisticsCPAStatistics statistics;
  private final CodeCovering covering;

  UsageStatisticsTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, UsageStatisticsCPAStatistics s, CodeCovering cover) throws InvalidConfigurationException {
    config.inject(this);
    wrappedTransfer = pWrappedTransfer;
    statistics = s;
    covering = cover;
    }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws InterruptedException, CPATransferException {

    final UsageStatisticsState oldState = (UsageStatisticsState)pElement;
    Collection<UsageStatisticsState> results;

    if (pCfaEdge == null) {
      CFANode node = extractLocation(oldState);
      results = new ArrayList<UsageStatisticsState>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
          getAbstractSuccessorForEdge(oldState, pPrecision, edge, results);
      }

    } else {
      results = new ArrayList<UsageStatisticsState>(1);
      getAbstractSuccessorForEdge(oldState, pPrecision, pCfaEdge, results);

    }
    return results;
  }

  private void getAbstractSuccessorForEdge(UsageStatisticsState oldState,
      Precision pPrecision, CFAEdge pCfaEdge, Collection<UsageStatisticsState> results)
      throws InterruptedException, CPATransferException {

    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessors(oldState.getWrappedState(), pPrecision, pCfaEdge);

    for (AbstractState newWrappedState : newWrappedStates) {
      UsageStatisticsState newState = oldState.createDuplicateWithNewWrappedState(newWrappedState);

      newState = handleEdge(newState, pCfaEdge);

      if (newState != null) {
        results.add(newState);
      }
    }
  }

  private UsageStatisticsState handleEdge(UsageStatisticsState newState, CFAEdge pCfaEdge) throws CPATransferException {

    switch(pCfaEdge.getEdgeType()) {

      // declaration of a function pointer.
      case DeclarationEdge: {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        handleDeclaration(newState, declEdge);
        break;
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        handleStatement(newState, statementEdge.getStatement(), pCfaEdge);
        break;
      }

      // maybe two function pointers are compared.
      case AssumeEdge: {
        CAssumeEdge assumeEdge = (CAssumeEdge) pCfaEdge;
        handleAssumption(newState, assumeEdge.getExpression(), pCfaEdge);
        break;
      }

      case FunctionCallEdge: {
        handleFunctionCall(newState, (CFunctionCallEdge)pCfaEdge);
        break;
      }
      //case BlankEdge: {
        //here can be 'switch' or 'default'
        /*
         * strange, but in this case (switch, else or default) getLineNumber
         * returns number of next line
         */
        /*if (pCfaEdge.getCode().contains("switch") ||
            pCfaEdge.getCode().contains("else") ||
            pCfaEdge.getCode().contains("default") ||
            pCfaEdge.getCode().contains("goto"))
           covering.addLine(pCfaEdge.getLineNumber() - 1);
        else*/



        case FunctionReturnEdge:
        case ReturnStatementEdge:
        case BlankEdge: {
          covering.addLine(pCfaEdge.getLineNumber());
        break;
      }

      // nothing to do.

      case CallToReturnEdge: {
        //handleFunctionCall(newState, (CFunctionCallEdge)pCfaEdge);
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }


    return newState;
  }

  private void handleFunctionCall(UsageStatisticsState pNewState, CFunctionCallEdge edge) throws HandleCodeException {
    CStatement statement = edge.getRawAST().get();
    covering.addLine(edge.getLineNumber());

    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CRightHandSide right = ((CFunctionCallAssignmentStatement)statement).getRightHandSide();
      //System.out.println(expression.toASTString());
      // expression - only name of function
      if (right instanceof CFunctionCallExpression) {
        handleFunctionCallExpression(pNewState, (CFunctionCallExpression)right);
      } else {
        //where is function?
        throw new HandleCodeException("Can't find function call here: " + right.toASTString());
      }

      CExpression variable = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();
      Set<Pair<Identifier, Access>> result = handleWrite(variable,
          AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);
      statistics.add(result, pNewState, variable.getFileLocation().getStartingLineNumber(), EdgeType.ASSIGNMENT);

    } else if (statement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, ((CFunctionCallStatement)statement).getFunctionCallExpression());

    } else {
      throw new HandleCodeException("No function found");
    }
  }


  private void handleDeclaration(UsageStatisticsState pNewState, CDeclarationEdge declEdge) throws CPATransferException {

    if (declEdge.getDeclaration() instanceof CFunctionDeclaration) {
      if (!declEdge.getRawStatement().contains(";"))
        covering.addFunction(declEdge.getLineNumber(), ((CFunctionDeclaration)declEdge.getDeclaration()).getName());
      else
        covering.addException(declEdge.getLineNumber());
      return;
    }
    else if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      // not a variable declaration
      return;
    }
    covering.addLine(declEdge.getLineNumber());

    CVariableDeclaration decl = (CVariableDeclaration)declEdge.getDeclaration();

    CInitializer init = decl.getInitializer();

    if (init == null) {
      //no assumption
      return;
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression)init).getExpression();
      Set<Pair<Identifier, Access>> result = handleRead(initExpression,
          AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);

      statistics.add(result, pNewState, initExpression.getFileLocation().getStartingLineNumber(), EdgeType.DECLARATION);
    }

    String name = decl.getName();

    if (name == null) {
      // not a variable declaration
      return;
    }

    Set<Pair<Identifier, Access>> result = new HashSet<Pair<Identifier, Access>>();
    Identifier id = createIdentifierFromDecl(decl,
        AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), false);
    //TODO really 0 (isDereference), check it
    result.add(Pair.of(id, Access.READ));

    statistics.add(result, pNewState, decl.getFileLocation().getStartingLineNumber(), EdgeType.DECLARATION);
  }

  void handleFunctionCallExpression(UsageStatisticsState pNewState, CFunctionCallExpression fcExpression) throws HandleCodeException {
    covering.addFunctionUsage(fcExpression.getFunctionNameExpression().toASTString());
    List<CExpression> params = fcExpression.getParameterExpressions();

    for (CExpression p : params) {
      Set<Pair<Identifier, Access>> result = handleRead(p,
                    AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);

      /*function parameters are declaration!*/
      statistics.add(result, pNewState, p.getFileLocation().getStartingLineNumber(), EdgeType.FUNCTION_CALL);
    }
  }

  private void handleStatement(UsageStatisticsState pNewState, CStatement pStatement,
        CFAEdge pCfaEdge) throws HandleCodeException {

    covering.addLine(pCfaEdge.getLineNumber());

    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"

      CAssignment assignment = (CAssignment)pStatement;
      Set<Pair<Identifier, Access>> result = handleWrite(assignment.getLeftHandSide(),
                  AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);

      statistics.add(result, pNewState, pStatement.getFileLocation().getStartingLineNumber(), EdgeType.ASSIGNMENT);

      if (assignment.getRightHandSide() instanceof CExpression) {
        result = handleRead((CExpression)assignment.getRightHandSide(),
                  AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);

        statistics.add(result, pNewState, pStatement.getFileLocation().getStartingLineNumber(), EdgeType.ASSIGNMENT);

      } else if (assignment.getRightHandSide() instanceof CFunctionCallExpression)
        handleFunctionCallExpression(pNewState, (CFunctionCallExpression)assignment.getRightHandSide());
      else {
        throw new HandleCodeException("Unrecognised type of right side of assignment: " + assignment.asStatement().toASTString());
      }

    } else if (pStatement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, ((CFunctionCallStatement)pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      // side-effect free statement
      Set<Pair<Identifier, Access>> result = handleWrite(((CExpressionStatement)pStatement).getExpression(),
          AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction(), 0);

      statistics.add(result, pNewState, pStatement.getFileLocation().getStartingLineNumber(), EdgeType.STATEMENT);

    } else {
      throw new HandleCodeException("Unrecognized statement: " + pStatement.toASTString());
    }
  }

  private void handleAssumption(UsageStatisticsState element, CExpression pExpression, CFAEdge cfaEdge) throws HandleCodeException {
    covering.addLine(cfaEdge.getLineNumber());

    Set<Pair<Identifier, Access>> result = handleRead(pExpression,
                  AbstractStates.extractStateByType(element, CallstackState.class).getCurrentFunction(), 0);

    statistics.add(result, element, pExpression.getFileLocation().getStartingLineNumber(), EdgeType.ASSUMPTION);
  }

  Identifier createIdentifier(CIdExpression expression, String function, boolean isDereference) throws HandleCodeException {

    CSimpleDeclaration decl = expression.getDeclaration();

    if (decl == null) {
      /*
       * It means, that we have function, but parser couldn't understand this:
       * int f();
       * int (*a)() = &f;
       * Skip it
       */
      return null;
    }

    return createIdentifierFromDecl(decl, function, isDereference);
  }

  Identifier createIdentifierFromDecl(CSimpleDeclaration decl, String function, boolean isDereference) throws HandleCodeException {
    String name = decl.getName();
    String type = decl.getType().toASTString("");
    type = type.replaceAll("\n", "");
    type = type.replaceAll("\\{.*\\} ", ""); //for long structions

    //boolean dereference = isDereference || (decl.getType().getClass() == CPointerType.class);
    //System.out.println(decl.toASTString());
    if (decl instanceof CDeclaration){
      if(((CDeclaration)decl).isGlobal())
        return new GlobalVariableIdentifier(name, type, isDereference);
      else {
        if (isDereference)
          return new LocalVariableIdentifier(name, type, function, isDereference);
        else
          return null;
      }

    } else if (decl instanceof CParameterDeclaration) {
      if (isDereference)
        return new LocalVariableIdentifier(name, type, function, isDereference);
      else
        return null;

    } else {
      throw new HandleCodeException("Unrecognized declaration: " + decl.toASTString());
    }
  }

  Set<Pair<Identifier, Access>> handleRead(CExpression expression, String function, int derefenceCounter) throws HandleCodeException {
    Set<Pair<Identifier, Access>> result = new HashSet<Pair<Identifier, Access>>();

    if (expression instanceof CArraySubscriptExpression) {
      result.addAll(handleRead(((CArraySubscriptExpression)expression).getArrayExpression(), function, derefenceCounter));

    } else if (expression instanceof CBinaryExpression) {
      result.addAll(handleRead(((CBinaryExpression)expression).getOperand1(), function, derefenceCounter));
      result.addAll(handleRead(((CBinaryExpression)expression).getOperand2(), function, derefenceCounter));

    } else if (expression instanceof CFieldReference) {
      Identifier id = new StructureFieldIdentifier(((CFieldReference)expression).getFieldName(),
          ((CFieldReference)expression).getFieldOwner().getExpressionType().toASTString(""),
          ((CFieldReference)expression).getExpressionType().toASTString(""), (derefenceCounter > 0));
      result.add(Pair.of(id, Access.READ));
      if (((CFieldReference)expression).isPointerDereference())
        result.addAll(handleRead(((CFieldReference)expression).getFieldOwner(), function, 1));
      else
        result.addAll(handleRead(((CFieldReference)expression).getFieldOwner(), function, 0));

    } else if (expression instanceof CIdExpression) {
      Identifier id = createIdentifier((CIdExpression)expression, function, (derefenceCounter > 0));
      result.add(Pair.of(id, Access.READ));

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        result.addAll(handleRead(((CUnaryExpression)expression).getOperand(), function, ++derefenceCounter));
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        result.addAll(handleRead(((CUnaryExpression)expression).getOperand(), function, --derefenceCounter));
      } else {
        result.addAll(handleRead(((CUnaryExpression)expression).getOperand(), function, derefenceCounter));
      }

    } else if (expression instanceof CLiteralExpression) {
      //nothing to do

    } else if (expression instanceof CCastExpression){
      //can't do anything with cast
      return handleRead(((CCastExpression)expression).getOperand(), function, derefenceCounter);

    } else if (expression instanceof CTypeIdExpression){
      //like a = sizeof(int), so nothing to do

    } else {
      throw new HandleCodeException("Undefined type of expression: " + expression.toASTString());
    }
    return result;
  }

  Set<Pair<Identifier, Access>> handleWrite(CExpression expression, String function, int derefenceCounter) throws HandleCodeException {
    Set<Pair<Identifier, Access>> result = new HashSet<Pair<Identifier, Access>>();

    if (expression instanceof CArraySubscriptExpression) {
      result.addAll(handleWrite(((CArraySubscriptExpression)expression).getArrayExpression(), function, derefenceCounter));

    } else if (expression instanceof CBinaryExpression) {
      throw new HandleCodeException(expression.toASTString() + " can't be in left side of statement");

    } else if (expression instanceof CFieldReference) {
      Identifier id = new StructureFieldIdentifier(((CFieldReference)expression).getFieldName(),
          ((CFieldReference)expression).getFieldOwner().getExpressionType().toASTString(""),
          ((CFieldReference)expression).getExpressionType().toASTString(""), (derefenceCounter > 0));
      result.add(Pair.of(id, Access.WRITE));
      if (((CFieldReference)expression).isPointerDereference()) {
        result.addAll(handleRead(((CFieldReference)expression).getFieldOwner(), function, 1));
      } else {
        result.addAll(handleRead(((CFieldReference)expression).getFieldOwner(), function, 0));
      }

    } else if (expression instanceof CIdExpression) {
      Identifier id = createIdentifier((CIdExpression)expression, function, (derefenceCounter > 0));
      result.add(Pair.of(id, Access.WRITE));

    } else if (expression instanceof CUnaryExpression) {
      if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.STAR) {
        result.addAll(handleRead(((CUnaryExpression)expression).getOperand(), function, ++derefenceCounter));
      } else if (((CUnaryExpression)expression).getOperator() == CUnaryExpression.UnaryOperator.AMPER) {
        result.addAll(handleWrite(((CUnaryExpression)expression).getOperand(), function, --derefenceCounter));
      } else {
        result.addAll(handleRead(((CUnaryExpression)expression).getOperand(), function, derefenceCounter));
      }

    } else if (expression instanceof CLiteralExpression) {
      //nothing to do

    } else if (expression instanceof CCastExpression){
      //TODO do smth with cast
      return handleWrite(((CCastExpression)expression).getOperand(), function, derefenceCounter);
    } else {
      throw new HandleCodeException("Undefined type of expression: " + expression.toASTString());
    }
    return result;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) {
    // in this method we could access the abstract domains of other CPAs
    // if required.
    return null;
  }
}
