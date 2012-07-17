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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

public class LockStatisticsCPAStatistics implements Statistics {

  private Map<String, Set<ActionInfo>> GlobalLockStatistics;
  private Map<String, Set<ActionInfo>> LocalLockStatistics;
  private Map<String, String> NameToType;

 // private Set<String> GlobalCases;
  //private Set<String> LocalCases;

  private Map<String, Set<ActionInfo>> GlobalUnsafeStatistics;
  private Map<String, Set<ActionInfo>> LocalUnsafeStatistics;

  PrintWriter writer = null;
  FileOutputStream file = null;


  public LockStatisticsCPAStatistics(Map<String, Set<ActionInfo>> global,
                                     Map<String, Set<ActionInfo>> local,
                                     Map<String, String> names) {
    GlobalLockStatistics = global;
    LocalLockStatistics = local;

    //GlobalCases = new HashSet<String>();
    //LocalCases = new HashSet<String>();

    NameToType = names;

    try {
      file = new FileOutputStream ("output/race_results.txt");
      writer = new PrintWriter(file);
      writer.close();
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла race_results.txt");
      System.exit(0);
    }
  }

  @Override
  public String getName() {
    return "LockStatisticsCPA";
  }

  private Set<LockStatisticsMutex> FindMutexes() {
    Set<LockStatisticsMutex> mutexes = new HashSet<LockStatisticsMutex>();

    for (String name : GlobalLockStatistics.keySet()) {
      for (ActionInfo action : GlobalLockStatistics.get(name)) {
        for (LockStatisticsMutex mutex : action.getLocks()) {
          if (!mutexes.contains(mutex))
            mutexes.add(mutex);
        }
      }
    }

    for (String name : LocalLockStatistics.keySet()) {
      for (ActionInfo action : LocalLockStatistics.get(name)) {
        for (LockStatisticsMutex mutex : action.getLocks()) {
          if (!mutexes.contains(mutex))
            mutexes.add(mutex);
        }
      }
    }

    return mutexes;
  }

  /*private void FindGlobalUnsafeCases() throws Exception {
    GlobalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();


    Map<Integer, Set<Set<LockStatisticsMutex>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsMutex>>>();

    for (String name : GlobalLockStatistics.keySet()) {
      Cases.clear();

      Set<ActionInfo> Actions = GlobalLockStatistics.get(name);
      Set<Set<LockStatisticsMutex>> DifferentLocks;

      for (ActionInfo action : Actions) {
        if (Cases.containsKey(action.getLine())) {
          DifferentLocks = Cases.get(action.getLine());
          if (!DifferentLocks.contains(action.getLocks())) {
            DifferentLocks.add(action.getLocks());
          }
        }
        else {
          DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
          DifferentLocks.add(action.getLocks());
          Cases.put(action.getLine(), DifferentLocks);
        }
      }



      //System.out.println(Cases.toString());

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

      Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

      boolean isDifferent = false;
      int FirstLine = -1;

      for (ActionInfo action : GlobalLockStatistics.get(name)) {
        if (LocksCount.get(Cases.get(action.getLine())).size() == 1 &&
            LocksCount.size() > 1) {
          UnsafeActions.add(action);
          if (FirstLine == -1) {
            FirstLine = action.getLine();
          }
          else {
            if (FirstLine != action.getLine()) {
              isDifferent = true;
            }
          }
        }
      }
      if (UnsafeActions.size() > 0 && isDifferent){
        GlobalCases.add(name);
        GlobalUnsafeStatistics.put(name, UnsafeActions);
      }
    }
  }

  private void FindLocalUnsafeCases() throws Exception {
    LocalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();

    Map<Integer, Set<Set<LockStatisticsMutex>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsMutex>>>();

    for (String name : LocalLockStatistics.keySet()) {
      Cases.clear();

      Set<ActionInfo> Actions = LocalLockStatistics.get(name);
      Set<Set<LockStatisticsMutex>> DifferentLocks;

      for (ActionInfo action : Actions) {
        if (Cases.containsKey(action.getLine())) {
          DifferentLocks = Cases.get(action.getLine());
          if (!DifferentLocks.contains(action.getLocks())) {
            DifferentLocks.add(action.getLocks());
          }
        }
        else {
          DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
          DifferentLocks.add(action.getLocks());
          Cases.put(action.getLine(), DifferentLocks);
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

      //find most common case

      Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

      boolean isDifferent = false;
      int FirstLine = -1;

      for (ActionInfo action : LocalLockStatistics.get(name)) {
        if (LocksCount.get(Cases.get(action.getLine())).size() == 1 &&
            LocksCount.size() > 1) {
          UnsafeActions.add(action);
          if (FirstLine == -1) {
            FirstLine = action.getLine();
          }
          else {
            if (FirstLine != action.getLine()) {
              isDifferent = true;
            }
          }
        }
      }
      if (UnsafeActions.size() > 0 && isDifferent){
        LocalCases.add(name);
        LocalUnsafeStatistics.put(name, UnsafeActions);
      }
    }
  }*/


