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

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.AccessPoint;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {

  public Map<SingleIdentifier, UsageSet> Stat;

  private UnsafeDetector unsafeDetector = null;

  @Option(name="localanalysis", description="should we use local analysis?")
  private boolean localAnalysis = false;

  @Option(name="output", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  /*@Option(description = "variables, which will not be saved in statistics")
  private Set<String> skippedvariables = null;

  @Option(values={"PAIR", "SETDIFF"},toUppercase=true,
      description="which data process we should use")
  private String unsafeDetectorType = "PAIR";*/

  @Option(description="print all unsafe cases in report")
  private boolean printAllUnsafeUsages = false;

  private final LogManager logger;
  //public final Set<SingleIdentifier> unsafes = new HashSet<>();

  public Timer transferRelationTimer = new Timer();
  public Timer printStatisticsTimer = new Timer();

  /*Timer full = new Timer();
  Timer one = new Timer();
  Timer two = new Timer();

  public void addTmp(SingleIdentifier id, EdgeInfo.EdgeType e, int line) {
    int i, j;
    if (id instanceof GlobalVariableIdentifier) {
      i = 0;
    } else if (id instanceof LocalVariableIdentifier) {
      i = 1;
    } else if (id instanceof StructureIdentifier) {
      i = 2;
    } else {
      System.err.println("What is the type of identifier: " + id.toString());
      return;
    }
    switch (e) {
      case ASSIGNMENT:
        j = 0;
        break;
      case ASSUMPTION:
        j = 1;
        break;
      case FUNCTION_CALL:
        j = 2;
        break;
      default:
        System.err.println("What is the type of usage: " + e);
        return;
    }
    if (i == 1 && j == 0) {
      System.out.println(line);
    }
    counter[i][j]++;
  }*/

  public UsageStatisticsCPAStatistics(Configuration config, LogManager pLogger) throws InvalidConfigurationException{
    //Stat = new HashMap<>();
    config.inject(this);
    unsafeDetector = new PairwiseUnsafeDetector(config);
    /*if (unsafeDetectorType.equals("PAIR")) {

    } else if (unsafeDetectorType.equals("SETDIFF")) {
      unsafeDetector = new SetDifferenceUnsafeDetector(config);
    } else {
      throw new InvalidConfigurationException("Unknown data procession " + unsafeDetectorType);
    }*/
    logger = pLogger;
  }

  /*public void add(Pair<SingleIdentifier, Pair<UsageInfo, UsageInfo>> unsafe) throws HandleCodeException {
    SingleIdentifier id = unsafe.getFirst();

    if (skippedvariables != null && skippedvariables.contains(id.getName())) {
      return;
    } else if (skippedvariables != null && id instanceof StructureIdentifier) {
      AbstractIdentifier owner = id;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (owner instanceof SingleIdentifier && skippedvariables.contains(((SingleIdentifier)owner).getName())) {
          return;
        }
      }
    }
    List<UsageInfo> uset;

    if (unsafes.keySet().contains(id)) {
      return;
    }

    if (!Stat.containsKey(id)) {
      uset = new LinkedList<>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
    }
    uset.add(unsafe.getSecond().getFirst());
    uset.add(unsafe.getSecond().getSecond());
    System.out.println("Add unsafe: " + id + ", " + unsafes.size());
    unsafes.add(id);
  }*/

  /*public void add(SingleIdentifier id, UsageInfo usage) throws HandleCodeException {

    if (skippedvariables != null && skippedvariables.contains(id.getName())) {
      return;
    } else if (skippedvariables != null && id instanceof StructureIdentifier) {
      AbstractIdentifier owner = id;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (owner instanceof SingleIdentifier && skippedvariables.contains(((SingleIdentifier)owner).getName())) {
          return;
        }
      }
    }
    List<UsageInfo> uset;

    if (unsafes.contains(id)) {
      return;
    }

    if (!Stat.containsKey(id)) {
      uset = new LinkedList<>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
      if (unsafeDetector.isUnsafeCase(uset, usage)) {
        unsafes.add(id);
      }
    }
    uset.add(usage);
    //System.out.println("Add unsafe: " + id + ", " + unsafes.size());
    //unsafes.add(id);
  }*/

  /*public void add(SingleIdentifier id, Access access, UsageStatisticsState state, EdgeType type, int line, CallstackState callstackState) throws HandleCodeException {
    if (state.containsLinks(id)) {
      id = (SingleIdentifier) state.getLinks(id);
    }
    if (skippedvariables != null && skippedvariables.contains(id.getName())) {
      return;
    } else if (skippedvariables != null && id instanceof StructureIdentifier) {
      AbstractIdentifier owner = id;
      while (owner instanceof StructureIdentifier) {
        owner = ((StructureIdentifier)owner).getOwner();
        if (owner instanceof SingleIdentifier && skippedvariables.contains(((SingleIdentifier)owner).getName())) {
          return;
        }
      }
    }
    if (id instanceof LocalVariableIdentifier && id.getDereference() <= 0) {
      //we don't save in statistics ordinary local variables
      return;
    }
    if (id instanceof StructureIdentifier && !id.isGlobal() && !id.isPointer()) {
      //skips such cases, as 'a.b'
      return;
    }
    if (id instanceof StructureIdentifier) {
      id = ((StructureIdentifier)id).toStructureFieldIdentifier();
    }
    if (!printAllUnsafeUsages && unsafes.contains(id)) {
      return;
    }
    logger.log(Level.FINE, "Add id " + id + " to unsafe statistics");
    List<UsageInfo> uset;
    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    logger.log(Level.FINEST, "Its locks are: " + lockState);

    //We can't get line from location, because it is old state
    LineInfo lineInfo = new LineInfo(line);
    EdgeInfo info = new EdgeInfo(type);

    UsageInfo usage = new UsageInfo(access, lineInfo, info, lockState, callstackState);

    if (!Stat.containsKey(id)) {
      uset = new LinkedList<>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
      if (!unsafes.contains(id) && unsafeDetector.isUnsafeCase(Stat.get(id), usage)) {
        unsafes.add(id);
      }
    }
    uset.add(usage);
  }*/

  private List<LockStatisticsLock> findAllLocks() {
    List<LockStatisticsLock> locks = new LinkedList<>();

    for (SingleIdentifier id : Stat.keySet()) {
      List<UsageInfo> uset = Stat.get(id);

      for (UsageInfo uinfo : uset){
        if (uinfo.getLockState() == null) {
          continue;
        }
    	  for (LockStatisticsLock lock : uinfo.getLockState().getLocks()) {
    		  if( !lock.existsIn(locks)) {
    	      locks.add(lock);
    		  }
        }
      }
    }

    return locks;
  }

  /*
   * looks through all unsafe cases of current identifier and find the example of two lines with different locks,
   * one of them must be 'write'
   */
  private void createVisualization(SingleIdentifier id, UsageInfo usage, BufferedWriter writer) throws IOException {
    LinkedList<TreeLeaf> leafStack = new LinkedList<>();
    TreeLeaf currentLeaf;

    TreeLeaf.clearTrunkState();
    LockStatisticsState Locks = usage.getLockState();
    if (Locks != null) {
      for (LockStatisticsLock lock : Locks.getLocks()) {
        for (AccessPoint accessPoint : lock.getAccessPoints()) {
          currentLeaf = createTree(accessPoint.getCallstack());
          currentLeaf.add(lock.toString(), accessPoint.getLineInfo().getLine());
        }
      }
    }

    currentLeaf = createTree(usage.getCallStack());
    currentLeaf.addLast(usage.createUsageView(id), usage.getLine().getLine());

    //print this tree with aide of dfs
    currentLeaf = TreeLeaf.getTrunkState();
    leafStack.clear();
    if (currentLeaf.children.size() > 0) {
      leafStack.push(currentLeaf);
      currentLeaf = currentLeaf.children.getFirst();
    } else {
      logger.log(Level.WARNING, "Empty error path, can't proceed");
      return;
    }
    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    writer.append("Line 0:     N0 -{/*" + (Locks == null ? "empty" : Locks.toString()) + "*/}-> N0\n");
    while (currentLeaf != null) {
      if (currentLeaf.children.size() > 0) {
        writer.append(currentLeaf.toString() + "();}-> N0\n");
        writer.append("Line 0:     N0 -{Function start dummy edge}-> N0" + "\n");
        leafStack.push(currentLeaf);
        currentLeaf = currentLeaf.children.getFirst();
      } else {
        writer.append(currentLeaf.toString() + "}-> N0\n");
        currentLeaf = findFork(writer, currentLeaf, leafStack);
      }
    }
  }

  private TreeLeaf findFork(BufferedWriter writer, TreeLeaf pCurrentLeaf, LinkedList<TreeLeaf> leafStack) throws IOException {
    TreeLeaf tmpLeaf, currentLeaf = pCurrentLeaf;

    while (true) {
      tmpLeaf = leafStack.pop();
      if (tmpLeaf.children.size() > 1  && !tmpLeaf.children.getLast().equals(currentLeaf)) {
        leafStack.push(tmpLeaf);
        currentLeaf = tmpLeaf.children.get(tmpLeaf.children.indexOf(currentLeaf) + 1);
        break;
      }
      if (tmpLeaf.equals(TreeLeaf.getTrunkState())) {
        currentLeaf = null;
        break;
      }
      writer.append("Line 0:     N0 -{return;}-> N0\n");
      currentLeaf = tmpLeaf;
    }
    return currentLeaf;
  }

  private TreeLeaf createTree(CallstackState state) {
    LinkedList<CallstackState> tmpList = revertCallstack(state);
    TreeLeaf currentLeaf;
    CallstackState tmpState;

    //add to tree of calls this path
    currentLeaf = TreeLeaf.getTrunkState();
    tmpState = tmpList.getFirst();
    if (!tmpState.getCallNode().getFunctionName().equals(tmpState.getCurrentFunction())) {
      currentLeaf = currentLeaf.add(tmpList.getFirst().getCallNode().getFunctionName(), 0);
    }
    for (CallstackState callstack : tmpList) {
      currentLeaf = currentLeaf.addLast(callstack.getCurrentFunction(), callstack.getCallNode().getLeavingEdge(0).getLineNumber());
    }
    return currentLeaf;
  }

  private LinkedList<CallstackState> revertCallstack(CallstackState tmpState) {
    LinkedList<CallstackState> tmpList = new LinkedList<>();

    while (tmpState != null) {
      tmpList.push(tmpState);
      tmpState = tmpState.getPreviousState();
    }
    return tmpList;
  }

  private void createVisualization(SingleIdentifier id, BufferedWriter writer) throws IOException {
    UsageSet uinfo = Stat.get(id);

    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    if (id instanceof StructureFieldIdentifier) {
      writer.append("###\n");
    } else if (id instanceof GlobalVariableIdentifier) {
      writer.append("#\n");
    } else if (id instanceof LocalVariableIdentifier) {
      writer.append("##" + ((LocalVariableIdentifier)id).getFunction() + "\n");
    } else {
      logger.log(Level.WARNING, "What is it? " + id.toString());
    }
    writer.append(id.getDereference() + "\n");
    writer.append(id.getType().toASTString(id.getName()) + "\n");
    if (uinfo.isTrueUnsafe()) {
      writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
    }
    writer.append("Line 0:     N0 -{/*Number of usages:" + uinfo.size() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");
    try {
      Pair<UsageInfo, UsageInfo> tmpPair = unsafeDetector.getUnsafePair(uinfo);
      createVisualization(id, tmpPair.getFirst(), writer);
      createVisualization(id, tmpPair.getSecond(), writer);
      if (printAllUnsafeUsages) {
        writer.append("Line 0:     N0 -{_____________________}-> N0" + "\n");
        writer.append("Line 0:     N0 -{All usages:}-> N0" + "\n");
        for (UsageInfo ui : uinfo) {
          createVisualization(id, ui, writer);
        }
      }
    } catch (HandleCodeException e) {
      logger.log(Level.WARNING, "Can't find unsafes in " + id.getName());
      return;
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
		BufferedWriter writer = null;
		printStatisticsTimer.start();
		UsageContainer container = AbstractStates.extractStateByType(reached.getFirstState(), UsageStatisticsState.class)
		    .getContainer();
		Stat = container.getStatistics();
		List<SingleIdentifier> unsafes = container.getUnsafes();
    try {
      writer = Files.openOutputFile(outputStatFileName);
      logger.log(Level.FINE, "Print statistics about unsafe cases");
      printCountStatistics(writer, Stat.keySet());
      Collection<SingleIdentifier> unsafeCases = unsafes;//unsafeDetector.getUnsafes(Stat);
      printCountStatistics(writer, unsafeCases);
      printLockStatistics(writer);
      logger.log(Level.FINEST, "Processing unsafe identifiers");
      for (SingleIdentifier id : unsafeCases) {
        createVisualization(id, writer);
      }
      writer.close();
    } catch(FileNotFoundException e) {
      logger.log(Level.SEVERE, "File " + outputStatFileName + " not found");
      return;
    } catch (IOException e) {
      logger.log(Level.SEVERE, e.getMessage());
      return;
    }
    out.println("Time for transfer relation:    " + transferRelationTimer);
    printStatisticsTimer.stop();
    out.println("Time for printing statistics:  " + printStatisticsTimer);
    //out.println("Time for adding to reached state:  " + USReachedSet.addTimer);
    /*System.out.println(" \t \t Global Local \t Structure");
    System.out.println("Assignment: \t " + counter[0][0] + " \t " + counter[1][0] + " \t " + counter[2][0]);
    System.out.println("Assumption: \t " + counter[0][1] +" \t " + counter[1][1] +" \t " + counter[2][1] );
    System.out.println("Function call: \t " + counter[0][2] +" \t " + counter[1][2] +" \t " + counter[2][2]);*/
  }

  private void printLockStatistics(BufferedWriter writer) throws IOException {
    List<LockStatisticsLock> mutexes = findAllLocks();

    Collections.sort(mutexes);
    writer.append(mutexes.size() + "\n");
    for (LockStatisticsLock lock : mutexes) {
      writer.append(lock.toString() + "\n");
    }
  }

  private void printCountStatistics(BufferedWriter writer, Collection<SingleIdentifier> idSet) throws IOException {
    int global = 0, local = 0, fields = 0;
    int globalPointer = 0, localPointer = 0, fieldPointer = 0;

    for (SingleIdentifier id : idSet) {
      if (id instanceof GlobalVariableIdentifier) {
        if (id.getDereference() == 0) {
          global++;
        } else {
          globalPointer++;
        }
      }
      else if (id instanceof LocalVariableIdentifier) {
        if (id.getDereference() == 0) {
          local++;
        } else {
          localPointer++;
        }
      }
      else if (id instanceof StructureFieldIdentifier) {
        if (id.getDereference() == 0) {
          fields++;
        } else {
          fieldPointer++;
        }
      }
    }
    //writer.println(counter);
    writer.append(global + "\n");
    writer.append(globalPointer + "\n");
    writer.append(local + "\n");
    writer.append(localPointer + "\n");
    //writer.println("--Structures:           " + structures);
    writer.append(fields + "\n");
    writer.append(fieldPointer + "\n");
    writer.append(global + globalPointer + local + localPointer + fields + fieldPointer + "\n");
  }

  @Override
  public String getName() {
    return "UsageStatisticsCPA";
  }

  /*public void addSkippedVariables(Set<String> vars) {
    skippedvariables.addAll(vars);
  }*/

  /*public void removeState(UsageStatisticsState pUstate) {
    List<UsageInfo> uset;
    Set<Pair<List<UsageInfo>, UsageInfo>> toDelete = new HashSet<>();
    for (SingleIdentifier id : Stat.keySet()) {
      uset = Stat.get(id);
      for (UsageInfo uinfo : uset) {
        if (uinfo.getKeyState().equals(pUstate)) {
          toDelete.add(Pair.of(uset, uinfo));
        }
      }
    }

    for (Pair<List<UsageInfo>, UsageInfo> pair : toDelete) {
      pair.getFirst().remove(pair.getSecond());
    }
  }*/
}
