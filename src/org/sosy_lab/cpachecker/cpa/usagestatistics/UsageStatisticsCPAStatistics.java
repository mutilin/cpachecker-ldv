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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.abm.ABMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.AccessPoint;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usagestatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {

  private Map<SingleIdentifier, List<UsageInfo>> Stat;
  //ABM interface to restore original callstacks
  private ABMRestoreStack stackRestoration;

  UnsafeDetector unsafeDetector = null;

  @Option(name="localanalysis", description="should we use local analysis?")
  private boolean localAnalysis = false;

  @Option(name="output", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  @Option(description = "variables, which will not be saved in statistics")
  private Set<String> skippedvariables = null;

  @Option(values={"PAIR", "SETDIFF"},toUppercase=true,
      description="which data process we should use")
  private String unsafeDetectorType = "PAIR";

  //@Option(description="if we need to print all variables, not only unsafe cases")
  //private boolean fullstatistics = true;

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
    /*if (i == 1 && j == 0) {
      System.out.println(line);
    }
    counter[i][j]++;*/
  }

  public UsageStatisticsCPAStatistics(Configuration config) throws InvalidConfigurationException{
    Stat = new HashMap<>();
    config.inject(this);

    if (unsafeDetectorType.equals("PAIR")) {
      unsafeDetector = new PairwiseUnsafeDetector(config);
    } else if (unsafeDetectorType.equals("SETDIFF")) {
      unsafeDetector = new SetDifferenceUnsafeDetector(config);
    } else {
      System.out.println("Unknown data procession " + unsafeDetectorType);
      System.exit(0);
    }
  }

  public void add(SingleIdentifier id, Access access, UsageStatisticsState state, EdgeType type) throws HandleCodeException {
    if (state.containsLinks(id)) {
      id = (SingleIdentifier) state.getLinks(id);
    }
    if (skippedvariables != null && skippedvariables.contains(id.getName())) {
      return;
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

    List<UsageInfo> uset;
    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(state, CallstackState.class);
    CFANode location = AbstractStates.extractLocation(state);

    callstackState = createStack(callstackState);

    LineInfo lineInfo = new LineInfo(location.getLineNumber());
    EdgeInfo info = new EdgeInfo(type);

    UsageInfo usage = new UsageInfo(access, lineInfo, info, lockState, callstackState);

    if (!Stat.containsKey(id)) {
      uset = new LinkedList<>();
      Stat.put(id, uset);
    } else {
      uset = Stat.get(id);
    }
    uset.add(usage);
  }

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
   * looks through all unsafe cases of current identifier and find the example of two lines with different locks, one of them must be 'write'
   */
  private void createVisualization(SingleIdentifier id, UsageInfo usage, PrintWriter writer) {
    LinkedList<TreeLeaf> leafStack = new LinkedList<>();
    TreeLeaf currentLeaf;

    TreeLeaf.clearTrunkState();
    LockStatisticsState Locks = usage.getLockState();
    if (Locks != null) {
      for (LockStatisticsLock lock : Locks.getLocks()) {
        for (AccessPoint accessPoint : lock.getAccessPoints()) {
          currentLeaf = createTree(accessPoint.getCallstack());
          currentLeaf.add(lock.toString(), accessPoint.line.line);
        }
      }
    }

    currentLeaf = createTree(usage.getCallStack());
    currentLeaf.addLast(createIdUsageView(id, usage), usage.getLine().line);

    //print this tree with aide of dfs
    currentLeaf = TreeLeaf.getTrunkState();
    leafStack.clear();
    if (currentLeaf.children.size() > 0) {
      leafStack.push(currentLeaf);
      currentLeaf = currentLeaf.children.getFirst();
    } else {
      System.err.println("Empty error path, can't proceed");
      return;
    }
    writer.println("Line 0:     N0 -{/*_____________________*/}-> N0");
    writer.println("Line 0:     N0 -{/*" + (Locks == null ? "empty" : Locks.toString()) + "*/}-> N0");
    while (currentLeaf != null) {
      if (currentLeaf.children.size() > 0) {
        writer.println("Line " + currentLeaf.line + ":     N0 -{" + currentLeaf.code + "();}-> N0");
        writer.println("Line 0:     N0 -{Function start dummy edge}-> N0");
        leafStack.push(currentLeaf);
        currentLeaf = currentLeaf.children.getFirst();
      } else {
        writer.println("Line " + currentLeaf.line + ":     N0 -{" + currentLeaf.code + "}-> N0");
        currentLeaf = findFork(writer, currentLeaf, leafStack);
      }
    }
  }

  private TreeLeaf findFork(PrintWriter writer, TreeLeaf pCurrentLeaf, LinkedList<TreeLeaf> leafStack) {
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
      writer.println("Line 0:     N0 -{return;}-> N0");
      currentLeaf = tmpLeaf;
    }
    return currentLeaf;
  }

  private String createIdUsageView(SingleIdentifier id, UsageInfo ui) {
    String name = id.toString();
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
      name = id.getType().toASTString(name);
    }
    return name;
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
      currentLeaf = currentLeaf.addLast(callstack);
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

  private void createVisualization(SingleIdentifier id, PrintWriter writer) {
    List<UsageInfo> uinfo = Stat.get(id);

    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    if (id instanceof StructureFieldIdentifier) {
      writer.println("###");
    } else if (id instanceof GlobalVariableIdentifier) {
      writer.println("#");
    } else if (id instanceof LocalVariableIdentifier) {
      writer.println("##" + ((LocalVariableIdentifier)id).getFunction());
    } else {
      System.err.println("What is it?" + id.toString());
    }
    /*if (id.getDereference() < 0) {
      System.out.println("Adress unsafe: " + id.getName());
    }*/
    writer.println(id.getDereference());
    writer.println(id.getType().toASTString(id.getName()));
    writer.println("Line 0:     N0 -{/*Number of usages:" + uinfo.size() + "*/}-> N0");
    writer.println("Line 0:     N0 -{/*Two examples:*/}-> N0");
    try {
      Pair<UsageInfo, UsageInfo> tmpPair = unsafeDetector.getUnsafePair(uinfo);
      createVisualization(id, tmpPair.getFirst(), writer);
      createVisualization(id, tmpPair.getSecond(), writer);
      /*writer.println("Line 0:     N0 -{_____________________}-> N0");
      writer.println("Line 0:     N0 -{All usages:}-> N0");
      for (UsageInfo ui : uinfo)
        createVisualization(id, ui, writer);
      */
    } catch (HandleCodeException e) {
      System.err.println("Can't find unsafes in " + id.getName());
      return;
    }
  }

  private void createDir(File file) throws IOException {
    File previousDir = file.getParentFile();
    if (!previousDir.exists()) {
      createDir(previousDir);
    }
    file.mkdir();
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
		PrintWriter writer = null;
		FileOutputStream file = null;

    try {
      File outputFile = outputStatFileName.toFile();
      if (!outputFile.exists()) {
        File previousDir = outputFile.getParentFile();
        if (!previousDir.exists()) {
          createDir(previousDir);
        }
        outputFile.createNewFile();
      }
      file = new FileOutputStream (outputFile.getPath());
      writer = new PrintWriter(file);
    } catch(FileNotFoundException e) {
      System.err.println("File " + outputStatFileName + " not found");
      return;
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return;
    }

    printCountStatistics(writer, Stat.keySet());
    Collection<SingleIdentifier> unsafeCases = unsafeDetector.getUnsafes(Stat);
    printCountStatistics(writer, unsafeCases);
    printLockStatistics(writer);

    for (SingleIdentifier id : unsafeCases) {
      createVisualization(id, writer);
    }

    writer.close();

    /*System.out.println(" \t \t Global Local \t Structure");
    System.out.println("Assignment: \t " + counter[0][0] + " \t " + counter[1][0] + " \t " + counter[2][0]);
    System.out.println("Assumption: \t " + counter[0][1] +" \t " + counter[1][1] +" \t " + counter[2][1] );
    System.out.println("Function call: \t " + counter[0][2] +" \t " + counter[1][2] +" \t " + counter[2][2]);*/
  }

  private void printLockStatistics(PrintWriter writer) {
    List<LockStatisticsLock> mutexes = findAllLocks();

    Collections.sort(mutexes, new LockStatisticsLock.LockComparator());
    writer.println(mutexes.size());
    for (LockStatisticsLock lock : mutexes) {
      writer.println(lock.toString());
    }
  }

  private void printCountStatistics(PrintWriter writer, Collection<SingleIdentifier> idSet) {
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
    writer.println(global);
    writer.println(globalPointer);
    writer.println(local);
    writer.println(localPointer);
    //writer.println("--Structures:           " + structures);
    writer.println(fields);
    writer.println(fieldPointer);
    writer.println(global + globalPointer + local + localPointer + fields + fieldPointer);
  }

  @Override
  public String getName() {
    return "UsageStatisticsCPA";
  }

  public CallstackState createStack(CallstackState state) throws HandleCodeException {
    //need to LockStatistics usage
    return stackRestoration.restoreCallstack(state);
  }

  public void setStackRestoration(ABMRestoreStack rStack) {
    stackRestoration = rStack;
  }
}
