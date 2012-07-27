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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;

/* This class has information about one action with variable - line, read/write,
 * locks.
 */
public class VariableInfo
{
  public static enum EdgeType {
    DECLARATION,
    ASSUMPTION,
    ASSIGNMENT;

    public String toASTString() {
      return name().toLowerCase();
    }
  }

  public class LineInfo {
    private Set<LockStatisticsLock> locks;
    private int line;
    private boolean isWrite;
    private List<String> callStack;
    private EdgeType type;

    LineInfo(){
      locks = new HashSet<LockStatisticsLock>();
    }

    LineInfo(int l, Set<LockStatisticsLock> lo, boolean write, List<String> stack, EdgeType t)
    {
      line = l;
      isWrite = write;
      locks = new HashSet<LockStatisticsLock>(lo);
      callStack = new LinkedList<String>(stack);
      type = t;
    }
    public boolean isWrite() {
      return isWrite;
    }

    public int getLine() {
      return line;
    }

    public List<String> getCallStack() {
      return callStack;
    }

    public Set<LockStatisticsLock> getLocks() {
      return locks;
    }

    public boolean intersect(LineInfo line) {
      if (line.locks.size() == 0 && this.locks.size() == 0)
        return true;

      for (LockStatisticsLock mutex : line.locks) {
        if (this.locks.contains(mutex))
          return true;
      }
      return false;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("    In line ");
      sb.append(line);
      sb.append(" (" + type.toASTString() + ")");
      if (locks.size() > 0) {
        sb.append(" was locked with {");
        for (LockStatisticsLock lock : locks)
          sb.append(lock.toString() + ", ");

        if (locks.size() > 0)
          sb.delete(sb.length() - 2, sb.length());
        sb.append("}, ");
      }
      else {
        sb.append(" without locks, ");
      }
      if (isWrite)
        sb.append("write access.");
      else
        sb.append("read access.");

      sb.append("\n      Call stack: ");
      for (String function : callStack) {
        sb.append(function + " <- ");
      }
      if (callStack.size() > 0)
        sb.delete(sb.length() - 3, sb.length());
      sb.append("\n");
      return sb.toString();
    }

    public boolean isEqual(LineInfo other) {
      return (this.callStack.equals(other.callStack) && this.isWrite == other.isWrite &&
              this.type.equals(other.type) && this.line == other.line && this.locks.equals(other.locks));
    }
  }

  private String name;
  private Map<String, Set<LineInfo>> usageList;
  private boolean isGlobal;

  VariableInfo(String n){
    name = n;
    usageList = new HashMap<String, Set<LineInfo>>();
  }

  VariableInfo(String n, int l, Set<LockStatisticsLock> lo, boolean write, String t,
              List<String> stack, EdgeType type, boolean global)
  {
    name = n;
    usageList = new HashMap<String, Set<LineInfo>>();
    isGlobal = global;

    Set<LineInfo> tmpSet = new HashSet<LineInfo>();
    LineInfo tmpLine = new LineInfo(l, lo, write, stack, type);
    tmpSet.add(tmpLine);
    usageList.put(t, tmpSet);
  }

  public String getName() {
    return name;
  }

  public boolean isGlobal() {
    return isGlobal;
  }

  public boolean contains(String type){
    return usageList.containsKey(type);
  }

  public Set<LineInfo> get(String type) {
    if (usageList.containsKey(type)){
      return usageList.get(type);
    }
    else
      return null;
  }

  public Set<String> keySet() {
    return usageList.keySet();
  }

  public void add(String type, Set<LineInfo> lines) {
    if (!usageList.containsKey(type)) {
      usageList.put(type, lines);
    }
    else {
      //TODO ?
    }
  }

  public void add (String t, int l, Set<LockStatisticsLock> lo, boolean write,
                  List<String> stack, EdgeType type) {
    Set<LineInfo> tmpSet;

    if (usageList.containsKey(t)){
      tmpSet = usageList.get(t);
      LineInfo tmpLine = new LineInfo(l, lo, write, stack, type);

      for (LineInfo line : tmpSet) {
        if ((line.callStack.equals(tmpLine.callStack) && line.isWrite == tmpLine.isWrite &&
            line.type.equals(tmpLine.type) && line.line == tmpLine.line &&
            line.locks.equals(tmpLine.locks))) {
          //System.out.println("line " + line.getLine() + "is equal");
          return;
        }

      }
      tmpSet.add(tmpLine);
    }
    else {
      tmpSet = new HashSet<LineInfo>();
      LineInfo tmpLine = new LineInfo(l, lo, write, stack, type);
      tmpSet.add(tmpLine);
      usageList.put(t, tmpSet);
    }
  }

  public Map<String, Set<LineInfo>> getLines() {
    return usageList;
  }

  public int size() {
    return usageList.size();
  }

  public int lines() {
    int lines = 0;

    for (String type : usageList.keySet()){
      Set<LineInfo> tmpInfo = usageList.get(type);
      lines += tmpInfo.size();
    }
    return lines;
  }


  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append("Variable: " + name);
    if (isGlobal)
      sb.append(" is global, ");
    else
      sb.append(" is local, ");
    sb.append(usageList.size() + " types\n");
    sb.append("[\n");
    for (String type : usageList.keySet()) {
      sb.append("  Type: (" + type + "), " + usageList.get(type).size() + " usages\n");
      sb.append("  {\n");
      for (LineInfo line : usageList.get(type)){
        sb.append(line.toString() + "\n");
      }
      sb.append("  }\n");
    }
    sb.append("]\n");
    return sb.toString();
  }
}