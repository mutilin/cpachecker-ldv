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
import org.sosy_lab.cpachecker.util.identifiers.Identifier;

/**
 * Represents one abstract state of the UsageStatistics CPA.
 */
class UsageStatisticsState extends AbstractSingleWrapperState  {
  /* Boilerplate code to avoid serializing this class */

  private static final long serialVersionUID = -898577877284268426L;

  private Map<Identifier, Identifier> variableBindingRelation;

  public UsageStatisticsState(AbstractState pWrappedElement) {
    super(pWrappedElement);
    variableBindingRelation = new HashMap<Identifier, Identifier>();
  }

  public UsageStatisticsState(AbstractState pWrappedElement, Map<Identifier, Identifier> map) {
    super(pWrappedElement);
    variableBindingRelation = new HashMap<Identifier, Identifier>(map);
  }

  public boolean contains(Identifier id) {
    return variableBindingRelation.containsKey(id);
  }

  public void put(Identifier id1, Identifier id2) {
   // System.out.println("Link " + (id1 == null ? "null" : id1.getName()) + " and " + (id2 == null ? "null" : id2.getName()));
    if (!id1.equals(id2))
      variableBindingRelation.put(id1, id2);
  }

  public Identifier get(Identifier id) {
    return variableBindingRelation.get(id);
  }

  public Map<Identifier, Identifier> getMap() {
    return variableBindingRelation;
  }

  @Override
  public UsageStatisticsState clone() {
    return new UsageStatisticsState(this.getWrappedState(), this.variableBindingRelation);
  }

  public UsageStatisticsState clone(AbstractState pWrappedState) {
    return new UsageStatisticsState(pWrappedState, this.variableBindingRelation);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((variableBindingRelation == null) ? 0 : variableBindingRelation.hashCode());
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
    if (variableBindingRelation == null) {
      if (other.variableBindingRelation != null)
        return false;
    } else if (!variableBindingRelation.equals(other.variableBindingRelation))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append(getWrappedState());
    return variableBindingRelation.size() + " : " + str.toString();
  }

  boolean isLessOrEqual(UsageStatisticsState other) {

    // this element is not less or equal than the other element, if it contains less elements
    if (variableBindingRelation.size() < other.variableBindingRelation.size()) {
      return false;
    }

    // also, this element is not less or equal than the other element,
    // if any one constant's value of the other element differs from the constant's value in this element
    for (Identifier id : variableBindingRelation.keySet()) {
      if (!other.variableBindingRelation.containsKey(id)) {
        return false;
      }
    }

    return true;
  }

}
