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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsPrecision;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.BinderFunctionInfo.LinkerInfo;
import org.sosy_lab.cpachecker.cpa.usagestatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsTransferRelation implements TransferRelation {

  private final TransferRelation wrappedTransfer;
  private final UsageStatisticsCPAStatistics statistics;
  private final ExpressionHandler handler;

  @Option(description = "variables, which will not be saved in statistics")
  private Set<String> skippedvariables = null;

  @Option(description = "functions, which we don't analize")
  private Set<String> skippedfunctions = null;

  @Option(description = "functions, which are used to bind variables (like list elements are binded to list variable)")
  private Set<String> binderFunctions = null;

  @Option(name="abortfunctions", description="functions, which stops analysis")
  private Set<String> abortfunctions;

  private final CallstackTransferRelation callstackTransfer;

  private Map<String, BinderFunctionInfo> binderFunctionInfo;
  private final LogManager logger;

  public UsageStatisticsTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, LogManager pLogger, UsageStatisticsCPAStatistics s, CallstackTransferRelation transfer) throws InvalidConfigurationException {
    config.inject(this);
    wrappedTransfer = pWrappedTransfer;
    callstackTransfer = transfer;
    statistics = s;

    binderFunctionInfo = new HashMap<>();
    if (binderFunctions != null) {
      BinderFunctionInfo tmpInfo;
      for (String name : binderFunctions) {
        tmpInfo = new BinderFunctionInfo(name, config);
        binderFunctionInfo.put(name, tmpInfo);
      }
    }
    handler = new ExpressionHandler();
    logger = pLogger;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge)
      throws InterruptedException, CPATransferException {

    Collection<UsageStatisticsState> results;
    statistics.transferRelationTimer.start();
    assert (pPrecision instanceof UsageStatisticsPrecision);

    if (pCfaEdge == null) {
      CFANode node = extractLocation(pElement);
      results = new ArrayList<>(node.getNumLeavingEdges());

      for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
        CFAEdge edge = node.getLeavingEdge(edgeIdx);
          getAbstractSuccessorForEdge((UsageStatisticsState)pElement, (UsageStatisticsPrecision)pPrecision, edge, results);
      }

    } else {
      results = new ArrayList<>(1);
      getAbstractSuccessorForEdge((UsageStatisticsState)pElement, (UsageStatisticsPrecision)pPrecision, pCfaEdge, results);

    }
    statistics.transferRelationTimer.stop();
    return results;
  }

  private void getAbstractSuccessorForEdge(UsageStatisticsState oldState,
      UsageStatisticsPrecision pPrecision, CFAEdge pCfaEdge, Collection<UsageStatisticsState> results)
      throws InterruptedException, CPATransferException {

    CFAEdge currentEdge = pCfaEdge;

    CFANode node = AbstractStates.extractLocation(oldState);
    if (node instanceof CFunctionEntryNode && abortfunctions.contains(node.getFunctionName())) {
      logger.log(Level.FINEST, currentEdge + " is abort edge, analysis was stopped");
      return;
    }

    if (checkFunciton(pCfaEdge, skippedfunctions)) {
      callstackTransfer.setFlag();
      //Find right summary edge
      //CFANode node = AbstractStates.extractLocation(oldState);
      for (int k = 0; k < node.getNumLeavingEdges(); k++) {
        currentEdge = node.getLeavingEdge(k);
        if (currentEdge instanceof CFunctionSummaryStatementEdge) {
          break;
        }
      }
      assert (currentEdge instanceof CFunctionSummaryStatementEdge);
      logger.log(Level.FINEST, ((CFunctionSummaryStatementEdge)currentEdge).getFunctionName() + " is skipped due to configuration");
    }

    AbstractState oldWrappedState = oldState.getWrappedState();
    UsageStatisticsState newState = oldState.clone();
    newState = handleEdge(pPrecision, newState, pCfaEdge);
    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessors(oldWrappedState, pPrecision.getWrappedPrecision(), currentEdge);
    for (AbstractState newWrappedState : newWrappedStates) {
      UsageStatisticsState resultState = newState.clone(newWrappedState);
      if (resultState != null) {
        results.add(resultState);
      }
    }
  }

  private boolean checkFunciton(CFAEdge pCfaEdge, Set<String> functionSet) {
    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      String FunctionName = ((FunctionCallEdge)pCfaEdge).getSuccessor().getFunctionName();
      if (functionSet != null && functionSet.contains(FunctionName)) {
        return true;
      }
    }
    return false;
  }

  private UsageStatisticsState handleEdge(UsageStatisticsPrecision precision, UsageStatisticsState newState
      , CFAEdge pCfaEdge) throws CPATransferException {

    switch(pCfaEdge.getEdgeType()) {

      case DeclarationEdge: {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        handleDeclaration(newState, precision, declEdge);
        break;
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        handleStatement(newState, precision, statementEdge.getStatement(), pCfaEdge);
        break;
      }

      case AssumeEdge: {
        visitStatement(newState, precision, ((CAssumeEdge)pCfaEdge).getExpression(), Access.READ, EdgeType.ASSUMPTION);
        break;
      }

      case FunctionCallEdge: {
        handleFunctionCall(newState, precision, (CFunctionCallEdge)pCfaEdge);
        break;
      }

      case FunctionReturnEdge:
      case ReturnStatementEdge:
      case BlankEdge:
      case CallToReturnEdge: {
        break;
      }

      case MultiEdge:
        for (CFAEdge edge : ((MultiEdge)pCfaEdge).getEdges()) {
          newState = handleEdge(precision, newState, edge);
        }
        break;

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }

    return newState;
  }

  private void handleFunctionCall(UsageStatisticsState pNewState
      , UsageStatisticsPrecision pPrecision, CFunctionCallEdge edge) throws HandleCodeException {
    CStatement statement = edge.getRawAST().get();
    /*String functionName = edge.getSuccessor().getFunctionName();
    if (functionName.equals("sync")) {
      System.out.println("sync function");
    }*/
   /* if (abortfunctions != null && abortfunctions.contains(functionName)) {
      pNewState = null;
      logger.log(Level.FINEST, functionName + " is abort function and was skipped");
      return;
    }*/
    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CRightHandSide right = ((CFunctionCallAssignmentStatement)statement).getRightHandSide();
      CExpression variable = ((CFunctionCallAssignmentStatement)statement).getLeftHandSide();

      visitStatement(pNewState, pPrecision, variable, Access.WRITE, EdgeType.ASSIGNMENT);
      // expression - only name of function
      if (right instanceof CFunctionCallExpression) {
        handleFunctionCallExpression(pNewState, pPrecision, variable, (CFunctionCallExpression)right);
      } else {
        //where is function?
        throw new HandleCodeException("Can't find function call here: " + right.toASTString());
      }

    } else if (statement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, pPrecision, null, ((CFunctionCallStatement)statement).getFunctionCallExpression());

    } else {
      throw new HandleCodeException("No function found");
    }
  }

  private void handleDeclaration(UsageStatisticsState pNewState, UsageStatisticsPrecision pPrecision, CDeclarationEdge declEdge) throws CPATransferException {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      // not a variable declaration
      return;
    }
    CVariableDeclaration decl = (CVariableDeclaration)declEdge.getDeclaration();

    if (!decl.toASTString().equals(declEdge.getRawStatement())) {
      //CPA replace "int t;" into "int t = 0;", so here there isn't assignment
      //It is right, but creates false unsafes.
      return;
    }

    CInitializer init = decl.getInitializer();

    if (init == null) {
      //no assignment
      return;
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression)init).getExpression();
      visitStatement(pNewState, pPrecision, initExpression, Access.READ, EdgeType.DECLARATION);

      String funcName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();

      AbstractIdentifier id = IdentifierCreator.createIdentifier(decl, funcName, 0);
      visitId(pNewState, pPrecision, id, Access.WRITE, EdgeType.DECLARATION, declEdge.getLineNumber());
    }
  }

  private void handleFunctionCallExpression(UsageStatisticsState pNewState
      , UsageStatisticsPrecision pPrecision, CExpression left, CFunctionCallExpression fcExpression) throws HandleCodeException {

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();
    if (binderFunctions != null && binderFunctions.contains(functionCallName))
    {
      BinderFunctionInfo currentInfo = binderFunctionInfo.get(functionCallName);
      List<CExpression> params = fcExpression.getParameterExpressions();

      assert params.size() == currentInfo.parameters;

      linkVariables(pNewState, left, params, currentInfo.linkInfo);

      AbstractIdentifier id;
      IdentifierCreator creator = new IdentifierCreator();
      String functionName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();
      creator.clear(functionName);

      for (int i = 0; i < params.size(); i++) {
        creator.setDereference(currentInfo.pInfo.get(i).dereference);
        id = params.get(i).accept(creator);
        visitId(pNewState, pPrecision, id, currentInfo.pInfo.get(i).access, EdgeType.FUNCTION_CALL, fcExpression.getFileLocation().getStartingLineNumber());
      }

    } else {
      for (CExpression p : fcExpression.getParameterExpressions()) {
        visitStatement(pNewState, pPrecision, p, Access.READ, EdgeType.FUNCTION_CALL);
      }
    }
  }

  private void handleStatement(UsageStatisticsState pNewState
      , UsageStatisticsPrecision pPrecision, CStatement pStatement, CFAEdge pCfaEdge) throws HandleCodeException {

    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment)pStatement;
      CExpression left = assignment.getLeftHandSide();
      CRightHandSide right = assignment.getRightHandSide();

      visitStatement(pNewState, pPrecision, left, Access.WRITE, EdgeType.ASSIGNMENT);

      if (right instanceof CExpression) {
        visitStatement(pNewState, pPrecision, (CExpression)right, Access.READ, EdgeType.ASSIGNMENT);

      } else if (right instanceof CFunctionCallExpression) {
        handleFunctionCallExpression(pNewState, pPrecision, left, (CFunctionCallExpression)right);

      } else {
        throw new HandleCodeException("Unrecognised type of right side of assignment: " + assignment.toASTString());
      }

    } else if (pStatement instanceof CFunctionCallStatement) {
      handleFunctionCallExpression(pNewState, pPrecision, null, ((CFunctionCallStatement)pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      visitStatement(pNewState, pPrecision, ((CExpressionStatement)pStatement).getExpression(), Access.WRITE, EdgeType.STATEMENT);

    } else {
      throw new HandleCodeException("Unrecognized statement: " + pStatement.toASTString());
    }
  }

  private void linkVariables(UsageStatisticsState state, CExpression left, List<CExpression> params
      , Pair<LinkerInfo, LinkerInfo> linkInfo) throws HandleCodeException {
    String function = AbstractStates.extractStateByType(state, CallstackState.class).getCurrentFunction();
    AbstractIdentifier leftId, rightId;
    IdentifierCreator creator = new IdentifierCreator();
    creator.clear(function);

    if (linkInfo != null) {
      //Sometimes these functions are used not only for linkings.
      //For example, sdlGetFirst also deletes element.
      //So, if we can't link (no left side), we skip it
      LinkerInfo info1, info2;
      info1 = linkInfo.getFirst();
      info2 = linkInfo.getSecond();
      if (info1.num == 0 && left != null) {
        creator.setDereference(info1.dereference);
        leftId = left.accept(creator);
        creator.setDereference(info2.dereference);
        rightId = params.get(info2.num - 1).accept(creator);

      } else if (info2.num == 0 && left != null) {
        creator.setDereference(info2.dereference);
        rightId = left.accept(creator);
        creator.setDereference(info1.dereference);
        leftId = params.get(info1.num - 1).accept(creator);

      } else if (info1.num > 0 && info2.num > 0) {
        creator.setDereference(info1.dereference);
        leftId = params.get(info1.num - 1).accept(creator);
        creator.setDereference(info2.dereference);
        rightId = params.get(info2.num - 1).accept(creator);
      } else {
        /* f.e. sdlGetFirst(), which is used for deleting element
         * we don't link, but it isn't an error
         */
        return;
      }
      linkId(state, leftId, rightId);
    }

  }

  private void linkId(UsageStatisticsState state, AbstractIdentifier idIn, AbstractIdentifier idFrom) throws HandleCodeException {
    if (idIn == null || idFrom == null) {
      return;
    }
    if (state.containsLinks(idFrom)) {
      idFrom = state.getLinks(idFrom);
    }
    logger.log(Level.FINEST, "Link " + idIn + " and " + idFrom);
    state.put(idIn, idFrom);
  }

  private void visitStatement(UsageStatisticsState state, UsageStatisticsPrecision pPrecision
      , CExpression expression, Access access, EdgeType eType) throws HandleCodeException {
    String functionName = AbstractStates.extractStateByType(state, CallstackState.class).getCurrentFunction();
    handler.setMode(functionName, access);
    expression.accept(handler);

    for (Pair<AbstractIdentifier, Access> pair : handler.result) {
      visitId(state, pPrecision, pair.getFirst(), pair.getSecond(), eType, expression.getFileLocation().getStartingLineNumber());
    }

  }

  private void visitId(UsageStatisticsState state, UsageStatisticsPrecision pPrecision
      , AbstractIdentifier id, Access access, EdgeType eType, int line) throws HandleCodeException {

    //Precise information, using results of shared analysis
    if (! (id instanceof SingleIdentifier)) {
      return;
    }

    SingleIdentifier singleId = (SingleIdentifier) id;

    CFANode node = AbstractStates.extractLocation(state);
    Map<GeneralIdentifier, DataType> localInfo = pPrecision.get(node);

    if (localInfo != null && LocalState.getType(localInfo, singleId) == DataType.LOCAL) {
      logger.log(Level.FINER, singleId + " is considered to be local, so it wasn't add to statistics");
      return;
    }

    CallstackState fullCallstack = pPrecision.retrieveWrappedPrecision(LockStatisticsPrecision.class).getPreciseState();

    if (fullCallstack == null) {
      //No ABM, so get real callstack
      fullCallstack = AbstractStates.extractStateByType(state, CallstackState.class);
    }

    if (state.containsLinks(singleId)) {
      singleId = (SingleIdentifier) state.getLinks(id);
    }

    if (skippedvariables != null && skippedvariables.contains(singleId.getName())) {
      return;
    } else if (skippedvariables != null && singleId instanceof StructureIdentifier) {
      AbstractIdentifier owner = singleId;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (owner instanceof SingleIdentifier && skippedvariables.contains(((SingleIdentifier)owner).getName())) {
          return;
        }
      }
    }

    if (singleId instanceof LocalVariableIdentifier && singleId.getDereference() <= 0) {
      //we don't save in statistics ordinary local variables
      return;
    }
    if (singleId instanceof StructureIdentifier && !singleId.isGlobal() && !singleId.isPointer()) {
      //skips such cases, as 'a.b'
      return;
    }
    if (singleId instanceof StructureIdentifier) {
      singleId = ((StructureIdentifier)singleId).toStructureFieldIdentifier();
    }
    logger.log(Level.FINER, "Add id " + singleId + " to unsafe statistics");
    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    logger.log(Level.FINEST, "Its locks are: " + lockState);

    //We can't get line from location, because it is old state
    LineInfo lineInfo = new LineInfo(line);
    EdgeInfo info = new EdgeInfo(eType);

    UsageInfo usage = new UsageInfo(access, lineInfo, info, lockState, fullCallstack);

    state.addUsage(singleId, usage);
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
