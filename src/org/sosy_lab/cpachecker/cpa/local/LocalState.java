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
import org.sosy_lab.cpachecker.util.identifiers.ReturnIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.VariableIdentifier;


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
  private LocalState previousState;
  //private CExpression returnExpression;
  private final Map<AbstractIdentifier, DataType> DataInfo;
  //private static int counter = 0;

  public LocalState(LocalState state) {
    DataInfo = new HashMap<>();
    previousState = state;
    //returnExpression = null;
  }

  private LocalState(Map<AbstractIdentifier, DataType> oldMap, LocalState state) {
    DataInfo = new HashMap<>(oldMap);
    previousState = state;
    //returnExpression = ret;
  }

  public LocalState getPreviousState() {
    return previousState;
  }

  /*public void setReturnExpression(CExpression expr) {
    returnExpression = expr;
  }

  public CExpression getReturnExpression() {
    return returnExpression;
  }*/

  public void set(AbstractIdentifier name, DataType type) {
    if (type == null) {
      if (DataInfo.containsKey(name)) {
        DataInfo.remove(name);
      }
      return;
    }
    if (name instanceof VariableIdentifier) {
      if (name instanceof ReturnIdentifier && DataInfo.containsKey(name)) {
        DataInfo.put(name, DataType.max(type, DataInfo.get(name)));
      } else {
        DataInfo.put(name, type);
      }
    } else if (name instanceof StructureIdentifier){
      //check if parent struct is global
      AbstractIdentifier owner = name;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (DataInfo.containsKey(owner)) {
          DataInfo.put(name, DataType.max(type, DataInfo.get(owner)));
          return;
        }
      }
      //we've found nothing
      DataInfo.put(name, type);
    } else if (name instanceof ConstantIdentifier) {
      //ConstantIdentifier - do nothing
    } else if (name instanceof BinaryIdentifier) {
      if (((BinaryIdentifier)name).getDereference() > 0) {
        DataInfo.put(name, type);
      } else {
        set(((BinaryIdentifier)name).getIdentifier1(), type);
        set(((BinaryIdentifier)name).getIdentifier2(), type);
      }
    }
  }
  public DataType getType(AbstractIdentifier pName) {
    AbstractIdentifier name;
    if (pName instanceof GlobalVariableIdentifier || pName instanceof LocalVariableIdentifier) {
      name = ((SingleIdentifier)pName).getGeneralId();
    } else {
      name = pName;
    }

    if (DataInfo.containsKey(name)) {
      return DataInfo.get(name);
    } else {
      if (name instanceof GlobalVariableIdentifier) {
        return DataType.GLOBAL;
      } else if (name instanceof LocalVariableIdentifier) {
        LocalVariableIdentifier localId = (LocalVariableIdentifier) name;
        if (localId.getDereference() == 0/* && !(localId.getType() instanceof CPointerType)*/) {
          //it is not value of variable, it is memory location
          return DataType.LOCAL;
        } else if (localId.getDereference() < 0) {
          //this is error. We can't get address here
          System.err.println("Adress in getType()");
        }
        return null;
      }
      else if (name instanceof BinaryIdentifier) {
        //in good case, we won't use this... But let it be.
        DataType type1 = getType(((BinaryIdentifier)name).getIdentifier1());
        DataType type2 = getType(((BinaryIdentifier)name).getIdentifier2());
        return DataType.max(type1, type2);
      } else if (name instanceof ConstantIdentifier) {
        /*if (name.getDereference() > 0)
          return DataType.GLOBAL;
        else*/
          return DataType.LOCAL;
      } else if (name instanceof StructureIdentifier){
        StructureIdentifier id = (StructureIdentifier) name;
        return this.getType(id.getOwner());
      } else {
        return null;
      }
    }
  }

  public boolean contains(AbstractIdentifier name) {
    return DataInfo.containsKey(name);
  }

  @Override
  public LocalState clone() {
    return new LocalState(this.DataInfo, this.previousState);
  }

  public LocalState reduce() {
    return new LocalState(this.DataInfo, null);
  }

  public LocalState expand(LocalState rootState) {
    //LocalState newState = this.clone();
    this.previousState = rootState.previousState;
    return this;
  }

  public LocalState join(LocalState pState2) {
    //by definition of Merge operator we should return state2, not this!
    if (this.equals(pState2)) {
      return pState2;
    }
    LocalState joinState = this.clone();
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
    //counter++;
    //System.out.println("Merge: " + counter);
    if ((this.previousState != null && pState2.previousState == null)
        && (this.previousState == null && pState2.previousState != null) ) {
      System.err.println("Panic! Merging states, but one of them has previous and another hasn't");
    } else if (this.previousState != null && pState2.previousState != null
        && !this.previousState.equals(pState2.previousState)) {
      //it can be, when we join states, called from different functions
      joinState.previousState = this.previousState.join(pState2.previousState);
    }

    /*if ((this.returnExpression != null && pState2.returnExpression == null)
        && (this.returnExpression == null && pState2.returnExpression != null) ) {
      System.err.println("Panic! Merging states, but one of them has returnExpression and another hasn't");

    } else if (this.returnExpression != null && pState2.returnExpression != null) {
      IdentifierCreator creator = new IdentifierCreator();
      try {
        AbstractIdentifier thisId = this.returnExpression.accept(creator);
        AbstractIdentifier otherId = pState2.returnExpression.accept(creator);
        DataType thisType = this.getType(thisId);
        DataType otherType = pState2.getType(otherId);
        if (DataType.max(thisType, otherType) != thisType) {
          joinState.returnExpression = pState2.returnExpression;
        }
      } catch (HandleCodeException e) {
        System.err.println("Can't create id for " + this.returnExpression.toASTString());
      }
    }*/
    return joinState;
  }

  public boolean isLessOrEqual(LocalState pState2) {
    //LOCAL < NULL < GLOBAL
    for (AbstractIdentifier name : this.DataInfo.keySet()) {
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

    //sb.append("{");
    for (AbstractIdentifier id : DataInfo.keySet()) {
      //sb.append("(");
      sb.append(id.toString() + " - " + DataInfo.get(id) + "\n");
    }

    if (sb.length() > 2) {
      sb.delete(sb.length() - 1, sb.length());
    }
    //sb.append("}");
    return sb.toString();
  }
}