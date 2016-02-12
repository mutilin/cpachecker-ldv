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

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

/**
 * Represents one abstract state of the UsageStatistics CPA.
 */
public class UsageStatisticsState extends AbstractSingleWrapperState implements Targetable {
  /* Boilerplate code to avoid serializing this class */

  private static final long serialVersionUID = -898577877284268426L;
  private TemporaryUsageStorage recentUsages;
  private boolean isStorageCloned;
  private final UsageContainer globalContainer;
  private final TemporaryUsageStorage functionContainer;

  private final Map<AbstractIdentifier, AbstractIdentifier> variableBindingRelation;

  public UsageStatisticsState(final AbstractState pWrappedElement, final UsageContainer pContainer) {
    //Only for getInitialState() and reduce
    super(pWrappedElement);
    variableBindingRelation = new HashMap<>();
    recentUsages = new TemporaryUsageStorage();
    globalContainer = pContainer;
    isStorageCloned = true;
    functionContainer = new TemporaryUsageStorage();
  }

  private UsageStatisticsState(final AbstractState pWrappedElement, final UsageStatisticsState state) {
    super(pWrappedElement);
    variableBindingRelation = new HashMap<>(state.variableBindingRelation);
    recentUsages = state.recentUsages;
    globalContainer = state.globalContainer;
    isStorageCloned = false;
    functionContainer = state.functionContainer;
  }

  public boolean containsLinks(final AbstractIdentifier id) {
    /* Special contains!
    *  if we have *b, map also contains **b, ***b and so on.
    *  So, if we get **b, having (*b, c), we give *c
    */
    final AbstractIdentifier tmpId = id.clone();
    for (int d = id.getDereference(); d >= 0; d--) {
      tmpId.setDereference(d);
      if (variableBindingRelation.containsKey(tmpId)) {
        return true;
      }
    }
    return false;
  }

  public void put(final AbstractIdentifier id1, final AbstractIdentifier id2) {
    if (!id1.equals(id2)) {
      variableBindingRelation.put(id1, id2);
    }
  }

  public boolean containsUsage(final SingleIdentifier id) {
    return recentUsages.containsKey(id);
  }

  public AbstractIdentifier getLinks(final AbstractIdentifier id) {
    /* Special get!
     * If we get **b, having (*b, c), we give *c
     */
    AbstractIdentifier tmpId = id.clone();
    for (int d = id.getDereference(); d >= 0; d--) {
      tmpId.setDereference(d);
      if (variableBindingRelation.containsKey(tmpId)) {
        tmpId = variableBindingRelation.get(tmpId).clone();
        int currentD = tmpId.getDereference();
        tmpId.setDereference(currentD + id.getDereference() - d);
        if (this.containsLinks(tmpId)) {
          tmpId = getLinks(tmpId);
        }
        return tmpId;
      }
    }
    return null;
  }

  @Override
  public UsageStatisticsState clone() {
    return clone(this.getWrappedState());
  }

  public UsageStatisticsState clone(final AbstractState pWrappedState) {
    return new UsageStatisticsState(pWrappedState, this);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((variableBindingRelation == null) ? 0 : variableBindingRelation.hashCode());
    result = prime * super.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UsageStatisticsState other = (UsageStatisticsState) obj;
    if (variableBindingRelation == null) {
      if (other.variableBindingRelation != null) {
        return false;
      }
    } else if (!variableBindingRelation.equals(other.variableBindingRelation)) {
      return false;
    }
    return super.equals(other);
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("[");
    for (AbstractIdentifier id : variableBindingRelation.keySet()) {
      str.append(id.toString());
      str.append("->");
      str.append(variableBindingRelation.get(id).toString());
      str.append(", ");
    }
    str.append("]\n");
    str.append(getWrappedState());
    return str.toString();
  }

  boolean isLessOrEqual(final UsageStatisticsState other) {
    //If we are here, the wrapped domain return true and the stop depends only on this value

    // this element is not less or equal than the other element, if that one contains less elements
    if (this.variableBindingRelation.size() > other.variableBindingRelation.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (AbstractIdentifier id : variableBindingRelation.keySet()) {
      if (!other.variableBindingRelation.containsKey(id)) {
        return false;
      }
    }

    //If we want to stop, we need to clone usages from the covered state (this - is new, other - is old)
    other.recentUsages.join(this.recentUsages);
    return true;
  }

  public void addUsage(final SingleIdentifier id, final UsageInfo usage) {
    //Clone it
    if (!isStorageCloned) {
      recentUsages = new TemporaryUsageStorage(recentUsages);
      isStorageCloned = true;
    }
    recentUsages.add(id, usage);
  }

  public UsageStatisticsState expand(final UsageStatisticsState root, final AbstractState wrappedState) {
    final UsageStatisticsState result = root.clone(wrappedState);
    //Now it is only join
    result.functionContainer.join(this.functionContainer);
    return result;
  }

  public UsageStatisticsState reduce(final AbstractState wrappedState) {
    UsageStatisticsState result = new UsageStatisticsState(wrappedState, this.globalContainer);
    return result;
  }

  public UsageContainer getContainer() {
    return globalContainer;
  }

  public void saveUnsafesInContainerIfNecessary(AbstractState abstractState) {
    ARGState argState = AbstractStates.extractStateByType(abstractState, ARGState.class);
    PredicateAbstractState state = AbstractStates.extractStateByType(argState, PredicateAbstractState.class);
    if (state == null || !state.getAbstractionFormula().isFalse() && state.isAbstractionState()) {
      recentUsages.setKeyState(argState);
      functionContainer.join(recentUsages);
      recentUsages.clear();
    }
  }

  public void updateContainerIfNecessary() {
    globalContainer.addNewUsagesIfNecessary(functionContainer);
  }
}
