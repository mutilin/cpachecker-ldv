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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class TreeLeaf implements Comparable<TreeLeaf> {
  private final String code;
  private final int line;
  private final Set<TreeLeaf> children;

  private static TreeLeaf trunk = new TreeLeaf("super_main", 0);

  private TreeLeaf(String c, int l) {
    code = c;
    line = l;
    children = new TreeSet<>();
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
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TreeLeaf other = (TreeLeaf) obj;
    if (code == null) {
      if (other.code != null) {
        return false;
      }
    } else if (!code.equals(other.code)) {
      return false;
    }
    if (line != other.line) {
      return false;
    }
    return true;
  }

  public TreeLeaf add(String code, int line) {
    for (TreeLeaf leaf : this.children) {
      if (leaf.code.equals(code) && leaf.line == line) {
        return leaf;
      }
    }
    TreeLeaf returnLeaf = new TreeLeaf(code, line);
    this.children.add(returnLeaf);
    return returnLeaf;
  }
  
  public Iterator<TreeLeaf> getChildrenIterator() {
    return children.iterator();
  }

  @Override
  public String toString() {
    return "Line " + line + ":     N0 -{" + code + ";}-> N0\n";
  }

  public static TreeLeaf getTrunkState() {
    return trunk;
  }

  public static TreeLeaf clearTrunkState() {
    trunk.children.clear();
    return getTrunkState();
  }

  @Override
  public int compareTo(TreeLeaf arg0) {
    return this.line - arg0.line;
  }
}
