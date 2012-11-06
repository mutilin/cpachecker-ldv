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
package org.sosy_lab.cpachecker.cpa.lockStatistics;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.LineInfo;


public class LockStatisticsLock {

  public static enum LockType {
    MUTEX,
    GLOBAL_LOCK,
    LOCAL_LOCK,
    SPINLOCK;

    public String toASTString() {
      return name().toLowerCase();
    }
  }

  private String name;
  private String functionName;
  private CallstackState callstack;
  private LineInfo line;
  private LockType type;

  LockStatisticsLock(String n, int l, LockType t, String fName, CallstackState s) {
    name = n;
    line = new LineInfo(l);
    type = t;
    functionName = fName;
    callstack = s;
  }

  LockStatisticsLock(String n, int l, LockType t, CallstackState s) {
    name = n;
    line = new LineInfo(l);
    type = t;
    functionName = "";
    callstack = s;
  }

  public String getName() {
    return name;
  }

  public int getLine() {
    return line.getLine();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((functionName == null) ? 0 : functionName.hashCode());
    //result = prime * result + ((line == null) ? 0 : line.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    LockStatisticsLock other = (LockStatisticsLock) obj;
    if (functionName == null) {
      if (other.functionName != null)
        return false;
    } else if (!functionName.equals(other.functionName))
      return false;
    /*if (line == null) {
      if (other.line != null)
        return false;
    } else if (!line.equals(other.line))
      return false;*/
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (type != other.type)
      return false;
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("  " + type.toASTString() + " "+ name + (functionName != "" ? " in " : "") + functionName + "(" + line.toString()
        + " line)\n");

    CallstackState e = callstack;
    sb.append("        ");
    while (e != null) {
      LineInfo lineN = null;
      for (int i = 0; i < e.getCallNode().getNumLeavingEdges(); i++) {
        CFAEdge edge = e.getCallNode().getLeavingEdge(i);
        if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
          lineN = new LineInfo(edge.getLineNumber());
          break;
        }
      }
      sb.append(e.getCurrentFunction() + "(" + (lineN != null ? lineN.toString() : "-") + ")" + " <- ");
      e = e.getPreviousState();
    }
    if (callstack != null)
      sb.delete(sb.length() - 3, sb.length());
    sb.append("\n");
    return sb.toString();
  }

  public Object getFunctionName() {
    return functionName;
  }
}
