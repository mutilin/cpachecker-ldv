/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonAction.CPAModification;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonExpression.ResultValue;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * A transition in the automaton implements one of the {@link PATTERN_MATCHING_METHODS}.
 * This determines if the transition matches on a certain {@link CFAEdge}.
 * @author rhein
 */
class AutomatonTransition {

  // The order of triggers, assertions and (more importantly) actions is preserved by the parser.
  private final AutomatonBoolExpr trigger;
  private final AutomatonBoolExpr assertion;
  private final List<AutomatonAction> actions;

  /**
   * When the parser instances this class it can not assign a followstate because
   * that state might not be created (forward-reference).
   * Only the name is known in the beginning and the followstate relation must be
   * resolved by calling setFollowState() when all States are known.
   */
  private final String followStateName;
  private AutomatonInternalState followState = null;

  public AutomatonTransition(AutomatonBoolExpr pTrigger, List<AutomatonBoolExpr> pAssertions, List<AutomatonAction> pActions,
      String pFollowStateName) {
    this(pTrigger, pAssertions, pActions, pFollowStateName, null);
  }

  public AutomatonTransition(AutomatonBoolExpr pTrigger,
      List<AutomatonBoolExpr> pAssertions, List<AutomatonAction> pActions,
      AutomatonInternalState pFollowState) {
    
    this(pTrigger, pAssertions, pActions, pFollowState.getName(), pFollowState);
  }
    
  private AutomatonTransition(AutomatonBoolExpr pTrigger,
      List<AutomatonBoolExpr> pAssertions, List<AutomatonAction> pActions,
      String pFollowStateName, AutomatonInternalState pFollowState) {
    
    this.trigger = checkNotNull(pTrigger);
    this.actions = ImmutableList.copyOf(pActions);
    this.followStateName = checkNotNull(pFollowStateName);
    this.followState = pFollowState;
    
    if (pAssertions.isEmpty()) {
      this.assertion = AutomatonBoolExpr.TRUE;
    } else {
      AutomatonBoolExpr lAssertion = null;
      for (AutomatonBoolExpr nextAssertion : pAssertions) {
        if (lAssertion == null) {
          // first iteration
          lAssertion = nextAssertion;
        } else {
          // other iterations
          lAssertion = new AutomatonBoolExpr.And(lAssertion, nextAssertion);
        }
      }
      this.assertion = lAssertion;
    }
  }

  /**
   * Resolves the follow-state relation for this transition.
   */
  void setFollowState(List<AutomatonInternalState> pAllStates) throws InvalidAutomatonException {
    if (this.followState == null) {
      for (AutomatonInternalState s : pAllStates) {
        if (s.getName().equals(followStateName)) {
          this.followState = s;
          return;
        }
      }
      throw new InvalidAutomatonException("No Follow-State with name " + followStateName + " found.");
    }
  }

  /** Writes a representation of this transition (as edge) in DOT file format to the argument {@link PrintStream}.
   */
  void writeTransitionToDotFile(int sourceStateId, PrintStream out) {
    out.println(sourceStateId + " -> " + followState.getStateId() + " [label=\"" /*+ pattern */ + "\"]");
  }

  /** Determines if this Transition matches on the current State of the CPA.
   * This might return a <code>MaybeBoolean.MAYBE</code> value if the method cannot determine if the transition matches.
   * In this case more information (e.g. more AbstractElements of other CPAs) are needed.
   */
  public ResultValue<Boolean> match(AutomatonExpressionArguments pArgs) {
    return trigger.eval(pArgs);
  }

  /**
   * Checks if all assertions of this transition are fulfilled
   * in the current configuration of the automaton this method is called.
   */
  public ResultValue<Boolean> assertionsHold(AutomatonExpressionArguments pArgs) {
    return assertion.eval(pArgs);
  }

  /**
   * Executes all actions of this transition in the order which is defined in the automaton definition file.
   */
  public void executeActions(AutomatonExpressionArguments pArgs) {
    for (AutomatonAction action : actions) {
      ResultValue<? extends Object> res = action.eval(pArgs);
      if (res.canNotEvaluate()) {
        pArgs.getLogger().log(Level.SEVERE, res.getFailureMessage() + " in " + res.getFailureOrigin());
      }
    }
    if (pArgs.getLogMessage() != null && pArgs.getLogMessage().length() > 0) {
      pArgs.getLogger().log(Level.INFO, pArgs.getLogMessage());
      pArgs.clearLogMessage();
    }
  }
  
  /** Returns if the actions of this transiton can be executed on these AutomatonExpressionArguments.
   * If false is returned more Information is needed (probably more AbstractElements from other CPAs).
   * @param pArgs
   * @return
   */
  public boolean canExecuteActionsOn(AutomatonExpressionArguments pArgs) {
    for (AutomatonAction action : actions) {
      if (! action.canExecuteOn(pArgs)) {
        return false;
      }
    }
    return true;
  }

  /**
   * returns null if setFollowState() was not called or no followState with appropriate name was found.
   */
  public AutomatonInternalState getFollowState() {
    return followState;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('"');
    sb.append(trigger);
    sb.append(" -> ");
    if (!assertion.equals(AutomatonBoolExpr.TRUE)) {
      sb.append("ASSERT ");
      sb.append(assertion);
    }
    if (!actions.isEmpty()) {
      Joiner.on(' ').appendTo(sb, actions);
      sb.append(" ");
    }
    sb.append(followState);
    sb.append(";\"");
    return sb.toString();
  }

  /**
   * Returns true if this Transition fulfills the requirements of an ObserverTransition (does not use MODIFY or STOP).
   * @return
   */
  boolean meetsObserverRequirements() {
    // assert followstate != BOTTOM
    if (this.followState.equals(AutomatonInternalState.BOTTOM)) {
      return false;
    }
    // actions are not MODIFY actions
    for (AutomatonAction action : this.actions) {
      if ((action instanceof CPAModification)) {
        return false;
      }
    }
    return true;
  }
}
