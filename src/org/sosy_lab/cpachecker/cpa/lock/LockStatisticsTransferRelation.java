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
package org.sosy_lab.cpachecker.cpa.lock;

import static com.google.common.collect.FluentIterable.from;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
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
import org.sosy_lab.cpachecker.cpa.lock.LockStatisticsState.LockStatisticsStateBuilder;
import org.sosy_lab.cpachecker.cpa.lock.effects.AbstractLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.AcquireLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.CheckLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.LockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.ReleaseLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.ResetLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.RestoreAllLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.RestoreLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.SaveStateLockEffect;
import org.sosy_lab.cpachecker.cpa.lock.effects.SetLockEffect;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.Pair;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{

  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  final Map<String, AnnotationInfo> annotatedfunctions;

  final Set<LockInfo> lockDescription;
  private final LogManager logger;

  int i = 0;
  public LockStatisticsTransferRelation(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;

    ConfigurationParser parser = new ConfigurationParser(config);

    lockDescription = parser.parseLockInfo();
    annotatedfunctions = parser.parseAnnotatedFunctions();
  }

  @Override
  public Collection<LockStatisticsState> getAbstractSuccessorsForEdge(AbstractState element, Precision pPrecision
      , CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    LockStatisticsState lockStatisticsElement     = (LockStatisticsState)element;

    //Firstly, determine operations with locks
    List<AbstractLockEffect> toProcess = determineOperations(cfaEdge);

    final LockStatisticsStateBuilder builder = lockStatisticsElement.builder();

    for (AbstractLockEffect e : toProcess) {
      e.effect(builder);
    }

    LockStatisticsState successor = builder.build();

    if (successor != null) {
      return Collections.singleton(successor);
    } else {
      return Collections.emptySet();
    }
  }

  public Set<LockIdentifier> getAffectedLocks(CFAEdge cfaEdge) {
    try {
      return from(determineOperations(cfaEdge))
      .filter(new Predicate<AbstractLockEffect>() {
        @Override
        public boolean apply(@Nullable AbstractLockEffect pInput) {
          return pInput instanceof LockEffect;
        }
      })
      .transform(new Function<AbstractLockEffect, LockIdentifier>() {
        @Override
        @Nullable
        public LockIdentifier apply(@Nullable AbstractLockEffect pInput) {
          return ((LockEffect)pInput).getAffectedLock();
        }
      }).toSet();
    } catch (UnrecognizedCCodeException e) {
      e.printStackTrace();
      return Collections.emptySet();
    }
  }

  private List<AbstractLockEffect> determineOperations(CFAEdge cfaEdge) throws UnrecognizedCCodeException {
    List<AbstractLockEffect> toProcess;
    switch (cfaEdge.getEdgeType()) {

      case FunctionCallEdge:
        toProcess = handleFunctionCall((CFunctionCallEdge)cfaEdge);
        break;

      case FunctionReturnEdge:
        toProcess = handleFunctionReturnEdge((CFunctionReturnEdge)cfaEdge);
        break;

      default:
        toProcess = handleSimpleEdge(cfaEdge);
    }
    return toProcess;
  }

  private LockInfo findLockByName(String name) {
    for (LockInfo lock : lockDescription) {
      if (lock.lockName.equals(name)) {
        return lock;
      }
    }
    return null;
  }

  private List<AbstractLockEffect> handleSimpleEdge(CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    List<AbstractLockEffect> result;

    switch(cfaEdge.getEdgeType()) {
      case StatementEdge:
        CStatement statement = ((CStatementEdge)cfaEdge).getStatement();
        /*if (statement instanceof CFunctionCallStatement && lockreset != null &&
          ((CFunctionCallStatement)statement).getFunctionCallExpression().getFunctionNameExpression().toASTString().equals(lockreset)) {
          builder.resetAll();
        } else {*/
          return handleStatement(statement);
        //}
      case AssumeEdge:
        return handleAssumption((CAssumeEdge)cfaEdge);

      case BlankEdge:
      case ReturnStatementEdge:
      case DeclarationEdge:
      case CallToReturnEdge:
        break;

      case MultiEdge:
        result = new LinkedList<>();
        for (CFAEdge edge : (MultiEdge)cfaEdge) {
          result.addAll(handleSimpleEdge(edge));
        }
        return result;
      default:
        throw new UnrecognizedCCodeException("Unknown edge type", cfaEdge);
    }
    return Collections.emptyList();
  }

  private List<AbstractLockEffect> handleAssumption(CAssumeEdge cfaEdge) {
    CExpression assumption = cfaEdge.getExpression();

    if (assumption instanceof CBinaryExpression) {
      if (((CBinaryExpression) assumption).getOperand1() instanceof CIdExpression) {
        LockInfo lockInfo = findLockByVariable(((CIdExpression)((CBinaryExpression) assumption).getOperand1()).getName());
        if (lockInfo != null) {
          LockIdentifier id = LockIdentifier.of(lockInfo.lockName);
          if ((((CBinaryExpression) assumption).getOperand2() instanceof CIntegerLiteralExpression)) {
            int level = ((CIntegerLiteralExpression)(((CBinaryExpression) assumption).getOperand2())).getValue().intValue();
            AbstractLockEffect e = CheckLockEffect.createEffectForId(level, cfaEdge.getTruthAssumption(), id);
            return Collections.singletonList(e);
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private List<AbstractLockEffect> convertAnnotationToLockEffect(Map<String, String> map, LockEffect e) {
    List<AbstractLockEffect> result = new LinkedList<>();

    for (String lockName : map.keySet()) {
      result.add(e.cloneWithTarget(LockIdentifier.of(lockName, map.get(lockName))));
    }
    return result;
  }

  private List<AbstractLockEffect> handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge) {
    //CFANode tmpNode = cfaEdge.getSummaryEdge().getPredecessor();
    String fName = cfaEdge.getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
    if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
      List<AbstractLockEffect> result = new LinkedList<>();

      AnnotationInfo currentAnnotation = annotatedfunctions.get(fName);
      if (currentAnnotation.restoreLocks.size() == 0
          && currentAnnotation.freeLocks.size() == 0
          && currentAnnotation.resetLocks.size() == 0
          && currentAnnotation.captureLocks.size() == 0) {
        //Not specified annotations are considered to be totally restoring
        AbstractLockEffect restoreAll = RestoreAllLockEffect.getInstance();
        return Collections.singletonList(restoreAll);
      } else {
        if (currentAnnotation.restoreLocks.size() > 0) {
          result.addAll(convertAnnotationToLockEffect(currentAnnotation.restoreLocks, RestoreLockEffect.getInstance()));
        }
        if (currentAnnotation.freeLocks.size() > 0) {
          result.addAll(convertAnnotationToLockEffect(currentAnnotation.freeLocks, ReleaseLockEffect.getInstance()));
        }
        if (currentAnnotation.resetLocks.size() > 0) {
          result.addAll(convertAnnotationToLockEffect(currentAnnotation.resetLocks, ResetLockEffect.getInstance()));
        }
        if (currentAnnotation.captureLocks.size() > 0) {
          for (String lockName : currentAnnotation.captureLocks.keySet()) {
            LockIdentifier tagerId = LockIdentifier.of(lockName, currentAnnotation.captureLocks.get(lockName));
            LockInfo lock = findLockByName(tagerId.getName());
            result.add(AcquireLockEffect.createEffectForId(tagerId, lock.maxLock));
          }
        }
        return result;
      }
    }
    return Collections.emptyList();
  }


  private List<AbstractLockEffect> handleFunctionCallExpression(CFunctionCallExpression function) {
    String functionName = function.getFunctionNameExpression().toASTString();
    Pair<Set<LockInfo>, LockEffect> locksWithEffect = findLockByFunction(functionName);
    Set<LockInfo> changedLocks = locksWithEffect.getFirst();
    LockEffect e = locksWithEffect.getSecond();

    List<AbstractLockEffect> result = new LinkedList<>();
    int p;
    for (LockInfo lock : changedLocks) {
      if (e == AcquireLockEffect.getInstance()) {
        p = lock.LockFunctions.get(functionName);
      } else if (e == ReleaseLockEffect.getInstance()) {
        p = lock.UnlockFunctions.get(functionName);
      } else if (e == ResetLockEffect.getInstance()) {
        p = lock.ResetFunctions.get(functionName);
      } else {
        //Other ones can be only global
        p = 0;
        assert (e == SetLockEffect.getInstance());
        CExpression expression = function.getParameterExpressions().get(0);
        //Replace it by parametrical one
        if (expression instanceof CIntegerLiteralExpression) {
          int newValue = ((CIntegerLiteralExpression)expression).getValue().intValue();
          if (lock.maxLock < newValue) {
            newValue = lock.maxLock;
          }
          e = SetLockEffect.createEffectForId(newValue, LockIdentifier.of(lock.lockName));
        } else {
          //We can not process not integers
          continue;
        }
      }
      LockIdentifier id;
      if (p != 0) {
        CExpression expression = function.getParameterExpressions().get(p - 1);
        id = LockIdentifier.of(lock.lockName, expression.toASTString());
      } else {
        id = LockIdentifier.of(lock.lockName);
      }
      if (e == AcquireLockEffect.getInstance()) {
        e = AcquireLockEffect.createEffectForId(id, lock.maxLock);
      } else {
        e = e.cloneWithTarget(id);
      }
      result.add(e);
    }
    return result;
  }

  private List<AbstractLockEffect> handleStatement(CStatement statement) {

    if (statement instanceof CAssignment) {
      /*
       * level = intLock();
       */
      CRightHandSide op2 = ((CAssignment)statement).getRightHandSide();

      if (op2 instanceof CFunctionCallExpression) {
        CFunctionCallExpression function = (CFunctionCallExpression) op2;
        return handleFunctionCallExpression(function);
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
            AbstractLockEffect e = SetLockEffect.createEffectForId(level, LockIdentifier.of(lock.lockName));
            return Collections.singletonList(e);
          } else {
            return Collections.emptyList();
          }
        }
      }

    } else if (statement instanceof CFunctionCallStatement) {
      /*
       * queLock(que);
       */
      CFunctionCallStatement funcStatement = (CFunctionCallStatement) statement;
      return handleFunctionCallExpression(funcStatement.getFunctionCallExpression());
    }
    //No lock-relating operations
    return Collections.emptyList();
  }

  private List<AbstractLockEffect> handleFunctionCall(CFunctionCallEdge callEdge) {
    List<AbstractLockEffect> result = new LinkedList<>();
    if (annotatedfunctions != null && annotatedfunctions.containsKey(callEdge.getSuccessor().getFunctionName())) {
      AbstractLockEffect saveState = SaveStateLockEffect.getInstance();
      result.add(saveState);
    }
    result.addAll(handleFunctionCallExpression(callEdge.getSummaryEdge().getExpression().getFunctionCallExpression()));
    return result;
  }

  private Pair<Set<LockInfo>, LockEffect> findLockByFunction(String functionName) {
    /* Now it is supposed that one function has the same effects on different locks
     */
    Set<LockInfo> changedLocks = new HashSet<>();
    LockEffect e = null;
    for (LockInfo lock : lockDescription) {
      LockEffect tmp = null;
      if (lock.LockFunctions.containsKey(functionName)) {
        tmp = AcquireLockEffect.getInstance();
      } else if (lock.UnlockFunctions.containsKey(functionName)) {
        tmp = ReleaseLockEffect.getInstance();
      } else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
        tmp = ResetLockEffect.getInstance();
      } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
        tmp = SetLockEffect.getInstance();
      }
      if (tmp != null){
        assert (e == null || e == tmp);
        e = tmp;
        changedLocks.add(lock);
      }
    }
    return Pair.of(changedLocks, e);
  }

  private LockInfo findLockByVariable(String varName) {
    for (LockInfo lock : lockDescription) {
      for (String variable : lock.Variables) {
        if (variable.equals(varName)) {
          return lock;
        }
      }
    }
    return null;
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

  /**
   * Used in UsageStatisticsCPAStatistics
   * In true case the current line should be highlighted in the final report
   * @param pEdge edge to check
   * @return the verdict
   */
  public String doesChangeTheState(CFAEdge pEdge) {
    return formatCaption(getAffectedLocks(pEdge));
  }

  private String formatCaption(Set<LockIdentifier> set) {

    if (set.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Change states for locks ");
    for (LockIdentifier lInfo : set) {
      sb.append(lInfo + ", ");
    }
    sb.delete(sb.length() - 2, sb.length());
    return sb.toString();
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState pState, List<AbstractState> pOtherStates,
      @Nullable CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {
    return null;
  }
}
