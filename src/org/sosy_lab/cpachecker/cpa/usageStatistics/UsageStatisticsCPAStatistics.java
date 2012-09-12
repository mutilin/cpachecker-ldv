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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {
  private Map<Identifier, Set<UsageInfo>> Stat;
  private int FullCounter = 0;
  private int skippedCases = 0;

  PrintWriter writer = null;
  FileOutputStream file = null;
  DataProcessing dataProcess = null;
  CodeCovering covering;

  @Option(name="output", description="file to write results")
  private String FileName = "race_results.txt";

  @Option(values={"SIMPLE", "SET"},toUppercase=true,
      description="which data process we should use")
  private String process = "SIMPLE";

  @Option(description="if we need to print all variables, not only unsafe cases")
  private boolean fullstatistics = true;

  @Option(description="Do we need to store statistics of all variables or only pointers")
  private boolean onlypointers = true;

  UsageStatisticsCPAStatistics(Configuration config, CodeCovering cover) throws InvalidConfigurationException{
    Stat = new HashMap<Identifier, Set<UsageInfo>>();
    config.inject(this);

    if (process.equals("SIMPLE"))
      dataProcess = new DataProcessSimple();
    else if (process.equals("SET"))
      dataProcess = new DataProcessSetAnalysis();
    else {
      System.out.println("Unknown data procession " + process);
      System.exit(0);
    }
    covering = cover;

  }

  public void add(Set<Pair<Identifier, Access>> result, AbstractState state, int line, EdgeType type) {
    Set<UsageInfo> uset;
    Identifier id;

    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(state, CallstackState.class);

    LineInfo lineInfo = new LineInfo(line);
    EdgeInfo info = new EdgeInfo(type);

    for (Pair<Identifier, Access> tmpPair : result) {
      FullCounter++;
      UsageInfo usage = new UsageInfo(tmpPair.getSecond(), lineInfo, info, lockState, callstackState);
      id = tmpPair.getFirst();
      if (id == null) {
        skippedCases++;
        continue;
      }

      if (!Stat.containsKey(id)) {
        uset = new HashSet<UsageInfo>();
        Stat.put(id, uset);
      } else {
        uset = Stat.get(id);
      }
      uset.add(usage);
    }
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

  private Set<LockStatisticsLock> FindMutexes() {
    Set<LockStatisticsLock> locks = new HashSet<LockStatisticsLock>();

    for (Identifier id : Stat.keySet()) {
      Set<UsageInfo> uset = Stat.get(id);
      for (UsageInfo uinfo : uset){
        for (LockStatisticsLock lock : uinfo.getLocks()) {
          if (!locks.contains(lock))
            locks.add(lock);
        }
      }
    }

    return locks;
  }

  private void printUnsafeCases(String comment, Collection<Identifier> identifiers, boolean details) {
  writer.println("-------------------------------------------------");
  writer.println("");

  int local = 0, global = 0,/* structures = 0,*/ fields = 0;

  for (Identifier id : identifiers) {
    if (id instanceof GlobalVariableIdentifier)
      global++;
    else if (id instanceof LocalVariableIdentifier)
      local++;
   /* else if (id instanceof StructureIdentifier)
      structures++;*/
    else if (id instanceof StructureFieldIdentifier)
      fields++;
  }

  writer.println(comment);
  writer.println("Total unsafe cases:     " + identifiers.size());
  writer.println("--Global:               " + global);
  writer.println("--Local:                " + local);
  //writer.println("--Structures:           " + structures);
  writer.println("--Structure fields:     " + fields);
  writer.println("");

  if (details && identifiers.size() > 0){
    int counter = 1;
    for (Identifier id : identifiers) {
      writer.println("");
      writer.println(counter + ") "+ id.toString());
      writer.println("    |- Unique usages: " + Stat.get(id).size());
      writer.println("    [");
      for (UsageInfo uinfo : Stat.get(id))
        writer.println(uinfo.toString());
      writer.println("    ]");
      counter++;
      writer.println("_____________________________________________");
    }
    writer.println("");
  }
}


  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    int global = 0, local = 0, counter = 0,/* structures = 0,*/ fields = 0;

    try {
      file = new FileOutputStream (FileName);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + FileName);
      System.exit(0);
    }

    for (Identifier id : Stat.keySet()){
      Set<UsageInfo> uset = Stat.get(id);

      counter += uset.size();
      if (id instanceof GlobalVariableIdentifier)
        global++;
      else if (id instanceof LocalVariableIdentifier)
        local++;
      /*else if (id instanceof StructureIdentifier)
        structures++;*/
      else if (id instanceof StructureFieldIdentifier)
        fields++;
    }

    writer.println("General statistics");
    writer.println("");
    writer.println("Total variables:        " + Stat.size());
    writer.println("--Global:               " + global);
    writer.println("--Local:                " + local);
    //writer.println("--Structures:           " + structures);
    writer.println("--Structure fields:     " + fields);
    writer.println("");

    writer.println("Total usages:           " + FullCounter);
    writer.println("Total unique usages:    " + counter);
    writer.println("Total skipped cases:    " + skippedCases);
    writer.println("");

    Set<LockStatisticsLock> mutexes = FindMutexes();

    writer.println("Number of used mutexes: " + mutexes.size());

    if (mutexes.size() > 0)
      writer.println("  " + mutexes.toString());


    Collection<Identifier> unsafeCases = dataProcess.process(Stat);
    printUnsafeCases(dataProcess.getDescription(), unsafeCases, true);

    /*unsafeCases = FindDifferentSets(Stat);
    printUnsafeCases("Lines with different sets of mutexes were printed", unsafeCases, true);
*/

    if (fullstatistics) {
      writer.println("");
      writer.println("=============================================");
      writer.println("");
      writer.println("Full statistics");
      writer.println("");
      counter = 1;
      for (Identifier id : Stat.keySet()) {
        writer.println("");
        writer.println(counter + ") " + id.toString());
        writer.println("    |- Unique usages: " + Stat.get(id).size());
        writer.println("      [");
        for (UsageInfo uinfo : Stat.get(id))
          writer.println(uinfo.toString());
        writer.println("      ]");
        counter++;
        writer.println("_____________________________________________");
      }
      writer.println("");
    }

    if(file != null)
      writer.close();

    covering.generate();
  }

  @Override
  public String getName() {
    return "UsageStatisticsCPA";
  }
}
