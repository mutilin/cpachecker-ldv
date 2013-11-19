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
import java.util.HashSet;
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
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
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
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  private final Map<String, AnnotationInfo> annotatedfunctions;

  private UsageStatisticsCPA stateGetter;
  private final Set<LockInfo> locks;

  /*@Option(name="exceptions",
      description="functions with parameters, which we don't need to use")
  private Set<String> exceptions;
*/
  private LogManager logger;

  public LockStatisticsTransferRelation(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;

    ConfigurationParser parser = new ConfigurationParser(config);

    locks = parser.parseLockInfo();
    annotatedfunctions = parser.parseAnnotatedFunctions();
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessors(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge) {

    return Collections.singleton(((LockStatisticsState)element).clone());
  }

  public Collection<LockStatisticsState> getAbstractSuccessors0(AbstractState element, CallstackState state, CFAEdge cfaEdge) throws UnrecognizedCCodeException
     {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsState successor;
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        String fCallName = ((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName();
        successor = handleFunctionCall(lockStatisticsElement, state, (CFunctionCallEdge)cfaEdge);
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName)) {
          logger.log(Level.FINE, "Annotated function " + fCallName + " call"
              + ", \n\tline = " + cfaEdge.getLineNumber());
          successor.setRestoreState(lockStatisticsElement);
        }
        break;

      case FunctionReturnEdge:
        CFANode tmpNode = ((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getPredecessor();
        String fName =((CFunctionReturnEdge)cfaEdge).getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
          successor = lockStatisticsElement.clone();
          logger.log(Level.FINE, "Annotated function " + fName + " return");
          if (annotatedfunctions.get(fName).restoreLocks != null) {
            successor = successor.restore(annotatedfunctions.get(fName).restoreLocks, logger);

            logger.log(Level.FINER, "Restore " + annotatedfunctions.get(fName).restoreLocks
                + ", \n\tline=" + tmpNode.getLineNumber());

          } else if (annotatedfunctions.get(fName).freeLocks != null) {
            //Free in state at first restores saved state
            logger.log(Level.FINER, "Free " + annotatedfunctions.get(fName).freeLocks.keySet() + " in " + successor
                        + ", \n\t line = " + tmpNode.getLineNumber());
            successor = successor.free(annotatedfunctions.get(fName).freeLocks, logger);
          } else if (annotatedfunctions.get(fName).resetLocks != null) {
            //Reset in state at first restores saved state
            logger.log(Level.FINER, "Reset " + annotatedfunctions.get(fName).resetLocks.keySet() + " in " + successor
                + ", \n\t line = " + tmpNode.getLineNumber());
            successor = successor.reset(annotatedfunctions.get(fName).resetLocks, logger);
          } else if (annotatedfunctions.get(fName).captureLocks != null) {
            Map<String, String> locks = annotatedfunctions.get(fName).captureLocks;
            logger.log(Level.FINER, "Force lock of " + annotatedfunctions.get(fName).captureLocks.keySet() + " in " + successor
                + ", \n\t line = " + tmpNode.getLineNumber());
            successor = successor.restore(locks, logger);
            for (String name : locks.keySet()) {
              processLock(successor, state, cfaEdge.getLineNumber(), findLockByName(name), locks.get(name));
            }
          }
          logger.log(Level.FINEST, "\tPredessor = " + lockStatisticsElement
                  + "\n\tSuccessor = " + successor);
        } else {
          successor = lockStatisticsElement.clone();
        }
        break;

      default:
        successor = handleSimpleEdge(lockStatisticsElement, state, cfaEdge);
    }

    if (!successor.equals(lockStatisticsElement)) {
      return Collections.singleton(successor);
    } else {
      return null;
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, CallstackState state, CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    switch(cfaEdge.getEdgeType()) {
      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          return new LockStatisticsState();
        } else {
          return handleStatement(element, state, statement, cfaEdge.getPredecessor().getFunctionName());
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
          tmpElement = handleSimpleEdge(tmpElement, state, edge);
        }
        return tmpElement;
      default:
        throw new UnrecognizedCCodeException("Unknown edge type", cfaEdge);
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {

    logger.log(Level.FINEST, "Strengthen LockStatistics result");
    CallstackState state = null;
    for (AbstractState tmpState : elements) {
      if (tmpState instanceof CallstackState) {
        state = (CallstackState)tmpState;
        break;
      }
    }
    assert (state != null);
    return getAbstractSuccessors0(element, state, cfaEdge);
  }

  private LockStatisticsState handleStatement(LockStatisticsState element, CallstackState state, CStatement expression,
      String currentFunction) {

    LockStatisticsState newElement = element.clone();

    if (expression instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)expression).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        CFunctionCallExpression function = (CFunctionCallExpression) op2;
        String functionName = function.getFunctionNameExpression().toASTString();
        Set<LockInfo> changedLocks = findLockByFunction(functionName);
        for (LockInfo lock : changedLocks) {
          changeState(newElement, state, lock, functionName, op2.getFileLocation().getStartingLineNumber(), currentFunction,
                           function.getParameterExpressions());
        }
      } else {
        /*
         * threadDispatchLevel = 1;
         */
        CLeftHandSide leftSide = ((CAssignment) expression).getLeftHandSide();
        CRightHandSide rightSide = ((CAssignment) expression).getRightHandSide();
        if (rightSide instanceof CIntegerLiteralExpression) {
          int level = ((CIntegerLiteralExpression)rightSide).getValue().intValue();
          String variable = leftSide.toASTString();
          LockInfo lock = findLockByVariable(variable);
          if (lock != null) {
            processSetLevel(newElement, state, expression.getFileLocation().getStartingLineNumber(), level, lock);
          }
        }
      }

    } else if (expression instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      CFunctionCallStatement statement = (CFunctionCallStatement) expression;
      String functionName = statement.getFunctionCallExpression().getFunctionNameExpression().toASTString();
      Set<LockInfo> changedLocks = findLockByFunction(functionName);
      for (LockInfo lock : changedLocks) {
       changeState(newElement, state, lock, functionName, statement.getFileLocation().getStartingLineNumber(), currentFunction,
          statement.getFunctionCallExpression().getParameterExpressions());
      }
    }
    return newElement;
  }

  private LockStatisticsState handleFunctionCall(LockStatisticsState element, CallstackState state, CFunctionCallEdge callEdge) {
    Set<CFunctionCall> expressions = callEdge.getRawAST().asSet();
    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, state, statement, callEdge.getPredecessor().getFunctionName());
      }
    } else {
      String functionName = callEdge.getSuccessor().getFunctionName();
      Set<LockInfo> changedLocks = findLockByFunction(functionName);
      for (LockInfo lock : changedLocks) {
        changeState(newElement, state, lock, functionName, callEdge.getLineNumber(), callEdge.getPredecessor().getFunctionName(),
          callEdge.getArguments());
      }
    }
    return newElement;
  }

  private Set<LockInfo> findLockByFunction(String functionName) {
    Set<LockInfo> changedLocks = new HashSet<>();
    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)
          || lock.UnlockFunctions.containsKey(functionName)
          || (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName))
          || (lock.setLevel != null && lock.setLevel.equals(functionName))
          ) {
        changedLocks.add(lock);
      }
    }
    return changedLocks;
  }

  /*private Set<LockInfo> findLockByLockFunciton(String functionName) {
    Set<LockInfo> changedLocks = new HashSet<>();
    for (LockInfo lock : locks) {
      if (lock.LockFunctions.containsKey(functionName)) {
        changedLocks.add(lock);
      }
    }
    return changedLocks;
  }*/

  private LockInfo findLockByVariable(String varName) {
    //LockInfo lock();
    for (LockInfo lock : locks) {
      for (String variable : lock.Variables) {
        if (variable.equals(varName)) {
          return lock;
        }
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

  private void changeState(LockStatisticsState newElement, CallstackState state, LockInfo lock, String functionName, int lineNumber
      , String currentFunction, List<CExpression> params) {
    if (lock.LockFunctions.containsKey(functionName)) {
      int p = lock.LockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      processLock(newElement, state, lineNumber, lock, variable);
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
      processSetLevel(newElement, state, lineNumber, p, lock);
      return;//they count from 1
    }
  }

  public void setUsCPA(UsageStatisticsCPA cpa) {
    stateGetter = cpa;
  }

  private void processLock(LockStatisticsState newElement, CallstackState state, int lineNumber, LockInfo lock, String variable) {
    CallstackState reducedCallstack = state;
    CallstackState callstack;
    try {
      callstack = stateGetter.getStats().createStack(reducedCallstack);
    } catch (HandleCodeException e) {
      logger.log(Level.WARNING, e.getLocalizedMessage());
      return;
    }

    logger.log(Level.FINER, "Lock at line " + lineNumber + ", Callstack: " + callstack);
    int d = newElement.getCounter(lock.lockName, variable);

    if (d < lock.maxLock) {
      newElement.add(lock.lockName, lineNumber, callstack, reducedCallstack, variable, logger);
    } else {
      Stack<AccessPoint> access = newElement.findLock(lock.lockName, variable).getAccessPoints();
      StringBuilder message = new StringBuilder();
      message.append("Try to lock " + lock.lockName + " more, than " + lock.maxLock + " in " + lineNumber + " line. Previous were in ");
      for (AccessPoint point : access) {
        message.append(point.getLineInfo().getLine() + ", ");
      }
      message.delete(message.length() - 2, message.length());
      logger.log(Level.WARNING, message.toString());
    }
  }

  private void processSetLevel(LockStatisticsState newElement, CallstackState state, int lineNumber, int level, LockInfo lock) {
    CallstackState reducedCallstack = state;
    CallstackState callstack;
    try {
      callstack = stateGetter.getStats().createStack(reducedCallstack);
    } catch (HandleCodeException e) {
      System.err.println(e.getLocalizedMessage());
      return;
    }
    logger.log(Level.FINER, "Set a lock level " + level + " at line " + lineNumber + ", Callstack: " + callstack);
    newElement.set(lock.lockName, level, lineNumber, callstack, reducedCallstack, "");
  }
}
