/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;


public class LocalState implements AbstractState {

  public static enum DataType {
    LOCAL,
    GLOBAL;

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public static DataType max(DataType op1, DataType op2) {
      if (op1 == GLOBAL || op2 == GLOBAL)
        return GLOBAL;
      else if (op1 == null || op2 == null)
        return null;
      else
        return LOCAL;
    }
  }
  //map from variable id to its type
  private LocalState previousState;
  private CExpression returnExpression;
  private final Map<Id, DataType> DataInfo;

  public LocalState(LocalState state) {
    DataInfo = new HashMap<Id, DataType>();
    previousState = state;
    returnExpression = null;
  }

  private LocalState(Map<Id, DataType> oldMap, LocalState state, CExpression ret) {
    DataInfo = new HashMap<Id, DataType>(oldMap);
    previousState = state;
    returnExpression = ret;
  }

  public void save(Id name, DataType type) {
    if (DataInfo.containsKey(name))
      DataInfo.put(name, DataType.max(type, DataInfo.get(name)));
    else if (type != null)
      DataInfo.put(name, type);
  }

  public LocalState getPreviousState() {
    return previousState;
  }

  public void setReturnExpression(CExpression expr) {
    returnExpression = expr;
  }

  public CExpression getReturnExpression() {
    return returnExpression;
  }

  public void set(Id name, DataType type) {
    if (type == null) {
      if (DataInfo.containsKey(name)) {
        DataInfo.remove(name);
      }
      return;
    }
    if (name instanceof VarId)
      DataInfo.put(name, type);
    else {
      //check if parent struct is global
      Id owner = name;
      while (owner instanceof StructId) {
        owner = ((StructId)owner).getOwner();
        if (DataInfo.containsKey(owner)) {
          DataInfo.put(name, DataType.max(type, DataInfo.get(owner)));
          return;
        }
      }
      //we've found nothing
      DataInfo.put(name, type);
    }
  }
  public DataType getType(Id name) {
    if (DataInfo.containsKey(name))
      return DataInfo.get(name);
    else {
      if (name instanceof VarId || name == null)
        return null;
      else {
        StructId id = (StructId) name;
        return this.getType(id.getOwner());
      }
    }
  }

  public boolean contains(Id name) {
    return DataInfo.containsKey(name);
  }

  @Override
  public LocalState clone() {
    return new LocalState(this.DataInfo, this.previousState, this.returnExpression);
  }

  public LocalState reduce() {
    return new LocalState(this.DataInfo, null, null);
  }

  public void expand(LocalState rootState) {
    this.previousState = rootState.previousState;
    //this.returnExpression = rootState.returnExpression;
  }

  public LocalState join(LocalState pState2) {

    LocalState joinState = this.clone();
    for (Id name : joinState.DataInfo.keySet()) {
      if (!pState2.DataInfo.containsKey(name) && joinState.DataInfo.get(name) != DataType.GLOBAL)
        joinState.DataInfo.remove(name);
    }
    for (Id name : pState2.DataInfo.keySet()) {
      if (!joinState.DataInfo.containsKey(name) && pState2.DataInfo.get(name) == DataType.GLOBAL)
        joinState.DataInfo.put(name, DataType.GLOBAL);
      else if (joinState.DataInfo.containsKey(name))
        joinState.DataInfo.put(name, DataType.max(this.DataInfo.get(name), pState2.DataInfo.get(name)));
    }
    return joinState;
  }

  public boolean isLessOrEqual(LocalState pState2) {

    for (Id name : this.DataInfo.keySet()) {
      if (!pState2.DataInfo.containsKey(name))
        return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((DataInfo == null) ? 0 : DataInfo.hashCode());
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
    LocalState other = (LocalState) obj;
    if (DataInfo == null) {
      if (other.DataInfo != null)
        return false;
    } else if (!DataInfo.equals(other.DataInfo))
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    //sb.append("{");
    for (Id id : DataInfo.keySet()) {
      //sb.append("(");
      sb.append(id.toString() + " - " + DataInfo.get(id) + "\n");
    }

    if (sb.length() > 2)
      sb.delete(sb.length() - 1, sb.length());
    //sb.append("}");
    return sb.toString();
  }
}