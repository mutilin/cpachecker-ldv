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
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.BinaryIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.ConstantIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
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
  private final Map<AbstractIdentifier, DataType> DataInfo;

  public LocalState(LocalState state) {
    DataInfo = new HashMap<AbstractIdentifier, DataType>();
    previousState = state;
    returnExpression = null;
  }

  private LocalState(Map<AbstractIdentifier, DataType> oldMap, LocalState state, CExpression ret) {
    DataInfo = new HashMap<AbstractIdentifier, DataType>(oldMap);
    previousState = state;
    returnExpression = ret;
  }

  /*public void save(SingleIdentifier name, DataType type) {
    if (DataInfo.containsKey(name))
      DataInfo.put(name, DataType.max(type, DataInfo.get(name)));
    else if (type != null)
      DataInfo.put(name, type);
  }*/

  public LocalState getPreviousState() {
    return previousState;
  }

  public void setReturnExpression(CExpression expr) {
    returnExpression = expr;
  }

  public CExpression getReturnExpression() {
    return returnExpression;
  }

  public void set(AbstractIdentifier name, DataType type) {
    if (type == null) {
      if (DataInfo.containsKey(name)) {
        DataInfo.remove(name);
      }
      return;
    }
    if (name instanceof VariableIdentifier)
      DataInfo.put(name, type);
    else if (name instanceof StructureIdentifier){
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
  public DataType getType(AbstractIdentifier name) {
    if (DataInfo.containsKey(name))
      return DataInfo.get(name);
    else {
      if (name instanceof GlobalVariableIdentifier)
        return DataType.GLOBAL;
      else if (name instanceof LocalVariableIdentifier) {
        LocalVariableIdentifier localId = (LocalVariableIdentifier) name;
        if (localId.getDereference() < 0 && !(localId.getType() instanceof CPointerType)) {
          //TODO may be precised...
          return DataType.LOCAL;
        }
        return null;
      }
      else if (name instanceof BinaryIdentifier) {
        //in good case, this if we won't use... But let it be.
        DataType type1 = getType(((BinaryIdentifier)name).getIdentifier1());
        DataType type2 = getType(((BinaryIdentifier)name).getIdentifier2());
        return DataType.max(type1, type2);
      } else if (name instanceof ConstantIdentifier) {
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
    for (AbstractIdentifier name : joinState.DataInfo.keySet()) {
      if (!pState2.DataInfo.containsKey(name) && joinState.DataInfo.get(name) != DataType.GLOBAL)
        joinState.DataInfo.remove(name);
    }
    for (AbstractIdentifier name : pState2.DataInfo.keySet()) {
      if (!joinState.DataInfo.containsKey(name) && pState2.DataInfo.get(name) == DataType.GLOBAL)
        joinState.DataInfo.put(name, DataType.GLOBAL);
      else if (joinState.DataInfo.containsKey(name))
        joinState.DataInfo.put(name, DataType.max(this.DataInfo.get(name), pState2.DataInfo.get(name)));
    }
    return joinState;
  }

  public boolean isLessOrEqual(LocalState pState2) {

    for (AbstractIdentifier name : this.DataInfo.keySet()) {
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

  public String toLog() {
    StringBuilder sb = new StringBuilder();
    for (AbstractIdentifier id : DataInfo.keySet()) {
      if (id instanceof SingleIdentifier)
        sb.append(((SingleIdentifier)id).toLog() + ";" + DataInfo.get(id) + "\n");
      else
        System.err.println("Can't write to log " + id.toString());
    }

    if (sb.length() > 2)
      sb.delete(sb.length() - 1, sb.length());
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

    if (sb.length() > 2)
      sb.delete(sb.length() - 1, sb.length());
    //sb.append("}");
    return sb.toString();
  }
}