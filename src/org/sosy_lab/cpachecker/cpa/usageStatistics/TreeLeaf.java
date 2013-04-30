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

import java.util.LinkedList;

import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;

public class TreeLeaf {
  public CallstackState stack;
  public String code;
  public int line;
  public LinkedList<TreeLeaf> children;

  private static TreeLeaf trunk = new TreeLeaf(null, "super_main", 0);

  private TreeLeaf(CallstackState name, String c, int l) {
    stack = name;
    code = c;
    line = l;
    children = new LinkedList<>();
  }

  public static TreeLeaf create(CallstackState state) {
    return new TreeLeaf(state, state.getCurrentFunction(), state.getCallNode().getLeavingEdge(0).getLineNumber());
  }

  public static TreeLeaf init(CallstackState state) {
    return new TreeLeaf(state, state.getCallNode().getFunctionName(), 0);
  }

  public static TreeLeaf init(String code, LineInfo line) {
    return new TreeLeaf(null, code, line.getLine());
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((code == null) ? 0 : code.hashCode());
    result = prime * result + line;
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
    TreeLeaf other = (TreeLeaf) obj;
    if (code == null) {
      if (other.code != null)
        return false;
    } else if (!code.equals(other.code))
      return false;
    if (line != other.line)
      return false;
    return true;
  }

  public TreeLeaf add(CallstackState state) {
    return add(state, state.getCurrentFunction(), state.getCallNode().getLeavingEdge(0).getLineNumber());
  }

  public TreeLeaf add(String code, int line) {
    return add(null, code, line);
  }

  private TreeLeaf add(CallstackState state, String code, int line) {
    for (TreeLeaf leaf : this.children) {
      if (leaf.code.equals(code) && leaf.line == line) {
        return leaf;
      }
    }
    TreeLeaf returnLeaf = new TreeLeaf(state, code, line);
    this.children.add(returnLeaf);
    return returnLeaf;
  }

  @Override
  public String toString() {
    return code + " in " + line + " with " + (stack == null ? "empty stack" : stack.hashCode()) + " stack and " + (children == null ? "no children" : children.size()) + " children.\n";
  }

  public static TreeLeaf getTrunkState() {
    return trunk;
  }

  public static TreeLeaf clearTrunkState() {
    trunk.children.clear();
    return getTrunkState();
  }

  public TreeLeaf addLast(CallstackState state) {
    return addLast(state, state.getCurrentFunction(), state.getCallNode().getLeavingEdge(0).getLineNumber());
  }

  private TreeLeaf addLast(CallstackState state, String code, int line) {
    for (TreeLeaf leaf : this.children) {
      if (leaf.code.equals(code) && leaf.line == line) {
        if (this.children.size() > this.children.indexOf(leaf) + 1) {
          this.children.remove(leaf);
          //this needs to move it to the end of list
          this.children.add(leaf);
        }
        return leaf;
      }
    }
    TreeLeaf returnLeaf = new TreeLeaf(state, code, line);
    this.children.add(returnLeaf);
    return returnLeaf;
  }

  public void addLast(String code, int line) {
    addLast(null, code, line);
  }
}
