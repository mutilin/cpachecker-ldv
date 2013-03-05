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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.BinderFunctionInfo.ParameterInfo;
import org.sosy_lab.cpachecker.cpa.usageStatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.cpa.usageStatistics.VariableIdentifier.Ref;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.StopAnalysisException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private final UsageStatisticsCPAStatistics statistics;
  private final CodeCovering covering;
  private final ExpressionHandler handler;

  @Option(description = "functions, which we don't analize")
  private Set<String> skippedfunctions = null;

  @Option(description = "functions, which are used to bind variables (like list elements are binded to list variable)")
  private Set<String> binderFunctions = null;

  private Map<String, BinderFunctionInfo> binderFunctionInfo;
  //TODO: strengthen (CallStackCPA, LockStatisticsCPA)
  //pass the state to LockStatisticsCPA to bind Callstack to lock
  private UsageStatisticsState oldState;

  public UsageStatisticsTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, UsageStatisticsCPAStatistics s, CodeCovering cover) throws InvalidConfigurationException {
    config.inject(this);
    wrappedTransfer = pWrappedTransfer;
    statistics = s;
    covering = cover;

    binderFunctionInfo = new HashMap<String, BinderFunctionInfo>();
    BinderFunctionInfo tmpInfo;
    for (String name : binderFunctions) {
      tmpInfo = new BinderFunctionInfo(name, config);
      binderFunctionInfo.put(name, tmpInfo);
    }
    handler = new ExpressionHandler();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws InterruptedException, CPATransferException {

    oldState = (UsageStatisticsState)pElement;
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
      UsageStatisticsState newState = oldState.clone(newWrappedState);

      newState = handleEdge(newState, pCfaEdge);
      if (newState != null) {
        results.add(newState);
      }
    }
  }

  private UsageStatisticsState handleEdge(UsageStatisticsState newState, CFAEdge pCfaEdge) throws CPATransferException {

    switch(pCfaEdge.getEdgeType()) {

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

      case AssumeEdge: {
        CAssumeEdge assumeEdge = (CAssumeEdge) pCfaEdge;
        handleAssumption(newState, assumeEdge.getExpression(), pCfaEdge);
        break;
      }

      case FunctionCallEdge: {
        handleFunctionCall(newState, (CFunctionCallEdge)pCfaEdge);
        break;
      }

      case FunctionReturnEdge:
      case ReturnStatementEdge:
      case BlankEdge:
      case CallToReturnEdge: {
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }

    return newState;
  }

  private void handleFunctionCall(UsageStatisticsState pNewState, CFunctionCallEdge edge) throws HandleCodeException {
    CStatement statement = edge.getRawAST().get();

    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CRightHandSide right = ((CFunctionCallAssignmentStatement)statement).getRightHandSide();
      CExpression variable = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();
      String functionName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();

      handler.setMode(functionName, Access.WRITE);
      variable.accept(handler);
      statistics.add(handler.result, pNewState, variable.getFileLocation().getStartingLineNumber(), EdgeType.ASSIGNMENT);
      // expression - only name of function
      if (right instanceof CFunctionCallExpression) {
        handleFunctionCallExpression(pNewState, variable, (CFunctionCallExpression)right);
      } else {
        //where is function?
        throw new HandleCodeException("Can't find function call here: " + right.toASTString());
      }

    } else if (statement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, null, ((CFunctionCallStatement)statement).getFunctionCallExpression());

    } else {
      throw new HandleCodeException("No function found");
    }

  }


  private void handleDeclaration(UsageStatisticsState pNewState, CDeclarationEdge declEdge) throws CPATransferException {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      // not a variable declaration
      if (declEdge.getDeclaration().getClass() == CFunctionDeclaration.class &&
          declEdge.getRawStatement().contains(";")) {
        covering.addException(declEdge.getLineNumber());
      }
      return;
    }
    CVariableDeclaration decl = (CVariableDeclaration)declEdge.getDeclaration();

    if (!decl.toASTString().equals(declEdge.getRawStatement())) {
      //CPA replace "int t;" into "int t = 0;", so here there isn't assignment

      if (!decl.toASTString().contains("CPAchecker_TMP"))
        //sometimes CPAChecker insert some declarations
        covering.addException(declEdge.getLineNumber());
      return;
    }

    CInitializer init = decl.getInitializer();

    if (init == null) {
      covering.addException(declEdge.getLineNumber());
      //no assumption
      return;
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression)init).getExpression();
      int line = initExpression.getFileLocation().getStartingLineNumber();
      String funcName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();
      handler.setMode(funcName, Access.READ);
      initExpression.accept(handler);

      statistics.add(handler.result, pNewState, line, EdgeType.DECLARATION);

      VariableIdentifier id = VariableIdentifier.createIdentifier(decl, funcName, 0);
      List<Pair<VariableIdentifier, Access>> result = new LinkedList<Pair<VariableIdentifier,Access>>();
      result.add(Pair.of(id, Access.WRITE));
      statistics.add(result, pNewState, line, EdgeType.DECLARATION);
    }
  }

  private void handleFunctionCallExpression(UsageStatisticsState pNewState, CExpression left, CFunctionCallExpression fcExpression) throws HandleCodeException {
    String functionName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();
    int line = fcExpression.getFileLocation().getStartingLineNumber();
    if (binderFunctions != null && binderFunctions.contains(functionCallName))
    {
      BinderFunctionInfo currentInfo = binderFunctionInfo.get(functionCallName);
      List<CExpression> params = fcExpression.getParameterExpressions();

      assert params.size() == currentInfo.parameters;

      if (currentInfo.linkInfo != null) {
        //Sometimes these functions are used not only for linkings.
        //For example, sdlGetFirst also deletes element.
        //So, if we can't link (no left side), we skip it
        if (currentInfo.linkInfo.getFirst() == 0 && left != null) {
          linkVariables(pNewState, left, params.get(currentInfo.linkInfo.getSecond() - 1));
        } else if (currentInfo.linkInfo.getSecond() == 0 && left != null) {
          linkVariables(pNewState, params.get(currentInfo.linkInfo.getFirst() - 1), left);
        } else if (left != null)
          linkVariables(pNewState, params.get(currentInfo.linkInfo.getFirst() - 1),
              params.get(currentInfo.linkInfo.getSecond() - 1));
      }
      for (int i = 0; i < params.size(); i++) {
        if (currentInfo.pInfo.get(i) == ParameterInfo.READ) {
          handler.setMode(functionName, Access.READ);
        } else if (currentInfo.pInfo.get(i) == ParameterInfo.WRITE) {
          handler.setMode(functionName, Access.WRITE);
        }
        params.get(i).accept(handler);
        statistics.add(handler.result, pNewState, line, EdgeType.FUNCTION_CALL);
      }
    }

    covering.addFunctionUsage(functionCallName);
    if (skippedfunctions != null && skippedfunctions.contains(functionName)) {
      CallstackState callstack = AbstractStates.extractStateByType(pNewState, CallstackState.class);
      throw new StopAnalysisException("Function " + functionCallName + " is skipped", callstack.getCallNode());
    }

    List<CExpression> params = fcExpression.getParameterExpressions();
    //It's strange, but callstack thinks, that we are already in called function
    /*CallstackState callstack = AbstractStates.extractStateByType(pNewState, CallstackState.class);
    if (callstack.getPreviousState() != null)
      //it means, that we call function
      functionName = callstack.getPreviousState().getCurrentFunction();
    else
      //we already in function and look on it signature
      //TODO now it works only with ABM
      return;*/
    for (CExpression p : params) {
      handler.setMode(functionName, Access.READ);
      p.accept(handler);

      statistics.add(handler.result, pNewState, p.getFileLocation().getStartingLineNumber(), EdgeType.FUNCTION_CALL);
    }
  }

  private void handleStatement(UsageStatisticsState pNewState, CStatement pStatement,
        CFAEdge pCfaEdge) throws HandleCodeException {

    String functionName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();
    int line = pStatement.getFileLocation().getStartingLineNumber();

    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment)pStatement;
      CExpression left = assignment.getLeftHandSide();
      CRightHandSide right = assignment.getRightHandSide();

      handler.setMode(functionName, Access.WRITE);
      left.accept(handler);

      statistics.add(handler.result, pNewState, line, EdgeType.ASSIGNMENT);

      if (right instanceof CExpression) {
        handler.setMode(functionName, Access.READ);
        ((CExpression)right).accept(handler);

        statistics.add(handler.result, pNewState, line, EdgeType.ASSIGNMENT);

        //linkVariables(pNewState, left, (CExpression)right);

      } else if (right instanceof CFunctionCallExpression) {

        handleFunctionCallExpression(pNewState, left, (CFunctionCallExpression)right);
      }
      else {
        throw new HandleCodeException("Unrecognised type of right side of assignment: " + assignment.asStatement().toASTString());
      }

    } else if (pStatement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, null, ((CFunctionCallStatement)pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      handler.setMode(functionName, Access.WRITE);
      ((CExpressionStatement)pStatement).getExpression().accept(handler);

      statistics.add(handler.result, pNewState, line, EdgeType.STATEMENT);

    } else {
      throw new HandleCodeException("Unrecognized statement: " + pStatement.toASTString());
    }
  }

  private void handleAssumption(UsageStatisticsState element, CExpression pExpression, CFAEdge cfaEdge) throws HandleCodeException {

    String functionName = AbstractStates.extractStateByType(element, CallstackState.class).getCurrentFunction();

    handler.setMode(functionName, Access.READ);
    pExpression.accept(handler);

    statistics.add(handler.result, element, cfaEdge.getLineNumber(), EdgeType.ASSUMPTION);
  }

  private void linkVariables(UsageStatisticsState state, CExpression in, CExpression from) throws HandleCodeException {
    String functionName = AbstractStates.extractStateByType(state, CallstackState.class).getCurrentFunction();
    List<Pair<VariableIdentifier, Access>> list;
    VariableIdentifier idIn, idFrom;

    /*if (in.getClass() != CIdExpression.class || (from.getClass() != CCastExpression.class && from.getClass() != CIdExpression.class))
      return;*/
    FirstVariableFinder finder = new FirstVariableFinder();
    finder.setMode(functionName, Access.READ);
    in.accept(finder);
    list = finder.result;
    if (list.size() != 1)
      return;
    idIn = list.get(0).getFirst();
    if (idIn == null)
      return;
    finder.setMode(functionName, Access.READ);
    from.accept(finder);
    list = finder.result;
    if (list.size() != 1)
      return;
    idFrom = list.get(0).getFirst();
    if (idFrom == null)
      return;

    if (idIn.getStatus() == Ref.VARIABLE && idIn.getType().getClass() == CPointerType.class) {

    } else if (idIn.getStatus() == Ref.REFERENCE && state.contains(idIn)) {
      idIn = state.get(idIn);
    } else {
      System.out.println(idIn.getName() + " and " + idFrom.getName() + " isn't linked");
      return;
    }

    if (idFrom.getStatus() == Ref.ADRESS ||
        (idFrom.getStatus() == Ref.VARIABLE && idIn.getStatus() == Ref.VARIABLE && (idIn.getType().getClass() == CPointerType.class))) {
       if (state.contains(idFrom)) {
         idFrom = state.get(idFrom);
       }
       state.put(idIn, idFrom.makeVariable());
     }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) {
    // in this method we could access the abstract domains of other CPAs
    // if required.
    return null;
  }

  public UsageStatisticsState getOldState() {
    return oldState;
  }
}
