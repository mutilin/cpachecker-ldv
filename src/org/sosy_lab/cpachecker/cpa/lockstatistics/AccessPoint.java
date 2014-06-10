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
package org.sosy_lab.cpachecker.cpa.lockstatistics;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.bam.BAMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;


public class AccessPoint {
  private final LineInfo line;
  /**
   * callstack is responsible for information in report.
   * So, in every moment in time this field should be correct to state of analysis
   */
  private CallstackState callstack;
  /**
   * This field shows, if this access point is new or not
   */
  private boolean isNewPoint;

  AccessPoint(LineInfo l, CallstackState stack) {
    line = l;
    callstack = stack;
    isNewPoint = true;
  }

  public CallstackState getCallstack() {
    return callstack;
  }

  public void markAsOld() {
    isNewPoint = false;
  }

  public boolean isNew() {
    return isNewPoint;
  }

  public LineInfo getLineInfo() {
    return line;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((callstack == null) ? 0 : callstack.hashCodeWithoutNode());
    result = prime * result + ((line == null) ? 0 : line.hashCode());
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
    AccessPoint other = (AccessPoint) obj;
    if (callstack == null) {
      if (other.callstack != null) {
        return false;
      }
    } else if (!callstack.equalsWithoutNode(other.callstack)) {
      return false;
    }
    if (line == null) {
      if (other.line != null) {
        return false;
      }
    } else if (!line.equals(other.line)) {
      return false;
    }
    return true;
  }

  @Override
  public AccessPoint clone() {
    AccessPoint result =  new AccessPoint(line, callstack);
    result.isNewPoint = this.isNewPoint;
    return result;
  }

  public AccessPoint expandCallstack(BAMRestoreStack pRestorator, CallstackReducer pReducer, CFANode pNode) {
    AccessPoint result = this.clone();
    CallstackState reducedCallstack = (CallstackState)pReducer.getVariableReducedState(callstack, null, pNode);
    try {
      result.callstack = pRestorator.restoreCallstack(reducedCallstack);
    } catch (HandleCodeException e) {
      System.err.println(e.getMessage());
    }
    return result;
  }
}
