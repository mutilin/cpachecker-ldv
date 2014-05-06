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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.BinaryIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;


public class LocalState implements AbstractState {

  public static enum DataType {
    LOCAL,
    GLOBAL;

    @Override
    public String toString() {
      return name().toLowerCase();
    }

    public static DataType max(DataType op1, DataType op2) {
      if (op1 == GLOBAL || op2 == GLOBAL) {
        return GLOBAL;
      } else if (op1 == null || op2 == null) {
        return null;
      } else {
        return LOCAL;
      }
    }
  }
  //map from variable id to its type
  private final LocalState previousState;
  private final Map<AbstractIdentifier, DataType> DataInfo;

  public LocalState(LocalState state) {
    DataInfo = new HashMap<>();
    previousState = state;
  }

  /*public LocalState(LocalState state, Configuration pConfig) throws InvalidConfigurationException {
    DataInfo = new HashMap<>();
    previousState = state;
  }*/

  private LocalState(Map<AbstractIdentifier, DataType> oldMap, LocalState state) {
    DataInfo = new HashMap<>(oldMap);
    previousState = state;
  }

  public LocalState getPreviousState() {
    return previousState;
  }

  public void forceSetLocal(AbstractIdentifier name) {
    DataInfo.put(name, DataType.LOCAL);
  }

  public void set(AbstractIdentifier name, DataType type) {
    if (LocalCPA.localVariables.contains(name.toString())) {
      DataInfo.put(name, DataType.LOCAL);
      return;
    }
    if (name instanceof GlobalVariableIdentifier) {
      //Don't save obvious information
      return;
    }
    //Check information we've already have;
    AbstractIdentifier infoId = name.containsIn(DataInfo.keySet());
    if (infoId == null) {
      //We have no information
      if (type != null) {
        DataInfo.put(name, type);
      }
      return;
    }
    DataType lastType = DataInfo.get(infoId);
    if (type == null) {
      if (name == infoId) {
        DataInfo.remove(name);
      }
      return;
    }
    if (infoId == name) {
      DataInfo.put(name, type);
    } else {
      DataInfo.put(name, DataType.max(type, lastType));
    }
  }

  public DataType getType(AbstractIdentifier pName) {
    return getType(this.DataInfo, pName);
  }

  public static DataType getType(Map<? extends AbstractIdentifier, DataType> localInfo, AbstractIdentifier aId) {
    AbstractIdentifier name;
    if (aId instanceof LocalVariableIdentifier || aId instanceof GlobalVariableIdentifier) {
      name = ((SingleIdentifier)aId).getGeneralId();
    } else {
      name = aId;
    }
    if (LocalCPA.localVariables != null && name instanceof SingleIdentifier
        && LocalCPA.localVariables.contains(((SingleIdentifier)name).getName())) {
      return DataType.LOCAL;
    }
    if (localInfo.containsKey(name)) {
      return localInfo.get(name);
    } else {
      if (name instanceof GlobalVariableIdentifier) {
        return DataType.GLOBAL;
      } else if (name instanceof LocalVariableIdentifier && !name.isPointer()) {
        //it is not value of variable, it is memory location
        return DataType.LOCAL;
      }
      else if (name instanceof BinaryIdentifier) {
        AbstractIdentifier tmp = name.containsIn(localInfo.keySet());
        return (tmp == null ? null : localInfo.get(tmp));
      } else if (name instanceof ConstantIdentifier) {
        if (name.isPointer() && !((ConstantIdentifier)name).getName().equals("0")) {
          return DataType.GLOBAL;
        } else {
          return DataType.LOCAL;
        }
      } else if (name instanceof StructureIdentifier){
        StructureIdentifier id = (StructureIdentifier) name;
        return getType(localInfo, id.getOwner());
      } else {
        return null;
      }
    }
  }

  @Override
  public LocalState clone() {
    return new LocalState(this.DataInfo, this.previousState);
  }

