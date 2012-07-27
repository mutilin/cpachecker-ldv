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

import java.io.PrintStream;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

public class LockStatisticsCPAStatistics implements Statistics {

  public LockStatisticsCPAStatistics() { }

  @Override
  public String getName() {
    return "LockStatisticsCPA";
  }

  /*private Collection<VariableInfo> FindUnsafeCases(Collection<VariableInfo> locks) {
    Map<Integer, Set<Set<LockStatisticsMutex>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsMutex>>>();

    Collection<VariableInfo> unsafe = new HashSet<VariableInfo>();

    for (VariableInfo var : locks) {
      VariableInfo UnsafeTypes = new VariableInfo(var.getName());

      for (String type : var.keySet()) {
        TypeInfo typeInfo = var.get(type);

        Cases.clear();
        Set<Set<LockStatisticsMutex>> DifferentLocks;

        for (LineInfo line : typeInfo.getLines()) {
          if (Cases.containsKey(line.getLine())) {
            DifferentLocks = Cases.get(line.getLine());
            if (!DifferentLocks.contains(line.getLocks())) {
              DifferentLocks.add(line.getLocks());
            }
          }
          else {
            DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
            DifferentLocks.add(line.getLocks());
            Cases.put(line.getLine(), DifferentLocks);
          }
        }

        Map<Set<Set<LockStatisticsMutex>>, Set<Integer>> LocksCount = new HashMap<Set<Set<LockStatisticsMutex>>, Set<Integer>>();

        for (Integer line : Cases.keySet()) {
          DifferentLocks = Cases.get(line);
          if (!LocksCount.containsKey(DifferentLocks)) {
            Set<Integer> lines = new HashSet<Integer>();
            lines.add(line);
            LocksCount.put(DifferentLocks, lines);
          }
          else {
            Set<Integer> lines = LocksCount.get(DifferentLocks);
            lines.add(line);
          }
        }

        TypeInfo UnsafeLines = UnsafeTypes.new TypeInfo(type);

        boolean isDifferent = false;
        int FirstLine = -1;

        for (LineInfo line : typeInfo.getLines()) {
          if (LocksCount.get(Cases.get(line.getLine())).size() == 1 &&
            LocksCount.size() > 1) {
            UnsafeLines.add(line);
            if (FirstLine == -1) {
              FirstLine = line.getLine();
            }
            else {
              if (FirstLine != line.getLine()) {
                isDifferent = true;
              }
            }
          }
        }
        if (UnsafeLines.size() > 0 && isDifferent){
          UnsafeTypes.add(UnsafeLines);
        }
      }
      if (UnsafeTypes.size() > 0) {
        unsafe.add(UnsafeTypes);
      }
    }

    return unsafe;
  }

  /*private Map<String, Set<ActionInfo>> FindUnsafeCases3(Map<String, Set<ActionInfo>> locks) {

    Map<String, Set<ActionInfo>> unsafe = new HashMap<String, Set<ActionInfo>>();
    Map<Set<LockStatisticsMutex>, Set<ActionInfo>> MutexToAction =
      new HashMap<Set<LockStatisticsMutex>, Set<ActionInfo>>();

    for (String name : locks.keySet()) {
      MutexToAction.clear();
      Set<ActionInfo> Actions = locks.get(name);

      for (ActionInfo action : Actions) {
        if (!MutexToAction.containsKey(action.getLocks())) {
          Set<ActionInfo> lines = new HashSet<ActionInfo>();
          lines.add(action);
          MutexToAction.put(action.getLocks(), lines);
        }
        else {
          Set<ActionInfo> lines = MutexToAction.get(action.getLocks());
          if (!lines.contains(action.getLine()))
            lines.add(action);
        }
      }

      Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

      for (Set<LockStatisticsMutex> mutexes : MutexToAction.keySet()) {
        if (MutexToAction.get(mutexes).size() == 1 && MutexToAction.size() > 1) {
          for (ActionInfo action : MutexToAction.get(mutexes)) {
            //only one mutex is here
            UnsafeActions.add(action);
          }
        }
      }


      //check all unsafe actions
      Boolean isDifferent = false;
      Set<ActionInfo> ToRemove = new HashSet<ActionInfo>();
      for (ActionInfo action : UnsafeActions) {
        for (ActionInfo action2 : locks.get(name)) {
          if (action.getLine() != action2.getLine()) {
            isDifferent = true;
            break;
          }
        }
        if (!isDifferent)
          ToRemove.add(action);
      }

      if (ToRemove.size() > 0) {
        for (ActionInfo action : ToRemove){
          UnsafeActions.remove(action);
        }
      }
      if (UnsafeActions.size() > 0) {
        unsafe.put(name, UnsafeActions);
      }
    }
    return unsafe;
  }*/

