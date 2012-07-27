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


public class LockStatisticsLock {

  public static enum LockType {
    MUTEX,
    SPINLOCK;

    public String toASTString() {
      return name().toLowerCase();
    }
  }

  private String name;
  private int line;
  private LockType type;

  LockStatisticsLock(String n, int l, LockType t) {
    name = n;
    line = l;
    type = t;
  }

  public String getName() {
    return name;
  }

  public int getLine() {
    return line;
  }

  @Override
  public boolean equals(Object mutex) {
    if (mutex instanceof LockStatisticsLock)
      return (name.equals(((LockStatisticsLock)mutex).name) &&
          /*line == ((LockStatisticsLock)mutex).line &&*/
          type == ((LockStatisticsLock)mutex).type);
    else
      return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return type.toASTString() + " "+ name + "(" + line + " line)";
  }
}