  private void FindUnsafeCases() {
    GlobalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();
    LocalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();

    for (String name : GlobalLockStatistics.keySet()) {
      Set<ActionInfo> Actions = GlobalLockStatistics.get(name);
      Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

      for (ActionInfo action : Actions) {
        for (ActionInfo action2 : Actions) {
          if (action.getLine() != action2.getLine() &&
              !action.getLocks().equals(action2.getLocks())) {
            if (!UnsafeActions.contains(action))
              UnsafeActions.add(action);
            if (!UnsafeActions.contains(action2))
              UnsafeActions.add(action2);
          }
        }
      }

      if (UnsafeActions.size() > 0) {
        GlobalUnsafeStatistics.put(name, UnsafeActions);
      }
    }

    for (String name : LocalLockStatistics.keySet()) {
      Set<ActionInfo> Actions = LocalLockStatistics.get(name);
      Set<ActionInfo> UnsafeActions = new HashSet<ActionInfo>();

      for (ActionInfo action : Actions) {
        for (ActionInfo action2 : Actions) {
          if (action.getLine() != action2.getLine() &&
              !action.getLocks().equals(action2.getLocks())) {
            if (!UnsafeActions.contains(action))
              UnsafeActions.add(action);
            if (!UnsafeActions.contains(action2))
              UnsafeActions.add(action2);
          }
        }
      }

      if (UnsafeActions.size() > 0)
        LocalUnsafeStatistics.put(name, UnsafeActions);
    }
  }

