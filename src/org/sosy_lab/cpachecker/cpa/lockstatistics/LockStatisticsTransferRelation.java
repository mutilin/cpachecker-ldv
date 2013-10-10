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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageStatisticsTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  private Map<String, AnnotationInfo> annotatedfunctions;

  private UsageStatisticsCPA stateGetter;
  private final Set<LockInfo> locks;

  /*@Option(name="exceptions",
      description="functions with parameters, which we don't need to use")
  private Set<String> exceptions;


  @Option(name="functionhandler", values={"LINUX", "OS"},toUppercase=true,
      description="which type of function handler we should use")
  private String HandleType = "LINUX";*/

  //private FunctionHandlerOS handler;
  private LogManager logger;

  public LockStatisticsTransferRelation(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;

    ConfigurationParser parser = new ConfigurationParser(config);

    locks = parser.parseLockInfo();
    annotatedfunctions = parser.parseAnnotatedFunctions();
   /* if (HandleType.equals("LINUX")) {
      handler = new FunctionHandlerLinux(lock, unlock, exceptions);
    }
    else if (HandleType.equals("OS")) {*/
    //handler = new FunctionHandlerOS(tmpInfo, logger);
    /*} else {
      throw new InvalidConfigurationException("Unsupported function handler");
    }*/
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge)
    throws CPATransferException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;

    LockStatisticsState successor;
    /*if (cfaEdge.getLineNumber() == 8081) {
      System.out.println("In CondWait()");
    }*/
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        String fCallName = ((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName();
      	if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName)) {
      		CFANode pred = ((CFunctionCallEdge)cfaEdge).getPredecessor();
      		logger.log(Level.FINER,"annotated name=" + fCallName + ", call"
                     + ", node=" + pred
                     + ", line=" + pred.getLineNumber()
                         + ", successor=" + lockStatisticsElement
                     );
      	}
        successor = handleFunctionCall(lockStatisticsElement, (CFunctionCallEdge)cfaEdge);
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName)) {
          successor.setRestoreState(lockStatisticsElement);
        }
        break;

      case FunctionReturnEdge:
        CFANode tmpNode = ((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getPredecessor();
        String fName =((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
          successor = lockStatisticsElement.clone();
          if (annotatedfunctions.get(fName).restoreLocks != null) {
            //At first, get restored state, because we can have a set of returned states
            //and all changes should be in terms of this returned state
            successor = successor.restore(annotatedfunctions.get(fName).restoreLocks, logger);

    		    logger.log(Level.FINER, "annotated name=" + fName + ", return"
                + ", node=" + tmpNode
                + ", line=" + tmpNode.getLineNumber()
                + ",\n\t successor=" + successor
                + ",\n\t element=" + element
                );

          }
          if (annotatedfunctions.get(fName).freeLocks != null) {
            //free some locks
            successor = successor.free(annotatedfunctions.get(fName).freeLocks, logger);
          }
          if (annotatedfunctions.get(fName).resetLocks != null) {
            successor = successor.reset(annotatedfunctions.get(fName).resetLocks, logger);
          }
          if (annotatedfunctions.get(fName).captureLocks != null) {
            Map<String, String> locks = annotatedfunctions.get(fName).captureLocks;
            for (String name : locks.keySet()) {
              processLock(successor, cfaEdge.getLineNumber(), findLockByName(name), locks.get(name));
            }
          }
          break;
        }
        successor = lockStatisticsElement.clone();
        break;

      default:
        successor = handleSimpleEdge(lockStatisticsElement, cfaEdge);
    }

    if (successor == null) {
      return Collections.emptySet();
    } else {
      return Collections.singleton(successor);
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, CFAEdge cfaEdge)
        throws CPATransferException {

    switch(cfaEdge.getEdgeType()) {
      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          return new LockStatisticsState();
        } else {
          return handleStatement(element, statement, cfaEdge.getPredecessor().getFunctionName());
        }

      case BlankEdge:
      case AssumeEdge:
      case ReturnStatementEdge:
      case DeclarationEdge:
      case CallToReturnEdge:
        return element.clone();

      case MultiEdge:
        LockStatisticsState tmpElement = element.clone();

        for (CFAEdge edge : (MultiEdge)cfaEdge) {
          tmpElement = handleSimpleEdge(tmpElement, edge);
        }
        return tmpElement;

      default:
        throw new UnrecognizedCFAEdgeException(cfaEdge);
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {
    return null;
  }

  public LockStatisticsState handleStatement(LockStatisticsState element, CStatement expression, String currentFunction) throws HandleCodeException {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        CFunctionCallExpression function = (CFunctionCallExpression) op2;
        String functionName = function.getFunctionNameExpression().toASTString();
        LockInfo lock = findLockByFunction(functionName);
        if (lock != null) {
          changeState(newElement, lock, functionName, op2.getFileLocation().getStartingLineNumber(), currentFunction,
                           function.getParameterExpressions());
        }
      }

    } else if (expression instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      CFunctionCallStatement statement = (CFunctionCallStatement) expression;
      String functionName = statement.getFunctionCallExpression().getFunctionNameExpression().toASTString();
      LockInfo lock = findLockByFunction(functionName);
      if (lock != null) {
       changeState(newElement, lock, functionName, statement.getFileLocation().getStartingLineNumber(), currentFunction,
          statement.getFunctionCallExpression().getParameterExpressions());
      }
    }
    return newElement;
  }

  public LockStatisticsState handleFunctionCall(LockStatisticsState element, CFunctionCallEdge callEdge) throws HandleCodeException {
    Set<CStatement> expressions = callEdge.getRawAST().asSet();
    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, statement, callEdge.getPredecessor().getFunctionName());
      }
    } else {
      String functionName = callEdge.getSuccessor().getFunctionName();
      LockInfo lock = findLockByFunction(functionName);
      if (lock != null) {
        changeState(newElement, lock, functionName, callEdge.getLineNumber(), callEdge.getPredecessor().getFunctionName(),
          callEdge.getArguments());
      }
    }
    return newElement;
  }

  private LockInfo findLockByFunction(String functionName) {
    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)
          || lock.UnlockFunctions.containsKey(functionName)
          || (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName))
          || (lock.setLevel != null && lock.setLevel.equals(functionName))
          ) {
        return lock;
      }
    }
    return null;
  }

  private LockInfo findLockByName(String name) {
    for (LockInfo lock : locks) {
      if (lock.lockName.equals(name)) {
        return lock;
      }
    }
    return null;
  }

  private void changeState(LockStatisticsState newElement, LockInfo lock, String functionName, int lineNumber
      , String currentFunction, List<CExpression> params) throws HandleCodeException {
    if (lock.LockFunctions.containsKey(functionName)) {
      int p = lock.LockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      processLock(newElement, lineNumber, lock, variable);
      return;

    } else if (lock.UnlockFunctions.containsKey(functionName)) {
      logger.log(Level.FINER, "Unlock at line " + lineNumber + "int function " + currentFunction);
      int p = lock.UnlockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      newElement.free(lock.lockName, variable, logger);
      return;

    } else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
      logger.log(Level.FINER, "Reset at line " + lineNumber + "int function " + currentFunction);
      int p = lock.ResetFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      newElement.reset(lock.lockName, variable, logger);
      return;

    } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
      int p = Integer.parseInt(params.get(0).toASTString()); //new level
      processSetLevel(newElement, lineNumber, p - 1, lock, "");
      return;//they count from 1
    }
  }

  public void setUsCPA(UsageStatisticsCPA cpa) {
    stateGetter = cpa;
  }

  private void processLock(LockStatisticsState newElement, int lineNumber, LockInfo lock, String variable) throws HandleCodeException {
    CallstackState reducedCallstack =
        AbstractStates.extractStateByType(((UsageStatisticsTransferRelation)stateGetter.getTransferRelation()).getOldState(),
            CallstackState.class);
    CallstackState callstack = stateGetter.getStats().createStack(reducedCallstack);

    logger.log(Level.FINER, "Lock at line " + lineNumber + ", Callstack: " + callstack);
    int d = newElement.getCounter(lock.lockName, variable);

    if (d < lock.maxLock) {
      newElement.add(lock.lockName, lineNumber, callstack, reducedCallstack, variable, logger);
    } else {
      Stack<AccessPoint> access = newElement.findLock(lock.lockName, variable).getAccessPoints();
      StringBuilder message = new StringBuilder();
      message.append("Try to lock " + lock.lockName + " more, than " + lock.maxLock + " in " + lineNumber + " line. Previous were in ");
      for (AccessPoint point : access) {
        message.append(point.line.getLine() + ", ");
      }
      message.delete(message.length() - 2, message.length());
      System.err.println(message.toString());
    }
  }

  private void processSetLevel(LockStatisticsState newElement, int lineNumber, int level, LockInfo lock, String variable) throws HandleCodeException {
    CallstackState reducedCallstack =
        AbstractStates.extractStateByType(((UsageStatisticsTransferRelation)stateGetter.getTransferRelation()).getOldState(),
            CallstackState.class);
    CallstackState callstack = stateGetter.getStats().createStack(reducedCallstack);

    logger.log(Level.FINER, "Set a lock level " + level + " at line " + lineNumber + ", Callstack: " + callstack);
    newElement.set(lock.lockName, level, lineNumber, callstack, reducedCallstack, "");
  }

  //private
}
