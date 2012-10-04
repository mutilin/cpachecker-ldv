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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.cpa.usageStatistics.VariableIdentifier.Ref;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {
  private Map<VariableIdentifier, Set<UsageInfo>> Stat;
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
    Stat = new HashMap<VariableIdentifier, Set<UsageInfo>>();
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

  public void add(List<Pair<VariableIdentifier, Access>> result, UsageStatisticsState state, int line, EdgeType type) {
    Set<UsageInfo> uset;
    VariableIdentifier id;

    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(state, CallstackState.class);

    LineInfo lineInfo = new LineInfo(line);
    EdgeInfo info = new EdgeInfo(type);

    for (Pair<VariableIdentifier, Access> tmpPair : result) {
      FullCounter++;
      id = tmpPair.getFirst();
      while (id != null && id.getStatus() == Ref.REFERENCE && state.contains(id.makeVariable())) {
        id = state.get(id.makeVariable());
      }
      if (id == null) {
        skippedCases++;
        continue;
      }
      UsageInfo usage = new UsageInfo(tmpPair.getSecond(), lineInfo, info, lockState, callstackState);

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

  /*
   * looks through all unsafe cases of current identifier and find the example of two lines with different locks, one of them must be 'write'
   */
  private Pair<UsageInfo, UsageInfo> findExamples(Identifier unsafeCase) throws HandleCodeException {
    Set<UsageInfo> uinfo = Stat.get(unsafeCase);

    for (UsageInfo info1 : uinfo) {
      for (UsageInfo info2 : uinfo) {
        if ((info1.getAccess() == Access.WRITE || info2.getAccess() == Access.WRITE) && !info1.intersect(info2)) {
          return Pair.of(info1, info2);
        }
      }
    }
    throw new HandleCodeException("Can't find example of unsafe cases");
  }

private void printCases(String comment, Collection<VariableIdentifier> identifiers) {
  writer.println("-------------------------------------------------");
  writer.println("");
  Collection<VariableIdentifier> global = new HashSet<VariableIdentifier>();
  Collection<VariableIdentifier> local = new HashSet<VariableIdentifier>();
  Collection<VariableIdentifier> fields = new HashSet<VariableIdentifier>();
  //Collection<Identifier> alreadyPrinted = new HashSet<Identifier>();


  for (VariableIdentifier id : identifiers) {
    if (id.getStatus() == Ref.VARIABLE || !identifiers.contains(id.makeVariable())) {
      if (id instanceof GlobalVariableIdentifier)
        global.add(id);
      else if (id instanceof LocalVariableIdentifier)
        local.add(id);
     /* else if (id instanceof StructureIdentifier)
        structures++;*/
      else if (id instanceof StructureFieldIdentifier)
        fields.add(id);
    }
  }

  writer.println(comment);
  writer.println("Total cases:            " + (global.size() + local.size() + fields.size()));
  writer.println("--Global:               " + global.size());
  writer.println("--Local:                " + local.size());
  //writer.println("--Structures:           " + structures);
  writer.println("--Structure fields:     " + fields.size());
  writer.println("");
  writer.println("-------------------------------------------------");

  int counter = 1;
  counter = printCollection("Global variables", global, counter, identifiers);
  counter = printCollection("Local variables", local, counter, identifiers);
  counter = printCollection("Structure fields", fields, counter, identifiers);
}
  private int printCollection(String description, Collection<VariableIdentifier> identifiers, int counter,
                              Collection<VariableIdentifier> allIdentifiers) {
    if (identifiers.size() > 0) {
      writer.println("");
      writer.println(description);
      writer.println("________________");
      for (VariableIdentifier id : identifiers) {
        writer.println("");
        writer.println(counter + ") "+ id.toString());
        printId(id, allIdentifiers.contains(id.makeReference()));
        counter++;
        writer.println("_____________________________________________");
      }
      writer.println("---------------------------------------------");
      writer.println("");
    }
    return counter;
  }

  private void printExample(VariableIdentifier id) {
    Pair<UsageInfo, UsageInfo> example;

    writer.println("    |- Two examples:");
    try {
      example = findExamples(id);
      writer.println("    " + example.getFirst().toString());
      writer.println("    " + example.getSecond().toString());
    } catch (HandleCodeException e) {
      writer.println(e.getMessage());
    }
  }

  private void printId(VariableIdentifier id, boolean ref) {
    writer.println("    |- Unique usages: " + Stat.get(id).size());
    printExample(id);
    writer.println("    [");
    writer.println("    ");
    for (UsageInfo uinfo : Stat.get(id))
      writer.println(uinfo.toString());
    writer.println("    ]");

    if (ref && id.getStatus() == Ref.VARIABLE) {
      VariableIdentifier refId = id.makeReference();
      writer.println("");
      writer.println("    " + refId.getName());
      writer.println("    |- Unique usages: " + Stat.get(refId).size());
      printExample(id);
      writer.println("    [");
      writer.println("    ");
      for (UsageInfo uinfo : Stat.get(refId))
        writer.println(uinfo.toString());
      writer.println("    ]");
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    Collection<VariableIdentifier> global = new HashSet<VariableIdentifier>();
    Collection<VariableIdentifier> local = new HashSet<VariableIdentifier>();
    Collection<VariableIdentifier> fields = new HashSet<VariableIdentifier>();

    try {
      file = new FileOutputStream (FileName);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + FileName);
      return;
    }

    int counter = 0;

    for (VariableIdentifier id : Stat.keySet()) {
      counter += Stat.get(id).size();

      if (id.getStatus() == Ref.VARIABLE || !Stat.keySet().contains(id.makeVariable())) {
        if (id instanceof GlobalVariableIdentifier)
        global.add(id);
      else if (id instanceof LocalVariableIdentifier)
        local.add(id);
      else if (id instanceof StructureFieldIdentifier)
        fields.add(id);
      }
    }

    writer.println("General statistics");
    writer.println("");
    writer.println("Total variables:        " + (global.size() + local.size() + fields.size()));
    writer.println("--Global:               " + global.size());
    writer.println("--Local:                " + local.size());
    //writer.println("--Structures:           " + structures);
    writer.println("--Structure fields:     " + fields.size());
    writer.println("");

    writer.println("Total usages:           " + FullCounter);
    writer.println("Total unique usages:    " + counter);
    writer.println("Total skipped cases:    " + skippedCases);
    writer.println("");

    Set<LockStatisticsLock> mutexes = FindMutexes();

    writer.println("Number of used mutexes: " + mutexes.size());

    if (mutexes.size() > 0)
      writer.println("  " + mutexes.toString());


    Collection<VariableIdentifier> unsafeCases = dataProcess.process(Stat);
    printCases(dataProcess.getDescription(), unsafeCases);

    /*unsafeCases = FindDifferentSets(Stat);
    printUnsafeCases("Lines with different sets of mutexes were printed", unsafeCases, true);
*/

    if (fullstatistics) {
      printCases("Full statistics", Stat.keySet());
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
