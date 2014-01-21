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
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

import com.google.common.collect.ImmutableMap;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  private final Map<String, AnnotationInfo> annotatedfunctions;

  private final Set<LockInfo> locks;

  /*@Option(name="exceptions",
      description="functions with parameters, which we don't need to use")
  private Set<String> exceptions;
*/
  private LogManager logger;

  int i = 0;
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

  public Collection<LockStatisticsState> getAbstractSuccessors0(AbstractState element, Precision pPrecision,
      CallstackState state, CFAEdge cfaEdge) throws UnrecognizedCCodeException
     {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsPrecision precision = (LockStatisticsPrecision) pPrecision;
    LockStatisticsState successor;

    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        String fCallName = ((CFunctionCallEdge)cfaEdge).getSuccessor().getFunctionName();
        successor = handleFunctionCall(lockStatisticsElement, precision, state, (CFunctionCallEdge)cfaEdge);
        if (annotatedfunctions != null && annotatedfunctions.containsKey(fCallName)) {
          successor.setRestoreState(lockStatisticsElement);
        }
        break;

      case FunctionReturnEdge:
        successor = handleFunctionReturnEdge(lockStatisticsElement, (CFunctionReturnEdge)cfaEdge, precision, state);
        break;

      default:
        successor = handleSimpleEdge(lockStatisticsElement, precision, state, cfaEdge);
    }

    if (!successor.equals(lockStatisticsElement)) {
      return Collections.singleton(successor);
    } else {
      return null;
    }
  }

  private LockStatisticsState handleSimpleEdge(LockStatisticsState element, LockStatisticsPrecision precision,
      CallstackState state, CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    switch(cfaEdge.getEdgeType()) {
      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          return new LockStatisticsState();
        } else {
          return handleStatement(element, precision, state, statement, cfaEdge.getPredecessor().getFunctionName());
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
          tmpElement = handleSimpleEdge(tmpElement, precision, state, edge);
        }
        return tmpElement;
      default:
        throw new UnrecognizedCCodeException("Unknown edge type", cfaEdge);
    }
  }

  private LockStatisticsState handleFunctionReturnEdge(LockStatisticsState lockStatisticsElement, CFunctionReturnEdge cfaEdge,
      LockStatisticsPrecision lockStatisticsPrecision, CallstackState state) {
    CFANode tmpNode = cfaEdge.getSummaryEdge().getPredecessor();
    String fName = cfaEdge.getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
    LockStatisticsState successor = lockStatisticsElement.clone();
    if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
      AnnotationInfo currentAnnotation = annotatedfunctions.get(fName);
      if (currentAnnotation.restoreLocks.size() > 0) {
        successor = successor.restore(annotatedfunctions.get(fName).restoreLocks, logger);

        logger.log(Level.FINER, "Restore " + annotatedfunctions.get(fName).restoreLocks
            + ", \n\tline=" + tmpNode.getLineNumber());

      }
      if (currentAnnotation.freeLocks.size() > 0) {
        //Free in state at first restores saved state
        logger.log(Level.FINER, "Free " + annotatedfunctions.get(fName).freeLocks.keySet() + " in " + successor
                    + ", \n\t line = " + tmpNode.getLineNumber());
        ImmutableMap<String, String> freeLocks = annotatedfunctions.get(fName).freeLocks;
        successor = successor.restore(freeLocks, logger); //it also clones
        String variable;

        for (String lockName : freeLocks.keySet()) {
          variable = freeLocks.get(lockName);
          LockStatisticsLock lock = successor.findLock(lockName, variable);
          if (lock != null) {
            successor.free(lockName, variable, logger);
          }
        }
      }
      if (currentAnnotation.resetLocks.size() > 0) {
        //Reset in state at first restores saved state
        logger.log(Level.FINER, "Reset " + annotatedfunctions.get(fName).resetLocks.keySet() + " in " + successor
            + ", \n\t line = " + tmpNode.getLineNumber());
        ImmutableMap<String, String> resetLocks = annotatedfunctions.get(fName).resetLocks;
        successor = successor.restore(resetLocks, logger); //it also clones
        String variable;

        for (String lockName : resetLocks.keySet()) {
          variable = resetLocks.get(lockName);
          LockStatisticsLock lock = successor.findLock(lockName, variable);
          if (lock != null) {
            successor.reset(lockName, variable, logger);
          }
        }
      }
      if (currentAnnotation.captureLocks.size() > 0) {
        Map<String, String> locks = annotatedfunctions.get(fName).captureLocks;
        logger.log(Level.FINER, "Force lock of " + annotatedfunctions.get(fName).captureLocks.keySet() + " in " + successor
            + ", \n\t line = " + tmpNode.getLineNumber());
        successor = successor.restore(locks, logger);
        for (String name : locks.keySet()) {
          processLock(successor, lockStatisticsPrecision, state, cfaEdge.getLineNumber(), findLockByName(name), locks.get(name));
        }
      }
      logger.log(Level.FINEST, "\tPredessor = " + lockStatisticsElement
              + "\n\tSuccessor = " + successor);
    }
    return successor;
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
    return getAbstractSuccessors0(element, precision, state, cfaEdge);
  }

  private LockStatisticsState handleStatement(LockStatisticsState element, LockStatisticsPrecision precision, CallstackState state, CStatement expression,
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
          changeState(newElement, precision, state, lock, functionName, op2.getFileLocation().getStartingLineNumber(), currentFunction,
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
          LockInfo lock = findLockByVariable(leftSide.toASTString());
          if (lock != null) {
            processSetLevel(newElement, precision, state, expression.getFileLocation().getStartingLineNumber(), level, lock);
          }
        } else {
          logger.log(Level.WARNING, "Lock level isn't numeric constant: " + expression.toASTString()
              + "(line " + expression.getFileLocation().getStartingLineNumber() + ")");
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
        changeState(newElement, precision, state, lock, functionName, statement.getFileLocation().getStartingLineNumber(), currentFunction,
          statement.getFunctionCallExpression().getParameterExpressions());
      }
    }
    return newElement;
  }

  private LockStatisticsState handleFunctionCall(LockStatisticsState element, LockStatisticsPrecision precision, CallstackState state, CFunctionCallEdge callEdge) {
    Set<CFunctionCall> expressions = callEdge.getRawAST().asSet();
    LockStatisticsState newElement = element.clone();

    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        newElement = handleStatement(newElement, precision, state, statement, callEdge.getPredecessor().getFunctionName());
      }
    } else {
      String functionName = callEdge.getSuccessor().getFunctionName();
      Set<LockInfo> changedLocks = findLockByFunction(functionName);
      for (LockInfo lock : changedLocks) {
        changeState(newElement, precision, state, lock, functionName, callEdge.getLineNumber(),
            callEdge.getPredecessor().getFunctionName(), callEdge.getArguments());
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

  private LockInfo findLockByVariable(String varName) {
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

  private void changeState(LockStatisticsState newElement, LockStatisticsPrecision precision, CallstackState state, LockInfo lock, String functionName, int lineNumber
      , String currentFunction, List<CExpression> params) {
    if (lock.LockFunctions.containsKey(functionName)) {
      int p = lock.LockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      processLock(newElement, precision, state, lineNumber, lock, variable);
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
      processSetLevel(newElement, precision, state, lineNumber, p, lock);
      return;//they count from 1
    }
  }

  private void processLock(LockStatisticsState newElement, LockStatisticsPrecision precision, CallstackState reducedCallstack, int lineNumber, LockInfo lock, String variable) {
    CallstackState callstack = precision.getPreciseState();

    logger.log(Level.FINER, "Lock at line " + lineNumber + ", Callstack: " + callstack);
    int d = newElement.getCounter(lock.lockName, variable);

    if (d < lock.maxLock) {
      newElement.add(lock.lockName, lineNumber, callstack, reducedCallstack, variable, logger);
    } else {
      List<AccessPoint> access = newElement.findLock(lock.lockName, variable).getAccessPoints();
      StringBuilder message = new StringBuilder();
      message.append("Try to lock " + lock.lockName + " more, than " + lock.maxLock + " in " + lineNumber + " line. Previous were in ");
      for (AccessPoint point : access) {
        message.append(point.getLineInfo().getLine() + ", ");
      }
      message.delete(message.length() - 2, message.length());
      logger.log(Level.WARNING, message.toString());
    }
  }

  private void processSetLevel(LockStatisticsState newElement, LockStatisticsPrecision precision, CallstackState reducedCallstack, int lineNumber, int level, LockInfo lock) {
    CallstackState callstack = precision.getPreciseState();
    logger.log(Level.FINER, "Set a lock level " + level + " at line " + lineNumber + ", Callstack: " + callstack);
    newElement.set(lock.lockName, level, lineNumber, callstack, reducedCallstack, "");
  }
}
