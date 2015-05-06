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

import static com.google.common.collect.FluentIterable.from;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
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
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier.LockType;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

@Options(prefix="cpa.lockStatistics")
public class LockStatisticsTransferRelation implements TransferRelation
{

  private static class Effect {
    /*Method change;

    Effect(Method c) {
      change = c;
    }

    public void execute(LockStatisticsStateBuilder builder, LockIdentifier id) {
      change.invoke(builder, id);
    }*/
  }
  private static class SETEffect extends Effect {
    final int p;

    public SETEffect(int t) {
     // super(LockStatisticsStateBuilder.class.getDeclaredMethod("set", {LockIdentifier.class, Integer.class}));
      p = t;
    }

   /* @Override
    public void execute(LockStatisticsStateBuilder builder, LockIdentifier id) {
      change.invoke(builder, id, p);
    }*/
  }

  private static class CHECKEffect extends SETEffect {
    final boolean isTruth;

    public CHECKEffect(int t, boolean truth) {
      super(t);
      isTruth = truth;
    }
  }

  //There ones have no parameters
  private final static Effect CAPTURE = new Effect();
  private final static Effect RELEASE = new Effect();
  private final static Effect RESET = new Effect();
  private final static Effect SAVESTATE = new Effect();
  private final static Effect RESTORE = new Effect();
  private final static Effect RESTOREALL = new Effect();

  //This one is without parameter and will be replaced by ParameterEffect after processing
  private final static Effect SET = new Effect();

  @Option(name="lockreset",
      description="function to reset state")
  private String lockreset;

  final Map<String, AnnotationInfo> annotatedfunctions;

  final Set<LockInfo> lockDescription;
  private LogManager logger;

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

    if (cfaEdge.getPredecessor().getFunctionName().equals("cpuclockStop")) {
      System.out.println("This");
    }
    //Firstly, determine operations with locks
    List<Pair<LockIdentifier, Effect>> toProcess = determineOperations(cfaEdge);

    LockStatisticsState successor = handleOperations(toProcess, lockStatisticsElement);

