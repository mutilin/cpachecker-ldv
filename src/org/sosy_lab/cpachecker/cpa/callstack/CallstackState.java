/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.callstack;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Serializable;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public final class CallstackState implements AbstractState, Partitionable, AbstractQueryableState, Serializable {

  private static final long serialVersionUID = 3629687385150064994L;
  private CallstackState previousState;
  private final String currentFunction;
  private transient CFANode callerNode;
  private final int depth;

  public CallstackState(CallstackState previousElement, String function, CFANode callerNode) {
    this.previousState = previousElement;
    this.currentFunction = checkNotNull(function);
    this.callerNode = checkNotNull(callerNode);
    if (previousElement == null) {
      depth = 1;
    } else {
      depth = previousElement.getDepth() + 1;
    }
  }

  public CallstackState getPreviousState() {
    return previousState;
  }

  public String getCurrentFunction() {
    return currentFunction;
  }

  public CFANode getCallNode() {
    return callerNode;
  }

  public int getDepth() {
    return depth;
  }

  @Override
  public Object getPartitionKey() {
    return this;
  }

  @Override
  public String toString() {
    return "Function " + getCurrentFunction()
        + " called from node " + getCallNode()
        + ", stack depth " + getDepth()
        + " [" + Integer.toHexString(super.hashCode()) + "]";
  }

  public boolean sameStateInProofChecking(CallstackState pOther) {
    if (pOther.callerNode == callerNode
        && pOther.depth == depth
        && pOther.currentFunction.equals(currentFunction)
        && (pOther.previousState == previousState || (previousState != null && pOther.previousState != null && previousState
            .sameStateInProofChecking(pOther.previousState)))) { return true; }
    return false;
  }

  @Override
  public String getCPAName() {
    return "Callstack";
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    return false;
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    if (pProperty.compareToIgnoreCase("caller") == 0) {
      if (callerNode != null) {
        return this.callerNode.getFunctionName();
      } else {
        return "";
      }
    }

    throw new InvalidQueryException(String.format("Evaluating %s not supported by %s", pProperty, this.getClass()
        .getCanonicalName()));
  }

  public int hashCodeWithoutNode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((currentFunction == null) ? 0 : currentFunction.hashCode());
    result = prime * result + depth;
    result = prime * result + ((previousState == null) ? 0 : previousState.hashCodeWithoutNode());
    return result;
  }

  public boolean equalsWithoutNode(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CallstackState other = (CallstackState) obj;
    if (currentFunction == null) {
      if (other.currentFunction != null) {
        return false;
      }
    } else if (!currentFunction.equals(other.currentFunction)) {
      return false;
    }
    if (depth != other.depth) {
      return false;
    }
    if (previousState == null) {
      if (other.previousState != null) {
        return false;
      }
    } else if (!previousState.equalsWithoutNode(other.previousState)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((callerNode == null) ? 0 : callerNode.hashCode());
    result = prime * result
        + ((currentFunction == null) ? 0 : currentFunction.hashCode());
    result = prime * result + depth;
    result = prime * result
        + ((previousState == null) ? 0 : previousState.hashCode());
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
    CallstackState other = (CallstackState) obj;
    if (callerNode == null) {
      if (other.callerNode != null)
        return false;
    } else if (!callerNode.equals(other.callerNode))
      return false;
    if (currentFunction == null) {
      if (other.currentFunction != null)
        return false;
    } else if (!currentFunction.equals(other.currentFunction))
      return false;
    if (depth != other.depth)
      return false;
    if (previousState == null) {
      if (other.previousState != null)
        return false;
    } else if (!previousState.equals(other.previousState))
      return false;
    return true;
  }

  @Override
  public CallstackState clone() {
    if (this.previousState != null) {
      return new CallstackState(this.previousState.clone(), this.currentFunction, this.callerNode);
    } else {
      return new CallstackState(null, this.currentFunction, this.callerNode);
    }
  }

  @Override
  public void modifyProperty(String pModification) throws InvalidQueryException {
    throw new InvalidQueryException("modifyProperty not implemented by " + this.getClass().getCanonicalName());
  }

  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeInt(callerNode.getNodeNumber());
  }

  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    int nodeNumber = in.readInt();
    callerNode = GlobalInfo.getInstance().getCFAInfo().get().getNodeByNodeNumber(nodeNumber);
  }
}
