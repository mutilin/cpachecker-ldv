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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

/**
 * Represents one abstract state of the UsageStatistics CPA.
 */
class UsageStatisticsState extends AbstractSingleWrapperState  {
  /* Boilerplate code to avoid serializing this class */

  private static final long serialVersionUID = -898577877284268426L;

  private Map<VariableIdentifier, VariableIdentifier> PointerRelation;

  public UsageStatisticsState(AbstractState pWrappedElement) {
    super(pWrappedElement);
    PointerRelation = new HashMap<VariableIdentifier, VariableIdentifier>();
  }

  public UsageStatisticsState(AbstractState pWrappedElement, Map<VariableIdentifier, VariableIdentifier> map) {
    super(pWrappedElement);
    PointerRelation = new HashMap<VariableIdentifier, VariableIdentifier>(map);
  }

  public boolean contains(VariableIdentifier id) {
    return PointerRelation.containsKey(id);
  }

  public void put(VariableIdentifier id1, VariableIdentifier id2) {
    System.out.println("Link " + (id1 == null ? "null" : id1.getName()) + " and " + (id2 == null ? "null" : id2.getName()));
    if (!id1.equals(id2))
      PointerRelation.put(id1, id2);
  }

  public VariableIdentifier get(VariableIdentifier id) {
    return PointerRelation.get(id);
  }

  public Map<VariableIdentifier, VariableIdentifier> getMap() {
    return PointerRelation;
  }

  @Override
  public UsageStatisticsState clone() {
    return new UsageStatisticsState(this.getWrappedState(), this.PointerRelation);
  }

  public UsageStatisticsState clone(AbstractState pWrappedState) {
    return new UsageStatisticsState(pWrappedState, this.PointerRelation);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((PointerRelation == null) ? 0 : PointerRelation.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    UsageStatisticsState other = (UsageStatisticsState) obj;
    if (PointerRelation == null) {
      if (other.PointerRelation != null)
        return false;
    } else if (!PointerRelation.equals(other.PointerRelation))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append(getWrappedState());
    return PointerRelation.size() + " : " + str.toString();
  }

  boolean isLessOrEqual(UsageStatisticsState other) {

    // this element is not less or equal than the other element, if it contains less elements
    if (PointerRelation.size() < other.PointerRelation.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (VariableIdentifier id : PointerRelation.keySet()) {
      if (!other.PointerRelation.containsKey(id)) {
        return false;
      }
    }

    return true;
  }

}