    if (successor != null) {
      return Collections.singleton(successor);
    } else {
      return Collections.emptySet();
    }
  }

  private List<Pair<LockIdentifier, Effect>> determineOperations(CFAEdge cfaEdge) throws UnrecognizedCCodeException {
    List<Pair<LockIdentifier, Effect>> toProcess;
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

  private LockStatisticsState handleOperations(List<Pair<LockIdentifier, Effect>> toProcess, LockStatisticsState oldState) {
    LockStatisticsStateBuilder builder = oldState.builder();

    for (Pair<LockIdentifier, Effect> pair : toProcess) {
      LockIdentifier id = pair.getFirst();
      Effect e = pair.getSecond();
      if (e == RESTOREALL) {
        builder.resetAll();
      } else if (e == SAVESTATE) {
        builder.setRestoreState(oldState);
      } else {
        assert (id != null);
        if (e == CAPTURE) {
          LockInfo lock = findLockByName(id.getName());
          int currentLevel = oldState.getCounter(id);
          if (lock.maxLock > currentLevel) {
            builder.add(id);
          }
        } else if (e == RELEASE) {
          builder.free(id);
        } else if (e == RESET) {
          builder.reset(id);
        } else if (e == RESTORE) {
          builder.restore(id);
        } else if (e.getClass() == SETEffect.class) {
          LockInfo lock = findLockByName(id.getName());
          int currentLevel = ((SETEffect)e).p;
          if (lock.maxLock >= currentLevel) {
            builder.set(id, currentLevel);
          } else {
            builder.set(id, lock.maxLock);
          }
        } else if (e.getClass() == CHECKEffect.class) {
          CHECKEffect check = (CHECKEffect)e;
          boolean result;
          int previousP = oldState.getCounter(id);
          result = ((previousP == check.p) == check.isTruth);
          if (!result) {
            return null;
          }
        }
      }
    }
    return builder.build();
  }

  private List<Pair<LockIdentifier, Effect>> handleSimpleEdge(CFAEdge cfaEdge) throws UnrecognizedCCodeException {

    List<Pair<LockIdentifier, Effect>> result;

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

  private List<Pair<LockIdentifier, Effect>> handleAssumption(CAssumeEdge cfaEdge) {
    CExpression assumption = cfaEdge.getExpression();

    if (assumption instanceof CBinaryExpression) {
      if (((CBinaryExpression) assumption).getOperand1() instanceof CIdExpression) {
        LockInfo lockInfo = findLockByVariable(((CIdExpression)((CBinaryExpression) assumption).getOperand1()).getName());
        if (lockInfo != null) {
          LockIdentifier id = LockIdentifier.of(lockInfo.lockName);
          if (!(((CBinaryExpression) assumption).getOperand2() instanceof CIntegerLiteralExpression)) {
            return Collections.emptyList();
          }
          int level = ((CIntegerLiteralExpression)(((CBinaryExpression) assumption).getOperand2())).getValue().intValue();
          Effect e = new CHECKEffect(level, cfaEdge.getTruthAssumption());
          return Collections.singletonList(Pair.of(id, e));
        }
      }
    }
    return Collections.emptyList();
  }

  private List<Pair<LockIdentifier, Effect>> convertAnnotationToEffect(Map<String, String> map, Effect e) {
    List<Pair<LockIdentifier, Effect>> result = new LinkedList<>();

    for (String lockName : map.keySet()) {
      result.add(Pair.of(LockIdentifier.of(lockName, map.get(lockName), LockType.GLOBAL_LOCK), e));
    }
    return result;
  }

  private List<Pair<LockIdentifier, Effect>> handleFunctionReturnEdge(CFunctionReturnEdge cfaEdge) {
    //CFANode tmpNode = cfaEdge.getSummaryEdge().getPredecessor();
    String fName = cfaEdge.getSummaryEdge().getExpression().getFunctionCallExpression().getFunctionNameExpression().toASTString();
    if (annotatedfunctions != null && annotatedfunctions.containsKey(fName)) {
      List<Pair<LockIdentifier, Effect>> result = new LinkedList<>();

      AnnotationInfo currentAnnotation = annotatedfunctions.get(fName);
      if (currentAnnotation.restoreLocks.size() == 0
          && currentAnnotation.freeLocks.size() == 0
          && currentAnnotation.resetLocks.size() == 0
          && currentAnnotation.captureLocks.size() == 0) {
        //Not specified annotations are considered to be totally restoring
        return Collections.singletonList(Pair.of((LockIdentifier)null, RESTOREALL));
      } else {
        if (currentAnnotation.restoreLocks.size() > 0) {
          result.addAll(convertAnnotationToEffect(currentAnnotation.restoreLocks, RESTORE));
          logger.log(Level.FINER, "Restore " + currentAnnotation.restoreLocks
              + ", \n\tline=" + cfaEdge.getLineNumber());

        }
        if (currentAnnotation.freeLocks.size() > 0) {
          //Free in state at first restores saved state
          result.addAll(convertAnnotationToEffect(currentAnnotation.freeLocks, RELEASE));
        }
        if (currentAnnotation.resetLocks.size() > 0) {
          //Reset in state at first restores saved state
          result.addAll(convertAnnotationToEffect(currentAnnotation.resetLocks, RESET));
        }
        if (currentAnnotation.captureLocks.size() > 0) {
          result.addAll(convertAnnotationToEffect(currentAnnotation.captureLocks, CAPTURE));
        }
        return result;
      }
    }
    return Collections.emptyList();
  }


  private List<Pair<LockIdentifier, Effect>> handleFunctionCallExpression(CFunctionCallExpression function) {
    String functionName = function.getFunctionNameExpression().toASTString();
    Pair<Set<LockInfo>, Effect> locksWithEffect = findLockByFunction(functionName);
    Set<LockInfo> changedLocks = locksWithEffect.getFirst();
    Effect e = locksWithEffect.getSecond();

    List<Pair<LockIdentifier, Effect>> result = new LinkedList<>();
    int p;
    for (LockInfo lock : changedLocks) {
      if (e == CAPTURE) {
        p = lock.LockFunctions.get(functionName);
      } else if (e == RELEASE) {
        p = lock.UnlockFunctions.get(functionName);
      } else if (e == RESET) {
        p = lock.ResetFunctions.get(functionName);
      } else {
        //Other ones can be only global
        p = 0;
        assert (e == SET);
        CExpression expression = function.getParameterExpressions().get(0);
        //Replace it by parametrical one
        if (expression instanceof CIntegerLiteralExpression) {
          e = new SETEffect(((CIntegerLiteralExpression)expression).getValue().intValue());
        } else {
          //We can not process not integers
          return Collections.emptyList();
        }
      }
      LockIdentifier id;
      if (p != 0) {
        CExpression expression = function.getParameterExpressions().get(p - 1);
        id = LockIdentifier.of(lock.lockName, expression.toASTString());
      } else {
        id = LockIdentifier.of(lock.lockName);
      }
      result.add(Pair.of(id, e));
    }
    return result;
  }

  private List<Pair<LockIdentifier, Effect>> handleStatement(CStatement statement) {

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
            Effect e = new SETEffect(level);
            return Collections.singletonList(Pair.of(LockIdentifier.of(lock.lockName, "", LockType.GLOBAL_LOCK), e));
          } else {
            logger.log(Level.WARNING, "Lock level isn't numeric constant: " + statement.toASTString()
                + "(line " + statement.getFileLocation().getStartingLineNumber() + ")");
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

  private List<Pair<LockIdentifier, Effect>> handleFunctionCall(CFunctionCallEdge callEdge) {
    List<Pair<LockIdentifier, Effect>> result = new LinkedList<>();
    if (annotatedfunctions != null && annotatedfunctions.containsKey(callEdge.getSuccessor().getFunctionName())) {
      result.add(Pair.of((LockIdentifier)null, SAVESTATE));
    }
    result.addAll(handleFunctionCallExpression(callEdge.getSummaryEdge().getExpression().getFunctionCallExpression()));
    return result;
  }

  private Pair<Set<LockInfo>, Effect> findLockByFunction(String functionName) {
    /* Now it is supposed that one function has the same effects on different locks
     */
    Set<LockInfo> changedLocks = new HashSet<>();
    Effect e = null;
    for (LockInfo lock : lockDescription) {
      Effect tmp = null;
      if (lock.LockFunctions.containsKey(functionName)) {
        tmp = CAPTURE;
      } else if (lock.UnlockFunctions.containsKey(functionName)) {
        tmp = RELEASE;
      } else if (lock.ResetFunctions != null && lock.ResetFunctions.containsKey(functionName)) {
        tmp = RESET;
      } else if (lock.setLevel != null && lock.setLevel.equals(functionName)) {
        tmp = SET;
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
  public String changeTheState(CFAEdge pEdge) {
    try {
      Set<LockIdentifier> locks = from(determineOperations(pEdge))
          .filter(new Predicate<Pair<LockIdentifier, Effect>>() {
            @Override
            public boolean apply(@Nullable Pair<LockIdentifier, Effect> pInput) {
              return pInput.getFirst() != null;
            }
          })
          .transform(new Function<Pair<LockIdentifier, Effect>, LockIdentifier>() {
            @Override
            @Nullable
            public LockIdentifier apply(@Nullable Pair<LockIdentifier, Effect> pInput) {
              return pInput.getFirst();
            }
          }).toSet();
      return formatCaption(locks);
    } catch (UnrecognizedCCodeException e) {
      e.printStackTrace();
      return null;
    }
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