  private LocalState clone(LocalState pPreviousState) {
    return new LocalState(this.DataInfo, pPreviousState);
  }

  public LocalState expand(LocalState rootState) {
    return this.clone(rootState.previousState);
  }

  public LocalState reduce() {
    return this.clone(null);
  }

  public LocalState join(LocalState pState2) {
    //by definition of Merge operator we should return state2, not this!
    if (this.equals(pState2)) {
      return pState2;
    }
    LocalState joinedPreviousState = null;
    if ((this.previousState != null && pState2.previousState == null)
        && (this.previousState == null && pState2.previousState != null) ) {
      System.err.println("Panic! Merging states, but one of them has previous and another hasn't");
    } else if (this.previousState != null && pState2.previousState != null
        && !this.previousState.equals(pState2.previousState)) {
      //it can be, when we join states, called from different functions
      joinedPreviousState = this.previousState.join(pState2.previousState);
    } else if (this.previousState != null && pState2.previousState != null
        && this.previousState.equals(pState2.previousState)) {
      joinedPreviousState = this.previousState;
    }

    LocalState joinState = this.clone(joinedPreviousState);
    Set<AbstractIdentifier> toDelete = new HashSet<>();

    for (AbstractIdentifier name : joinState.DataInfo.keySet()) {
      if (!pState2.DataInfo.containsKey(name) && joinState.DataInfo.get(name) != DataType.GLOBAL) {
        toDelete.add(name);
      }
    }

    for (AbstractIdentifier del : toDelete) {
      joinState.DataInfo.remove(del);
    }

    for (AbstractIdentifier name : pState2.DataInfo.keySet()) {
      if (!joinState.DataInfo.containsKey(name) && pState2.DataInfo.get(name) == DataType.GLOBAL) {
        joinState.DataInfo.put(name, DataType.GLOBAL);
      } else if (joinState.DataInfo.containsKey(name)) {
        joinState.DataInfo.put(name, DataType.max(this.DataInfo.get(name), pState2.DataInfo.get(name)));
      }
    }

    return joinState;
  }

  public boolean isLessOrEqual(LocalState pState2) {
    //LOCAL < NULL < GLOBAL
    /*for (AbstractIdentifier name : this.DataInfo.keySet()) {
      if (this.DataInfo.get(name) == DataType.LOCAL) {
        continue;
      }
      //Here thisType can be only Global, so pState2 also should contains Global
      if (!pState2.DataInfo.containsKey(name) || pState2.DataInfo.get(name) == DataType.LOCAL) {
        return false;
      }
    }
    for (AbstractIdentifier name : pState2.DataInfo.keySet()) {
      if (!this.DataInfo.containsKey(name) && pState2.DataInfo.get(name) == DataType.LOCAL) {
        return false;
      }
    }*/
    for (AbstractIdentifier name : this.DataInfo.keySet()) {
      if (this.getType(name) != pState2.getType(name)) {
        return false;
      }
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
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    LocalState other = (LocalState) obj;
    if (DataInfo == null) {
      if (other.DataInfo != null) {
        return false;
      }
    } else if (!DataInfo.equals(other.DataInfo)) {
      return false;
    }
    return true;
  }

  public String toLog() {
    StringBuilder sb = new StringBuilder();
    for (AbstractIdentifier id : DataInfo.keySet()) {
      if (id instanceof SingleIdentifier) {
        sb.append(((SingleIdentifier)id).toLog() + ";" + DataInfo.get(id) + "\n");
      } else {
        System.err.println("Can't write to log " + id.toString());
      }
    }

    if (sb.length() > 2) {
      sb.delete(sb.length() - 1, sb.length());
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    for (AbstractIdentifier id : DataInfo.keySet()) {
      sb.append(id.toString() + " - " + DataInfo.get(id) + "\n");
    }

    if (sb.length() > 2) {
      sb.delete(sb.length() - 1, sb.length());
    }
    return sb.toString();
  }
}