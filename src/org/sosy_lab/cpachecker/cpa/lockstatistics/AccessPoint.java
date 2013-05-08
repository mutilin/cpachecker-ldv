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

import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.LineInfo;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;


public class AccessPoint {
  public LineInfo line;
  private CallstackState callstack;
  private boolean replaceLabel;

  AccessPoint(LineInfo l, CallstackState stack) {
    line = l;
    callstack = stack;
    replaceLabel = false;
  }

  public CallstackState getCallstack() {
    return callstack;
  }

  public void setLabel() {
    replaceLabel = true;
  }

  public void resetLabel() {
    replaceLabel = false;
  }

  public boolean getLabel() {
    return replaceLabel;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((callstack == null) ? 0 : callstack.hashCode());
    result = prime * result + ((line == null) ? 0 : line.hashCode());
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
    AccessPoint other = (AccessPoint) obj;
    if (callstack == null) {
      if (other.callstack != null)
        return false;
    } else if (!callstack.equals(other.callstack))
      return false;
    if (line == null) {
      if (other.line != null)
        return false;
    } else if (!line.equals(other.line))
      return false;
    return true;
  }

  public void setCallstack(CallstackState state) {
    callstack = state;
  }

  public void expandCallstack(AccessPoint rootAccessPoint) throws HandleCodeException {
    if (this.line != rootAccessPoint.line) return;
    if (this.callstack == null)
      this.callstack = rootAccessPoint.callstack;
    else
      //strange: how we can have not null callstack, if we reduced it
      throw new HandleCodeException("Not null reduced callstack");
  }

  public void replace(AccessPoint pAccessPoint) {
    line = pAccessPoint.line;
    callstack = pAccessPoint.callstack;
    replaceLabel = false;
  }
}
