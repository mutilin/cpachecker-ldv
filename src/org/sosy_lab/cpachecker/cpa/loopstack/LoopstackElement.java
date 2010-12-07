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
package org.sosy_lab.cpachecker.cpa.loopstack;

import static com.google.common.base.Preconditions.*;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.util.assumptions.AvoidanceReportingElement;

import com.google.common.base.Objects;

public class LoopstackElement implements AbstractElement, Partitionable, AvoidanceReportingElement {
  
  private final LoopstackElement previousElement;
  private final CFANode loopHeadNode;
  private final int depth;
  private final int iteration;
  private final boolean stop;
  
  public LoopstackElement(LoopstackElement previousElement, CFANode callerNode, int iteration, boolean stop) {
    this.previousElement = previousElement;
    this.loopHeadNode = checkNotNull(callerNode);
    if (previousElement == null) {
      depth = 1;
    } else {
      depth = previousElement.getDepth() + 1;
    }
    checkArgument(iteration > 0);
    this.iteration = iteration;
    this.stop = stop;
  }
  
  public LoopstackElement() {
    previousElement = null;
    loopHeadNode = null;
    depth = 0;
    iteration = 0;
    stop = false;
  }
  
  public LoopstackElement getPreviousElement() {
    return previousElement;
  }
  
  public CFANode getLoopHeadNode() {
    return loopHeadNode;
  }
  
  public int getDepth() {
    return depth;
  }
  
  public int getIteration() {
    return iteration;
  }
  
  @Override
  public Object getPartitionKey() {
    return this;
  }
  
  @Override
  public boolean mustDumpAssumptionForAvoidance() {
    return stop;
  }
  
  @Override
  public String toString() {
    return " Loop starting at node " + getLoopHeadNode()
         + ", stack depth " + getDepth()
         + " [" + Integer.toHexString(super.hashCode()) + "]";
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof LoopstackElement)) {
      return false;
    }

    LoopstackElement other = (LoopstackElement)obj;
    return (this.previousElement == other.previousElement)
        && (this.iteration == other.iteration)
        && (Objects.equal(this.loopHeadNode, other.loopHeadNode));
  }

  @Override
  public int hashCode() {
    return iteration * 17 + (loopHeadNode == null ? 0 : loopHeadNode.hashCode());
  }  
}