  /*private void printUnsafeCases(String comment, boolean details) {
    writer.println("-------------------------------------------------");
    writer.println("");
    writer.println(comment);
    writer.println("");
    writer.println("Total unsafe cases:     " + (GlobalUnsafeStatistics.size() +
                                                LocalUnsafeStatistics.size()));
    writer.println("--Global:               " + GlobalUnsafeStatistics.size());
    writer.println("--Local:                " + LocalUnsafeStatistics.size());
    writer.println("");

    if (GlobalUnsafeStatistics.size() > 0 && details){
      writer.println("Global unsafe cases: ");

      for (VariableInfo var : GlobalUnsafeStatistics) {
        writer.println(var.toString());
      }

      writer.println("");
    }

    if (LocalUnsafeStatistics.size() > 0 && details){
      writer.println("Local unsafe cases: ");

      for (VariableInfo var : LocalUnsafeStatistics) {
        writer.println(var.toString());
      }

      writer.println("");
    }
  }*/

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    /*boolean NeedForDetails = true;

    writer.println("General statistics");
    writer.println("");
    writer.println("Total variables:        " + (GlobalLockStatistics.size() +
                                                LocalLockStatistics.size()));
    writer.println("--Global:               " + GlobalLockStatistics.size());
    writer.println("--Local:                " + LocalLockStatistics.size());
    writer.println("");

    Set<LockStatisticsMutex> mutexes = FindMutexes();

    writer.println("Number of used mutexes: " + mutexes.size());

    if (mutexes.size() > 0)
      writer.println("  " + mutexes.toString());

    /*GlobalUnsafeStatistics = FindUnsafeCases(GlobalLockStatistics);
    LocalUnsafeStatistics = FindUnsafeCases(LocalLockStatistics);
    printUnsafeCases("Prints actions, that have unique set of mutexes", NeedForDetails);

    GlobalUnsafeStatistics = FindUnsafeCases1(GlobalLockStatistics);
    LocalUnsafeStatistics = FindUnsafeCases1(LocalLockStatistics);
    printUnsafeCases("Prints actions, that have (different lines & mutexes) & equal types",NeedForDetails);

    GlobalUnsafeStatistics = FindUnsafeCases2(GlobalUnsafeStatistics);
    LocalUnsafeStatistics = FindUnsafeCases2(LocalUnsafeStatistics);
    printUnsafeCases("Prints actions, that have (different lines & set of mutexes) & equal types",NeedForDetails);

    /*GlobalUnsafeStatistics = FindUnsafeCases3(GlobalLockStatistics);
    LocalUnsafeStatistics = FindUnsafeCases3(LocalLockStatistics);
    printUnsafeCases("Prints actions, that have unique mutexes",true);

    writer.println("");
    writer.println("=============================================");
    writer.println("");
    writer.println("Full statistics");
    writer.println("");
    writer.println("Global variables:");
    for (VariableInfo var : GlobalLockStatistics) {
      writer.println(var.toString());
      writer.println("");
    }
    writer.println("----------------------------------------------");
    writer.println("Local variables:");
    for (VariableInfo var : LocalLockStatistics) {
      writer.println(var.toString());
    }
    if(file != null)
      writer.close();*/
  }
}