  private void FindUnsafeCases2() {
    GlobalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();
    LocalUnsafeStatistics = new HashMap<String, Set<ActionInfo>>();
    Map<Integer, Set<Set<LockStatisticsMutex>>> Cases = new HashMap<Integer, Set<Set<LockStatisticsMutex>>>();

    for (String name : GlobalLockStatistics.keySet()) {
      Cases.clear();

      Set<ActionInfo> Actions = GlobalLockStatistics.get(name);
      Set<Set<LockStatisticsMutex>> DifferentLocks;

      for (ActionInfo action : Actions) {
        if (Cases.containsKey(action.getLine())) {
          DifferentLocks = Cases.get(action.getLine());
          if (!DifferentLocks.contains(action.getLocks())) {
            DifferentLocks.add(action.getLocks());
          }
        }
        else {
          DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
          DifferentLocks.add(action.getLocks());
          Cases.put(action.getLine(), DifferentLocks);
        }
      }

      Set<Integer> LinesToSave = new HashSet<Integer>();

      for (Integer line : Cases.keySet()) {
        for (Integer line2 : Cases.keySet()) {
          if (!Cases.get(line).equals(Cases.get(line2)) && !line.equals(line2)) {
            if (!LinesToSave.contains(line))
              LinesToSave.add(line);
            if (!LinesToSave.contains(line2))
              LinesToSave.add(line2);
          }
        }
      }

      Set<ActionInfo> UnsafeCases = new HashSet<ActionInfo>();

      for (ActionInfo action : Actions){
        if (LinesToSave.contains(action.getLine())) {
          UnsafeCases.add(action);
        }
      }

      if (UnsafeCases.size() > 0) {
        GlobalUnsafeStatistics.put(name, UnsafeCases);
      }
    }


    for (String name : LocalLockStatistics.keySet()) {
      Cases.clear();

      Set<ActionInfo> Actions = LocalLockStatistics.get(name);
      Set<Set<LockStatisticsMutex>> DifferentLocks;

      for (ActionInfo action : Actions) {
        if (Cases.containsKey(action.getLine())) {
          DifferentLocks = Cases.get(action.getLine());
          if (!DifferentLocks.contains(action.getLocks())) {
            DifferentLocks.add(action.getLocks());
          }
        }
        else {
          DifferentLocks = new HashSet<Set<LockStatisticsMutex>>();
          DifferentLocks.add(action.getLocks());
          Cases.put(action.getLine(), DifferentLocks);
        }
      }

      Set<Integer> LinesToSave = new HashSet<Integer>();

      for (Integer line : Cases.keySet()) {
        for (Integer line2 : Cases.keySet()) {
          if (!Cases.get(line).equals(Cases.get(line2)) && !line.equals(line2)) {
            if (!LinesToSave.contains(line))
              LinesToSave.add(line);
            if (!LinesToSave.contains(line2))
              LinesToSave.add(line2);
          }
        }
      }

      Set<ActionInfo> UnsafeCases = new HashSet<ActionInfo>();

      for (ActionInfo action : Actions){
        if (LinesToSave.contains(action.getLine())) {
          UnsafeCases.add(action);
        }
      }

      if (UnsafeCases.size() > 0) {
        LocalUnsafeStatistics.put(name, UnsafeCases);
      }
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    try {
      file = new FileOutputStream ("output/race_results.txt", true);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Ошибка открытия файла race_results.txt");
      System.exit(0);
    }

    //FindSuspiciousCases();
    //try {
      /*if (GlobalLockStatistics.size() > 0)
        FindGlobalUnsafeCases();
      if (LocalLockStatistics.size() > 0)
        FindLocalUnsafeCases();*/
      FindUnsafeCases2();

      writer.println("General statistics");
      writer.println("");
      writer.println("Total variables:        " + (GlobalLockStatistics.keySet().size() +
                                                LocalLockStatistics.keySet().size()));
      writer.println("--Global:               " + GlobalLockStatistics.keySet().size());
      writer.println("--Local:                " + LocalLockStatistics.keySet().size());
      writer.println("");

      Set<LockStatisticsMutex> mutexes = FindMutexes();

      writer.println("Number of used mutexes: " + mutexes.size());

      if (mutexes.size() > 0)
        writer.println("  " + mutexes.toString());

      writer.println("");
      writer.println("Total unsafe cases:     " + (GlobalUnsafeStatistics.size() +
                                                  LocalUnsafeStatistics.size()));
      writer.println("--Global:               " + GlobalUnsafeStatistics.size());
      writer.println("--Local:                " + LocalUnsafeStatistics.size());
      writer.println("");

      if (GlobalUnsafeStatistics.size() > 0){
        writer.println("Global unsafe cases: ");

        for (String name : GlobalUnsafeStatistics.keySet()) {
          if (NameToType.containsKey(name))
            writer.println("  " + NameToType.get(name));
          else
            writer.println("  " + name);

          Set<ActionInfo> tmpActions = GlobalUnsafeStatistics.get(name);

          for (ActionInfo action : tmpActions) {
            writer.println("    " + action.toString());
          }
          writer.println("");
        }

        writer.println("");
        writer.println("");
      }

      if (LocalUnsafeStatistics.size() > 0){
        writer.println("Local unsafe cases: ");

        for (String name : LocalUnsafeStatistics.keySet()) {
          if (NameToType.containsKey(name))
            writer.println("  " + NameToType.get(name));
          else
            writer.println("  " + name);

          Set<ActionInfo> tmpActions = LocalUnsafeStatistics.get(name);

          for (ActionInfo action : tmpActions) {
            writer.println("    " + action.toString());
          }
          writer.println("");
        }

        writer.println("");
      }
      writer.println("");
      writer.println("=============================================");
      writer.println("");
      writer.println("Full statistics");
      writer.println("");
      writer.println("Global variables:");

      for (String name : GlobalLockStatistics.keySet()) {
        if (NameToType.containsKey(name))
          writer.println("  " + NameToType.get(name));
        else
          writer.println("  " + name);

        Set<ActionInfo> tmpActions = GlobalLockStatistics.get(name);

        for (ActionInfo action : tmpActions) {
          writer.println("    " + action.toString());
        }
        writer.println("");
      }

      writer.println("----------------------------------------------");
      writer.println("Local variables:");

      for (String name : LocalLockStatistics.keySet()) {
        if (NameToType.containsKey(name))
          writer.println("  " + NameToType.get(name));
        else
          writer.println("  " + name);

        Set<ActionInfo> tmpActions = LocalLockStatistics.get(name);

        for (ActionInfo action : tmpActions) {
          writer.println("    " + action.toString());
        }
        writer.println("");
      }
   /* }
    catch (Exception e) {
      writer.println("Statistics is unavaliable.");
      e.printStackTrace();
    }
    finally {*/
      if(file != null)
        writer.close();
    //}
  }
}
