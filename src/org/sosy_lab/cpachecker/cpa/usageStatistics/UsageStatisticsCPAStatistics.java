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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
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
  private LinkedList<Block> BlockStack;

  PrintWriter writer = null;
  FileOutputStream file = null;
  DataProcessing dataProcess = null;
  CodeCovering covering;

  @Option(name="output", description="directory to write results")
  private String DirName = "test/";
  private String VisualName = "visualize";

  //private String OrigName;

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

    VisualName = DirName + VisualName;
    //OrigName = config.getProperty("cpa.usagestatistics.path");
    //reducer = pReducer;
  }

  public void add(List<Pair<VariableIdentifier, Access>> result, UsageStatisticsState state, int line, EdgeType type) throws HandleCodeException {
    Set<UsageInfo> uset;
    VariableIdentifier id;

    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(state, CallstackState.class);

    callstackState = createStack(callstackState);

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

  private Set<LockStatisticsLock> FindMutexes() {
    Set<LockStatisticsLock> locks = new HashSet<LockStatisticsLock>();

    for (Identifier id : Stat.keySet()) {
      Set<UsageInfo> uset = Stat.get(id);
      for (UsageInfo uinfo : uset){
        for (LockStatisticsLock lock : uinfo.getLockState().getLocks()) {
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
  /*private Pair<UsageInfo, UsageInfo> findExamples(Identifier unsafeCase) throws HandleCodeException {
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
      else if (id instanceof StructureIdentifier)
        structures++;
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
      writer.println(example.getFirst().toString());
      writer.println(example.getSecond().toString());
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
  }*/

  private void createVisualization(VariableIdentifier id) {
    Set<UsageInfo> uinfo = Stat.get(id);
    LinkedList<CallstackState> tmpList = new LinkedList<CallstackState>();
    LinkedList<TreeLeaf> leafStack = new LinkedList<TreeLeaf>();
    TreeLeaf tmpLeaf, currentLeaf;
    CallstackState tmpState;



    if (uinfo == null || uinfo.size() == 0)
      return;
    if (id instanceof StructureFieldIdentifier)
      writer.println("###" + id.getSimpleName());
    else if (id instanceof GlobalVariableIdentifier)
      writer.println("#" + id.getSimpleName());
    else if (id instanceof LocalVariableIdentifier)
      writer.println("##" + id.getSimpleName() + "_" + ((LocalVariableIdentifier)id).getFunction());
    writer.println(id.type.toASTString(id.getSimpleName()));
    writer.println("Line 0:     N0 -{/*Number of usages:" + uinfo.size() + "*/}-> N0");
    for (UsageInfo ui : uinfo) {
      LockStatisticsState Locks = ui.getLockState();
      currentLeaf = TreeLeaf.clearTrunkState();
      for (LockStatisticsLock lock : Locks.getLocks()) {
        currentLeaf = TreeLeaf.getTrunkState();
        tmpState = lock.getCallstack();
        tmpList.clear();
        //revert callstacks of locks
        while (tmpState != null) {
          tmpList.push(tmpState);
          tmpState = tmpState.getPreviousState();
        }
        //create tree of calls for locks
        currentLeaf = currentLeaf.add(tmpList.getFirst().getCallNode().getFunctionName(), 0);
        for (CallstackState callstack : tmpList) {
          currentLeaf = currentLeaf.add(callstack);
        }
        //System.out.println("Add " + lock.getName());
        currentLeaf.add(lock.getName() + "()", lock.getLine().line);
      }

      tmpState = ui.getCallStack();
      tmpList.clear();
      //revert call stack of error trace to variable
      while (tmpState != null) {
        tmpList.push(tmpState);
        tmpState = tmpState.getPreviousState();
      }
      //add to tree of calls this path
      currentLeaf = TreeLeaf.getTrunkState();
      currentLeaf = currentLeaf.add(tmpList.getFirst().getCallNode().getFunctionName(), 0);
      for (CallstackState callstack : tmpList) {
        currentLeaf = currentLeaf.addLast(callstack);
      }
      String name = id.getName();
      if (ui.getEdgeInfo().getEdgeType() == EdgeType.ASSIGNMENT) {
        if (ui.getAccess() == Access.READ) {
          name = "... = " + name + ";";
        } else if (ui.getAccess() == Access.WRITE) {
          name += " = ...;";
        }
      } else if (ui.getEdgeInfo().getEdgeType() == EdgeType.ASSUMPTION) {
        name = "if ("  + name + ") {}";
      } else if (ui.getEdgeInfo().getEdgeType() == EdgeType.FUNCTION_CALL) {
        name = "f("  + name + ");";
      } else if (ui.getEdgeInfo().getEdgeType() == EdgeType.DECLARATION) {
        name = id.type.toASTString(name);
      }
      currentLeaf.addLast(name, ui.getLine().line);

      //print this tree with aide of dfs
      currentLeaf = TreeLeaf.getTrunkState();
      leafStack.clear();
      if (currentLeaf.children.size() > 0) {
        leafStack.push(currentLeaf);
        currentLeaf = currentLeaf.children.getFirst();
      } else {
        //strange, but we don't have any stacks
        continue;
      }
      writer.println("Line 0:     N0 -{/*_____________________*/}-> N0");
      writer.println("Line 0:     N0 -{/*" + ui.getLockState().toString() + "*/}-> N0");
      while (currentLeaf != null) {
        if (currentLeaf.children.size() > 0) {
          writer.println("Line " + currentLeaf.line + ":     N0 -{" + currentLeaf.code + "();}-> N0");
          writer.println("Line 0:     N0 -{Function start dummy edge}-> N0");
          leafStack.push(currentLeaf);
          currentLeaf = currentLeaf.children.getFirst();
        } else {
          writer.println("Line " + currentLeaf.line + ":     N0 -{" + currentLeaf.code + "}-> N0");
          while (true) {
            tmpLeaf = leafStack.pop();
            if (tmpLeaf.equals(TreeLeaf.getTrunkState())) {
              currentLeaf = null;
              break;
            }
            if (tmpLeaf.children.size() > 1  && !tmpLeaf.children.getLast().equals(currentLeaf)) {
              leafStack.push(tmpLeaf);
              currentLeaf = tmpLeaf.children.get(tmpLeaf.children.indexOf(currentLeaf) + 1);
              break;
            }
            writer.println("Line 0:     N0 -{return;}-> N0");
            currentLeaf = tmpLeaf;
          }
        }
      }
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {

    /*Collection<GlobalVariableIdentifier> global = new HashSet<GlobalVariableIdentifier>();
    Collection<LocalVariableIdentifier> local = new HashSet<LocalVariableIdentifier>();
    Collection<StructureFieldIdentifier> fields = new HashSet<StructureFieldIdentifier>();*/
    int global = 0, local = 0, fields = 0, pointers = 0;

    try {
      file = new FileOutputStream (VisualName);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + VisualName);
      return;
    }

    int counter = 0;

    for (VariableIdentifier id : Stat.keySet()) {
      counter += Stat.get(id).size();

      if (id.getStatus() == Ref.VARIABLE || !Stat.keySet().contains(id.makeVariable())) {
        if (id instanceof GlobalVariableIdentifier)
        //global.add((GlobalVariableIdentifier)id);
          global++;
        else if (id instanceof LocalVariableIdentifier)
        //local.add((LocalVariableIdentifier)id);
          local++;
        else if (id instanceof StructureFieldIdentifier)
        //fields.add((StructureFieldIdentifier)id);
          fields++;
        }
      else {
        pointers++;
      }
    }

    writer.println(global);
    writer.println(local);
    //writer.println("--Structures:           " + structures);
    writer.println(fields);
    writer.println(pointers);

    writer.println(FullCounter);
    writer.println(counter);
    writer.println(skippedCases);

    Set<LockStatisticsLock> mutexes = FindMutexes();

    writer.println(mutexes.size());

    for (LockStatisticsLock lock : mutexes) {
      writer.println(lock.toString());
    }

    Collection<VariableIdentifier> unsafeCases = dataProcess.process(Stat);
    counter = global = local = fields = pointers = 0;

    for (VariableIdentifier id : unsafeCases) {
      counter += Stat.get(id).size();

      if (id.getStatus() == Ref.VARIABLE || !Stat.keySet().contains(id.makeVariable())) {
        if (id instanceof GlobalVariableIdentifier)
        //global.add((GlobalVariableIdentifier)id);
          global++;
        else if (id instanceof LocalVariableIdentifier)
        //local.add((LocalVariableIdentifier)id);
          local++;
        else if (id instanceof StructureFieldIdentifier)
        //fields.add((StructureFieldIdentifier)id);
          fields++;
        }
      else {
        pointers++;
      }
    }
    writer.println(global + local + fields + pointers);
    writer.println(counter);
    writer.println(global);
    writer.println(local);
    //writer.println("--Structures:           " + structures);
    writer.println(fields);
    writer.println(pointers);

    //printCases(dataProcess.getDescription(), unsafeCases);

    for (VariableIdentifier id : unsafeCases) {
      createVisualization(id);
    }

    if(file != null)
      writer.close();

    covering.generate();
  }

  @Override
  public String getName() {
    return "UsageStatisticsCPA";
  }

  public CallstackState createStack(CallstackState state) throws HandleCodeException {
    CallstackState fullState = null, tmpState;
    CFANode currentNode, previousNode, predecessor;
    CFAEdge edge;

    previousNode = null;
    for (int i = 0; i < BlockStack.size(); i++) {
      currentNode = BlockStack.get(i).getCallNode();
      predecessor = currentNode;
      for (int j = 0; j < currentNode.getNumEnteringEdges(); j++) {
        edge = currentNode.getEnteringEdge(j);
        predecessor = edge.getPredecessor();
        if (previousNode == null || predecessor.getFunctionName().equals(previousNode.getFunctionName())) break;
      }
      fullState = new CallstackState(fullState, currentNode.getFunctionName(), predecessor);
      previousNode = currentNode;
    }
    CallstackState newState = state.clone();
    tmpState = newState;
    if (fullState != null) {
      if (tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() == null)
        return fullState;
      else if (!tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() != null) {
        while (!tmpState.getPreviousState().getCurrentFunction().equals(fullState.getCurrentFunction()))
          tmpState = tmpState.getPreviousState();
        tmpState.setPreviousState(fullState);
        return newState;
      } else if (tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() != null) {
        return fullState;
      } else /*if (!tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() == null)*/ {
        throw new HandleCodeException("Strange situation in creating call stack");
      }
    } else {
      return newState;
    }
  }

  public void setBlockStack(LinkedList<Block> stack) {
    BlockStack = stack;
  }
}
