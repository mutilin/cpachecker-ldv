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

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.MultiEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

import com.google.common.collect.ImmutableMap;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{
  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  final Map<String, AnnotationInfo> annotatedfunctions;

  final Set<LockInfo> locks;
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
  public Collection<LockStatisticsState> getAbstractSuccessorsForEdge(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge) {

    return Collections.singleton(((LockStatisticsState)element).clone());
  }

  public Collection<LockStatisticsState> getAbstractSuccessors0(AbstractState element, CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;
    LockStatisticsState successor;
    LockStatisticsStateBuilder builder = lockStatisticsElement.builder();
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        handleFunctionCall(lockStatisticsElement, builder,(CFunctionCallEdge)cfaEdge);
        break;

      case FunctionReturnEdge:
        handleFunctionReturnEdge(lockStatisticsElement, builder, (CFunctionReturnEdge)cfaEdge);
        break;

      default:
        boolean result = handleSimpleEdge(lockStatisticsElement, builder, cfaEdge);
        if (!result) {
          //False assumption edge
          return Collections.emptySet();
        }
    }

    successor = builder.build();

    if (!successor.equals(lockStatisticsElement)) {
      return Collections.singleton(successor);
    } else {
      return null;
    }
  }

  private boolean handleSimpleEdge(LockStatisticsState oldElement, LockStatisticsStateBuilder builder, CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    switch(cfaEdge.getEdgeType()) {
      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          builder.resetAll();
        } else {
          handleStatement(oldElement, builder, statement, new LineInfo(cfaEdge), cfaEdge.getPredecessor().getFunctionName());
        }
        break;
      case AssumeEdge:
        return handleAssumption(oldElement, (CAssumeEdge)cfaEdge);

      case BlankEdge:
      case ReturnStatementEdge:
      case DeclarationEdge:
      case CallToReturnEdge:
        break;

      case MultiEdge:
        LockStatisticsState tmpElement = oldElement;

        for (CFAEdge edge : (MultiEdge)cfaEdge) {
          boolean result = handleSimpleEdge(tmpElement, builder, edge);
          if (!result) {
            return result;
          }
          tmpElement = builder.build();
        }
        break;
      default:
        throw new UnrecognizedCCodeException("Unknown edge type", cfaEdge);
    }
    return true;
  }

  private boolean handleAssumption(LockStatisticsState oldElement, CAssumeEdge cfaEdge) {
    CExpression assumption = cfaEdge.getExpression();

    if (assumption instanceof CBinaryExpression) {
      if (((CBinaryExpression) assumption).getOperand1() instanceof CIdExpression) {
        LockInfo lockInfo = findLockByVariable(((CIdExpression)((CBinaryExpression) assumption).getOperand1()).getName());
        if (lockInfo != null) {
          int currentLevel = oldElement.getCounter(lockInfo.lockName, "");
          if (!(((CBinaryExpression) assumption).getOperand2() instanceof CIntegerLiteralExpression)) {
            return true;
          }
          int level = ((CIntegerLiteralExpression)(((CBinaryExpression) assumption).getOperand2())).getValue().intValue();
          if (currentLevel == level) {
            if (cfaEdge.getTruthAssumption()) {
              return true;
            } else {
              return false;
            }
          } else {
            return false;
          }
        }
      }
    }
    return true;
  }

  private void handleFunctionReturnEdge(LockStatisticsState oldState, LockStatisticsStateBuilder builder, CFunctionReturnEdge cfaEdge) {
    //CFANode tmpNode = cfaEdge.getSummaryEdge().getPredecessor();
    String fName = cfaEdge.getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();

    if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
      AnnotationInfo currentAnnotation = annotatedfunctions.get(fName);
      if (currentAnnotation.restoreLocks.size() > 0) {
        builder.restore(annotatedfunctions.get(fName).restoreLocks, logger);

        logger.log(Level.FINER, "Restore " + annotatedfunctions.get(fName).restoreLocks
            + ", \n\tline=" + cfaEdge.getLineNumber());

      }
      if (currentAnnotation.freeLocks.size() > 0) {
        //Free in state at first restores saved state
        logger.log(Level.FINER, "Free " + annotatedfunctions.get(fName).freeLocks.keySet() + " in " + oldState
                    + ", \n\t line = " + cfaEdge.getLineNumber());
        ImmutableMap<String, String> freeLocks = annotatedfunctions.get(fName).freeLocks;
        builder.restore(freeLocks, logger);
        String variable;

        for (String lockName : freeLocks.keySet()) {
          variable = freeLocks.get(lockName);
          builder.free(lockName, variable, logger);
        }
      }
      if (currentAnnotation.resetLocks.size() > 0) {
        //Reset in state at first restores saved state
        logger.log(Level.FINER, "Reset " + annotatedfunctions.get(fName).resetLocks.keySet() + " in " + oldState
            + ", \n\t line = " + cfaEdge.getLineNumber());
        ImmutableMap<String, String> resetLocks = annotatedfunctions.get(fName).resetLocks;
        builder.restore(resetLocks, logger);
        String variable;

        for (String lockName : resetLocks.keySet()) {
          variable = resetLocks.get(lockName);
          builder.reset(lockName, variable, logger);
        }
      }
      if (currentAnnotation.captureLocks.size() > 0) {
        Map<String, String> locks = annotatedfunctions.get(fName).captureLocks;
        logger.log(Level.FINER, "Force lock of " + annotatedfunctions.get(fName).captureLocks.keySet() + " in " + oldState
            + ", \n\t line = " + cfaEdge.getLineNumber());
        builder.restore(locks, logger);
        for (String name : locks.keySet()) {
          processLock(builder.build(), builder, new LineInfo(cfaEdge), findLockByName(name), locks.get(name));
        }
      }
    }
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException {

    logger.log(Level.FINEST, "Strengthen LockStatistics result");
    return getAbstractSuccessors0(element, cfaEdge);
  }

  private void handleStatement(LockStatisticsState oldState, LockStatisticsStateBuilder builder,
      CStatement statement, LineInfo line, String currentFunction) {

    if (statement instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)statement).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        CFunctionCallExpression function = (CFunctionCallExpression) op2;
        String functionName = function.getFunctionNameExpression().toASTString();
        Set<LockInfo> changedLocks = findLockByFunction(functionName);
        for (LockInfo lock : changedLocks) {
          changeState(oldState, builder, lock, functionName, line,
              currentFunction, function.getParameterExpressions());
        }
      } else {
        /*
         * threadDispatchLevel = 1;
         */
        CLeftHandSide leftSide = ((CAssignment) statement).getLeftHandSide();
        CRightHandSide rightSide = ((CAssignment) statement).getRightHandSide();
        LockInfo lock = findLockByVariable(leftSide.toASTString());
        if (lock != null) {
          if (rightSide instanceof CIntegerLiteralExpression) {
            int level = ((CIntegerLiteralExpression)rightSide).getValue().intValue();
            processSetLevel(builder, line, level, lock);
          } else {
            logger.log(Level.WARNING, "Lock level isn't numeric constant: " + statement.toASTString()
                + "(line " + statement.getFileLocation().getStartingLineNumber() + ")");
          }
        }
      }

    } else if (statement instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      CFunctionCallStatement funcStatement = (CFunctionCallStatement) statement;
      String functionName = funcStatement.getFunctionCallExpression().getFunctionNameExpression().toASTString();
      Set<LockInfo> changedLocks = findLockByFunction(functionName);
      for (LockInfo lock : changedLocks) {
        changeState(oldState, builder, lock, functionName, line, currentFunction,
            funcStatement.getFunctionCallExpression().getParameterExpressions());
      }
    }
  }

  private void handleFunctionCall(LockStatisticsState oldState, LockStatisticsStateBuilder builder, CFunctionCallEdge callEdge) {
    Set<CFunctionCall> expressions = callEdge.getRawAST().asSet();
    if (expressions.size() > 0) {
      for (CStatement statement : expressions) {
        handleStatement(oldState, builder, statement, new LineInfo(callEdge), callEdge.getPredecessor().getFunctionName());
      }
    } else {
      String functionName = callEdge.getSuccessor().getFunctionName();
      Set<LockInfo> changedLocks = findLockByFunction(functionName);
      for (LockInfo lock : changedLocks) {
        changeState(oldState, builder, lock, functionName, new LineInfo(callEdge),
            callEdge.getPredecessor().getFunctionName(), callEdge.getArguments());
      }
    }
    if (annotatedfunctions != null && annotatedfunctions.containsKey(callEdge.getSuccessor().getFunctionName())) {
      builder.setRestoreState(oldState);
    }
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

  private void changeState(LockStatisticsState oldElement, LockStatisticsStateBuilder builder, LockInfo lock, String functionName, LineInfo line
      , String currentFunction, List<CExpression> params) {
    if (lock.LockFunctions.containsKey(functionName)) {
      int p = lock.LockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      processLock(oldElement, builder, line, lock, variable);

    } else if (lock.UnlockFunctions.containsKey(functionName)) {
      logger.log(Level.FINER, "Unlock at line " + line + "int function " + currentFunction);
      int p = lock.UnlockFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      builder.free(lock.lockName, variable, logger);

    } else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
      logger.log(Level.FINER, "Reset at line " + line + "int function " + currentFunction);
      int p = lock.ResetFunctions.get(functionName);
      String variable = (p == 0 ? "" : params.get(p - 1).toASTString());
      builder.reset(lock.lockName, variable, logger);

    } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
      int p = Integer.parseInt(params.get(0).toASTString()); //new level
      processSetLevel(builder, line, p, lock);//they count from 1
    }
  }

  private void processLock(LockStatisticsState oldElement, LockStatisticsStateBuilder builder,
      LineInfo line, LockInfo lock, String variable) {
    logger.log(Level.FINER, "Lock at line " + line);
    int d = oldElement.getCounter(lock.lockName, variable);

    if (d < lock.maxLock) {
      builder.add(lock.lockName, line, variable, logger);
    } else {
      /*UnmodifiableIterator<AccessPoint> accessPointIterator =
          oldElement.getAccessPointIterator(lock.lockName, variable);
      StringBuilder message = new StringBuilder();
      message.append("Try to lock " + lock.lockName + " more, than " + lock.maxLock + " in " + line + " line. Previous were in ");
      while (accessPointIterator.hasNext()) {
        message.append(accessPointIterator.next().getLineInfo().getLine() + ", ");
      }
      message.delete(message.length() - 2, message.length());
      logger.log(Level.WARNING, message.toString());*/
    }
  }

  private void processSetLevel(LockStatisticsStateBuilder builder, LineInfo line, int level, LockInfo lock) {
    logger.log(Level.FINER, "Set a lock level " + level + " at line " + line);
    builder.set(lock.lockName, level, line, "");
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(AbstractState pState, Precision pPrecision)
      throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException(
        "The " + this.getClass().getSimpleName()
        + " expects to be called with a CFA edge supplied"
        + " and does not support configuration where it needs to"
        + " return abstract states for any CFA edge.");
  }
}
