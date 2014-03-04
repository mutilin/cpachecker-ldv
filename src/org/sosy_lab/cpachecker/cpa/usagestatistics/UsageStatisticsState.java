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
import java.util.LinkedList;
import java.util.Map;

import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

/**
 * Represents one abstract state of the UsageStatistics CPA.
 */
public class UsageStatisticsState extends AbstractSingleWrapperState  {
  /* Boilerplate code to avoid serializing this class */

  private static final long serialVersionUID = -898577877284268426L;
  private final UsageContainer container;
  private Map <SingleIdentifier, LinkedList<UsageInfo>> recentUsages;

  private final Map<AbstractIdentifier, AbstractIdentifier> variableBindingRelation;

  public UsageStatisticsState(AbstractState pWrappedElement, UsageContainer pContainer) {
    //Only for getInitialState()
    super(pWrappedElement);
    variableBindingRelation = new HashMap<>();
    recentUsages = new HashMap<>();
    container = pContainer;
  }

  private UsageStatisticsState(AbstractState pWrappedElement, UsageStatisticsState state) {
    super(pWrappedElement);
    variableBindingRelation = new HashMap<>(state.variableBindingRelation);
    recentUsages = new HashMap<>(state.recentUsages);

    for (SingleIdentifier id : state.recentUsages.keySet()) {
      recentUsages.put(id, new LinkedList<>(state.recentUsages.get(id)));
    }
    container = state.container;
  }

  public boolean containsLinks(AbstractIdentifier id) {
    /* Special contains!
    *  if we have *b, map also contains **b, ***b and so on.
    *  So, if we get **b, having (*b, c), we give *c
    */
    AbstractIdentifier tmpId = id.clone();
    for (int d = id.getDereference(); d >= 0; d--) {
      tmpId.setDereference(d);
      if (variableBindingRelation.containsKey(tmpId)) {
        return true;
      }
    }
    return false;
  }

  public void put(AbstractIdentifier id1, AbstractIdentifier id2) {
    if (!id1.equals(id2)) {
      variableBindingRelation.put(id1, id2);
    }
  }

  public boolean containsUsage(SingleIdentifier id) {
    return recentUsages.containsKey(id);
  }

  public void removeUsage(SingleIdentifier id) {
    recentUsages.remove(id);
  }

  public AbstractIdentifier getLinks(AbstractIdentifier id) {
    /* Special get!
     * If we get **b, having (*b, c), we give *c
     */
    AbstractIdentifier tmpId = id.clone();
    for (int d = id.getDereference(); d >= 0; d--) {
      tmpId.setDereference(d);
      if (variableBindingRelation.containsKey(tmpId)) {
        tmpId = variableBindingRelation.get(tmpId);
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
    return new UsageStatisticsState(this.getWrappedState(), this);
  }

  public UsageStatisticsState clone(AbstractState pWrappedState) {
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

  boolean isLessOrEqual(UsageStatisticsState other) {

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

    return true;
  }

  public void addUsage(SingleIdentifier id, UsageInfo usage) {
    LinkedList<UsageInfo> uset;
    if (!recentUsages.containsKey(id)) {
      uset = new LinkedList<>();
      recentUsages.put(id, uset);
    } else {
      uset = recentUsages.get(id);
    }
    uset.add(usage);

  }

  public void clearUsagesIfNeed() {
    PredicateAbstractState state = AbstractStates.extractStateByType(this, PredicateAbstractState.class);
    if (state == null || !state.getAbstractionFormula().isFalse() && state.isAbstractionState()) {
      recentUsages.clear();
    }
  }

  @Override
  public boolean isTarget() {
    boolean result = container.isTarget();
    if (getWrappedState() instanceof Targetable) {
      result = result || ((Targetable)getWrappedState()).isTarget();
    }
    return result;
  }

  @Override
  public ViolatedProperty getViolatedProperty() throws IllegalStateException {
    return ViolatedProperty.OTHER;
  }

  public UsageStatisticsState expand(UsageStatisticsState root, AbstractState wrappedState) {
    UsageStatisticsState result = root.clone(wrappedState);

    for (SingleIdentifier id : this.recentUsages.keySet()) {
      result.recentUsages.put(id, new LinkedList<>(this.recentUsages.get(id)));
    }
    return result;
  }

  public UsageContainer getContainer() {
    return container;
  }

  public void saveUnsafesInContainer() {
    for (SingleIdentifier id : recentUsages.keySet()) {
      for (UsageInfo uinfo : recentUsages.get(id)) {
        container.add(id, uinfo);
      }
    }
  }

  public void updateKeyState(AbstractState pState) {
    for (SingleIdentifier id : recentUsages.keySet()) {
      for (UsageInfo uinfo : recentUsages.get(id)) {
        if (uinfo.getKeyState() == null) {
          uinfo.setKeyState(pState);
        }
      }
    }
  }
}
