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
import java.util.Collections;
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
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsTransferRelation;
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

  @Option(description = "functions, which we don't analize")
  private Set<String> skippedfunctions = null;

  @Option(description = "functions, which are used to bind variables (like list elements are binded to list variable)")
  private Set<String> binderFunctions = null;

  @Option(name="abortfunctions", description="functions, which stops analysis")
  private Set<String> abortfunctions;

  private final CallstackTransferRelation callstackTransfer;
  private final LockStatisticsTransferRelation lockstatTransfer;
  private final VariableSkipper varSkipper;

  private Map<String, BinderFunctionInfo> binderFunctionInfo;
  private final LogManager logger;

  public UsageStatisticsTransferRelation(TransferRelation pWrappedTransfer,
      Configuration config, LogManager pLogger, UsageStatisticsCPAStatistics s,
      CallstackTransferRelation transfer, LockStatisticsTransferRelation lTransfer) throws InvalidConfigurationException {
    config.inject(this);
    wrappedTransfer = pWrappedTransfer;
    callstackTransfer = transfer;
    lockstatTransfer = lTransfer;
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
    varSkipper = new VariableSkipper(config);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision)
      throws InterruptedException, CPATransferException {

    Collection<AbstractState> results;
    assert (pPrecision instanceof UsageStatisticsPrecision);

    CFANode node = extractLocation(pElement);
    results = new ArrayList<>(node.getNumLeavingEdges());

    for (int edgeIdx = 0; edgeIdx < node.getNumLeavingEdges(); edgeIdx++) {
      CFAEdge edge = node.getLeavingEdge(edgeIdx);
      results.addAll(getAbstractSuccessorsForEdge(pElement, pPrecision, edge));
    }
    return results;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision,
      CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    statistics.transferRelationTimer.start();
    Collection<AbstractState> result = new ArrayList<>();
    CFAEdge currentEdge = pCfaEdge;
    UsageStatisticsState oldState = (UsageStatisticsState) pState;
    CFANode node = AbstractStates.extractLocation(oldState);
    if (pCfaEdge instanceof CFunctionCallEdge || pCfaEdge instanceof CStatementEdge) {
      String functionName = null;
      if (pCfaEdge instanceof CFunctionCallEdge) {
        functionName = ((CFunctionCallEdge)pCfaEdge).getSuccessor().getFunctionName();
      } else {
        CStatement statement = ((CStatementEdge)pCfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement) {
          functionName = ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toString();
        }
      }
      if (functionName != null && abortfunctions != null && abortfunctions.contains(functionName)) {
        logger.log(Level.FINEST, currentEdge + " is abort edge, analysis was stopped");
        statistics.transferRelationTimer.stop();
        return Collections.emptySet();
      }
    }

    boolean needToReset = false;
    if (checkFunciton(pCfaEdge, skippedfunctions)) {
      callstackTransfer.setFlag();
      needToReset = true;
      if (node.getLeavingSummaryEdge() != null) {
        currentEdge = node.getLeavingSummaryEdge();
        logger.log(Level.FINEST, ((CFunctionSummaryEdge)currentEdge).getFunctionEntry().getFunctionName() + " is skipped");
      } else {
        throw new CPATransferException("Cannot find summary edge for " + pCfaEdge + " as skipped function");
      }
    }

    AbstractState oldWrappedState = oldState.getWrappedState();
    UsageStatisticsState newState = oldState.clone();
    newState = handleEdge((UsageStatisticsPrecision)pPrecision, newState, pCfaEdge);
    Collection<? extends AbstractState> newWrappedStates = wrappedTransfer.getAbstractSuccessorsForEdge(oldWrappedState,
        ((UsageStatisticsPrecision)pPrecision).getWrappedPrecision(), currentEdge);
    for (AbstractState newWrappedState : newWrappedStates) {
      UsageStatisticsState resultState = newState.clone(newWrappedState);
      if (resultState != null) {
        result.add(resultState);
      }
    }
    if (needToReset) {
      callstackTransfer.resetFlag();
    }
    statistics.transferRelationTimer.stop();
    return result;
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

    if (decl.isGlobal()) {
      return;
    }

    CInitializer init = decl.getInitializer();

    if (init == null) {
      //no assignment
      return;
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression)init).getExpression();
      //Use EdgeType assignement for initializer expression to avoid mistakes related to expressions "int CPACHECKER_TMP_0 = global;"
      visitStatement(pNewState, pPrecision, initExpression, Access.READ, EdgeType.ASSIGNMENT);

      if (!decl.toASTString().equals(declEdge.getRawStatement())) {
        //CPA replace "int t;" into "int t = 0;", so here there isn't assignment
        //It is right, but creates false unsafes.
        return;
      }
      String funcName = AbstractStates.extractStateByType(pNewState, CallstackState.class).getCurrentFunction();

      AbstractIdentifier id = IdentifierCreator.createIdentifier(decl, funcName, 0);
      visitId(pNewState, pPrecision, id, Access.WRITE, EdgeType.DECLARATION, declEdge.getLineNumber());
    }
  }

  private void handleFunctionCallExpression(final UsageStatisticsState pNewState
      , final UsageStatisticsPrecision pPrecision, final CExpression left, final CFunctionCallExpression fcExpression) throws HandleCodeException {

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

  private void handleStatement(final UsageStatisticsState pNewState
      , final UsageStatisticsPrecision pPrecision, final CStatement pStatement, final CFAEdge pCfaEdge) throws HandleCodeException {

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

  private void linkVariables(final UsageStatisticsState state, final CExpression left, final List<CExpression> params
      , final Pair<LinkerInfo, LinkerInfo> linkInfo) throws HandleCodeException {
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

  private void linkId(final UsageStatisticsState state, final AbstractIdentifier idIn, AbstractIdentifier idFrom) throws HandleCodeException {
    if (idIn == null || idFrom == null) {
      return;
    }
    if (state.containsLinks(idFrom)) {
      idFrom = state.getLinks(idFrom);
    }
    logger.log(Level.FINEST, "Link " + idIn + " and " + idFrom);
    state.put(idIn, idFrom);
  }

  private void visitStatement(final UsageStatisticsState state, final UsageStatisticsPrecision pPrecision
      , final CExpression expression, final Access access, final EdgeType eType) throws HandleCodeException {
    String functionName = AbstractStates.extractStateByType(state, CallstackState.class).getCurrentFunction();
    handler.setMode(functionName, access);
    expression.accept(handler);

    for (Pair<AbstractIdentifier, Access> pair : handler.result) {
      visitId(state, pPrecision, pair.getFirst(), pair.getSecond(), eType, expression.getFileLocation().getStartingLineNumber());
    }

  }

  private void visitId(final UsageStatisticsState state, final UsageStatisticsPrecision pPrecision
      , final AbstractIdentifier id, final Access access, final EdgeType eType, final int line) throws HandleCodeException {

    //Precise information, using results of shared analysis
    if (! (id instanceof SingleIdentifier)) {
      return;
    }

    SingleIdentifier singleId = (SingleIdentifier) id;

    CFANode node = AbstractStates.extractLocation(state);
    Map<GeneralIdentifier, DataType> localInfo = pPrecision.get(node);

    if (localInfo != null && singleId.getType(localInfo) == DataType.LOCAL) {
      logger.log(Level.FINER, singleId + " is considered to be local, so it wasn't add to statistics");
      return;
    }
    CallstackState fullCallstack = lockstatTransfer.getFullCallstack();

    if (fullCallstack == null) {
      //No ABM, so get real callstack
      fullCallstack = AbstractStates.extractStateByType(state, CallstackState.class);
    }

    if (state.containsLinks(singleId)) {
      singleId = (SingleIdentifier) state.getLinks(id);
    }

    if (varSkipper.shouldBeSkipped(singleId)) {
      return;
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
    LineInfo lineInfo = new LineInfo(line, node);
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
