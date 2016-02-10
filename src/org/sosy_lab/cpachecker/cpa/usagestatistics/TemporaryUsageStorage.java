/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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

import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;


public class TemporaryUsageStorage extends TreeMap<SingleIdentifier, LinkedList<UsageInfo>> {
  private static final long serialVersionUID = -8932709343923545136L;
  private TemporaryUsageStorage previousStorage;

  private Set<SingleIdentifier> deeplyCloned = new TreeSet<>();

  private Set<UsageInfo> withoutARGState = new TreeSet<>();

  /*static int posCounter = 0;
  static int listCounterByPut = 0;
  static int listCounterByGet = 0;*/

  public TemporaryUsageStorage(TemporaryUsageStorage previous) {
    super(previous);
    previousStorage = previous;
  }

  public TemporaryUsageStorage() {
    previousStorage = null;
  }

  public boolean add(SingleIdentifier id, UsageInfo info) {
    LinkedList<UsageInfo> storage = getStorageForId(id);
    if (info.getKeyState() == null) {
      withoutARGState.add(info);
    }
    /*posCounter++;
    if (posCounter % 1000 == 0) {
      System.out.println("Usage positions: " + posCounter);
    }*/
    return storage.add(info);
  }

  @Override
  public LinkedList<UsageInfo> put(SingleIdentifier id, LinkedList<UsageInfo> list) {
    deeplyCloned.add(id);
    /*listCounterByPut++;
    if (listCounterByPut % 100 == 0) {
      System.out.println("Sets number by put: " + listCounterByPut);
    }*/
    return super.put(id, list);
  }

  public boolean addAll(SingleIdentifier id, LinkedList<UsageInfo> list) {
    LinkedList<UsageInfo> storage = getStorageForId(id);
    /*posCounter+=list.size();
    if (posCounter % 1000 == 0) {
      System.out.println("Usage positions: " + posCounter);
    }*/
    return storage.addAll(list);
  }

  private LinkedList<UsageInfo> getStorageForId(SingleIdentifier id) {
    if (deeplyCloned.contains(id)) {
      //List is already cloned
      assert this.containsKey(id);
      return this.get(id);
    } else {
      deeplyCloned.add(id);
      LinkedList<UsageInfo> storage;
      if (this.containsKey(id)) {
        //clone
        //posCounter+=this.get(id).size();
        storage = new LinkedList<>(this.get(id));
      } else {
        storage = new LinkedList<>();
      }
      /*listCounterByGet++;
      if (listCounterByGet % 100 == 0) {
        System.out.println("Sets number by get: " + listCounterByGet);
      }*/
      this.put(id, storage);
      return storage;
    }
  }

  public void setKeyState(ARGState state) {
    for (UsageInfo uinfo : withoutARGState) {
      uinfo.setKeyState(state);
    }
    withoutARGState.clear();
  }

  public void cleanUsages() {
    super.clear();
    //We can't use recursion due to stack overflow
    TemporaryUsageStorage previous = previousStorage, tmpStorage;
    while (previous != null) {
      previous.clear();
      tmpStorage = previous.previousStorage;
      previous.previousStorage = null;
      previous = tmpStorage;
    }
  }

  @Override
  public void clear() {
    super.clear();
    deeplyCloned.clear();
  }
}
