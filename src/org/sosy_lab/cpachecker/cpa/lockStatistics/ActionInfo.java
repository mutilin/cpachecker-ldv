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

import java.util.HashSet;
import java.util.Set;

/* This class has information about one action with variable - line, read/write,
 * locks.
 */
public class ActionInfo
{
  private int line;
  private Set<LockStatisticsMutex> locks;
  private boolean isWrite;

  ActionInfo(int l, Set<LockStatisticsMutex> lo, boolean write)
  {
    line = l;
    isWrite = write;
    locks = new HashSet<LockStatisticsMutex>();

    //we could't clone it. I don't know why
    for (LockStatisticsMutex lock : lo)
      locks.add(lock);
  }

  public Set<LockStatisticsMutex> getLocks() {
    return locks;
  }

  public boolean isWrite() {
    return isWrite;
  }

  public int getLine() {
    return line;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append("  In line ");

   // for (Integer line : lines)
      sb.append(line);
    /*if (lines.size() > 0)
        sb.delete(sb.length() - 2, sb.length());
*/
    if (locks.size() > 0) {
      sb.append(" was locked with {");
      for (LockStatisticsMutex lock : locks)
        sb.append(lock.toString() + ", ");

      if (locks.size() > 0)
        sb.delete(sb.length() - 2, sb.length());
      sb.append("}, ");
    }
    else {
      sb.append(" without locks, ");
    }
    if (isWrite)
      sb.append("write access");
    else
      sb.append("read access");

    return sb.toString();
  }
}