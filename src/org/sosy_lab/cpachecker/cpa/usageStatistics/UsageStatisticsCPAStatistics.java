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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
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
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.abm.ABMRestoreStack;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.lockStatistics.AccessPoint;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsLock;
import org.sosy_lab.cpachecker.cpa.lockStatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usageStatistics.EdgeInfo.EdgeType;
import org.sosy_lab.cpachecker.cpa.usageStatistics.UsageInfo.Access;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {

  private Map<SingleIdentifier, Set<UsageInfo>> Stat;
  private int totalVarUsageCounter = 0;
  //skipped by transfer relation
  private int skippedUsageCounter = 0;
  //ABM interface to restore original callstacks
  private ABMRestoreStack stackRestoration;

  UnsafeDetector unsafeDetector = null;
  //lcov data for code coverage generation
  CodeCovering covering;

  @Option(name="localanalysis", description="should we use local analysis?")
  private boolean localAnalysis = false;

  @Option(name="output", description="path to write results")
  private String outputStatFileName = "test/rawstat";

  private String outputFileName = "output/localsave";

  @Option(description = "variables, which will not be saved in statistics")
  private Set<String> skippedvariables = null;

  @Option(values={"PAIR", "SETDIFF"},toUppercase=true,
      description="which data process we should use")
  private String unsafeDetectorType = "PAIR";
  private Map<String, Map<String, String>> localStatistics;

  //@Option(description="if we need to print all variables, not only unsafe cases")
  //private boolean fullstatistics = true;

  //@Option(description="Do we need to store statistics of all variables or only pointers")
  //private boolean onlypointers = true;

  public UsageStatisticsCPAStatistics(Configuration config, CodeCovering cover) throws InvalidConfigurationException{
    Stat = new HashMap<SingleIdentifier, Set<UsageInfo>>();
    config.inject(this);

    if (unsafeDetectorType.equals("PAIR"))
      unsafeDetector = new PairwiseUnsafeDetector(config);
    else if (unsafeDetectorType.equals("SETDIFF"))
      unsafeDetector = new SetDifferenceUnsafeDetector(config);
    else {
      System.out.println("Unknown data procession " + unsafeDetectorType);
      System.exit(0);
    }
    covering = cover;

    if (localAnalysis) {
      //Restore all information
      localStatistics = new HashMap<String, Map<String, String>>();
      try {
        BufferedReader reader = new BufferedReader(new FileReader(outputFileName));
        String line, node = null, local;
        String[] localSet;
        Map<String, String> info = null;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("N")) {
            //N1 - it's node identifier
            if (node != null && info != null) {
              localStatistics.put(node, info);
            }
            node = line;
            info = new HashMap<String, String>();
          } else if (line.length() > 0) {
            // it's information about local statistics
            local = line;
            localSet = local.split(" - ");
            info.put(localSet[0], localSet[1]);

          }
        }
        if (node != null && info != null) {
          localStatistics.put(node, info);
        }
        reader.close();
      } catch(FileNotFoundException e) {
        System.err.println("Cannot open file " + outputFileName);
      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }

  public void add(List<Pair<SingleIdentifier, Access>> result, UsageStatisticsState state, int line, EdgeType type) throws HandleCodeException {
    Set<UsageInfo> uset;
    SingleIdentifier id;

    LockStatisticsState lockState = AbstractStates.extractStateByType(state, LockStatisticsState.class);
    CallstackState callstackState = AbstractStates.extractStateByType(state, CallstackState.class);

    callstackState = createStack(callstackState);

    LineInfo lineInfo = new LineInfo(line);
    EdgeInfo info = new EdgeInfo(type);

    for (Pair<SingleIdentifier, Access> tmpPair : result) {
      totalVarUsageCounter++;
      id = tmpPair.getFirst();
      if (id == null) {
        skippedUsageCounter++;
        continue;
      }
      if (skippedvariables != null && skippedvariables.contains(id.getName())) {
        skippedUsageCounter++;
        continue;
      }
      if (id instanceof LocalVariableIdentifier && !(id.getType() instanceof CPointerType)
          && id.getDereference() <= 0) {
        //we don't save in statistics ordinary local variables
        skippedUsageCounter++;
        continue;
      }
      //last check: if this variable is local, because of local analysis
      if (localAnalysis) {
        CFANode node = AbstractStates.extractLocation(state);
        Map<String, String> localInfo = localStatistics.get(node.toString());
        if (localInfo != null && localInfo.containsKey(id.getName())) {
          String dataType = localInfo.get(id.getName());
          if (dataType.equalsIgnoreCase("local")) {
            System.out.println("Skip " + id.getName() + " as local");
            skippedUsageCounter++;
            continue;
          }
        }
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

  private Set<LockStatisticsLock> findAllLocks() {
    Set<LockStatisticsLock> locks = new HashSet<LockStatisticsLock>();

    for (SingleIdentifier id : Stat.keySet()) {
      Set<UsageInfo> uset = Stat.get(id);

      for (UsageInfo uinfo : uset){
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
  private void createVisualization(SingleIdentifier id, UsageInfo ui, PrintWriter writer) {
    LinkedList<CallstackState> tmpList = new LinkedList<CallstackState>();
    LinkedList<TreeLeaf> leafStack = new LinkedList<TreeLeaf>();
    TreeLeaf tmpLeaf, currentLeaf;
    CallstackState tmpState;

    LockStatisticsState Locks = ui.getLockState();
    currentLeaf = TreeLeaf.clearTrunkState();
    for (LockStatisticsLock lock : Locks.getLocks()) {
      for (AccessPoint accessPoint : lock.getAccessPoints()) {
        currentLeaf = TreeLeaf.getTrunkState();
        tmpState = accessPoint.getCallstack();
        tmpList.clear();
        //revert callstacks of locks
        while (tmpState != null) {
          tmpList.push(tmpState);
          tmpState = tmpState.getPreviousState();
        }
        //create tree of calls for locks
        tmpState = tmpList.getFirst();
        if (!tmpState.getCallNode().getFunctionName().equals(tmpState.getCurrentFunction()))
          currentLeaf = currentLeaf.add(tmpList.getFirst().getCallNode().getFunctionName(), 0);
        for (CallstackState callstack : tmpList) {
          currentLeaf = currentLeaf.add(callstack);
        }
        //System.out.println("Add " + lock.getName());
        currentLeaf.add(lock.getName() + "()", accessPoint.line.line);
      }
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
    tmpState = tmpList.getFirst();
    if (!tmpState.getCallNode().getFunctionName().equals(tmpState.getCurrentFunction()))
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
      name = id.getType().toASTString(name);
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
      return;
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
      }
    }
  }

  private void createVisualization(SingleIdentifier id, PrintWriter writer, boolean allStats) {
    Set<UsageInfo> uinfo = Stat.get(id);

    if (uinfo == null || uinfo.size() == 0)
      return;
    if (allStats) {
      if (id instanceof StructureFieldIdentifier)
        writer.println("###");
      else if (id instanceof GlobalVariableIdentifier) {
        writer.println("#");
      }
      else if (id instanceof LocalVariableIdentifier)
        writer.println("##" + ((LocalVariableIdentifier)id).getFunction());
      else
        System.err.println("What is it?" + id.toString());
      if (id.getDereference() < 0) {
        System.out.println("Adress unsafe: " + id.getName());
      }
      writer.println(id.getDereference());
      writer.println(id.getType().toASTString(id.getName()));
      writer.println("Line 0:     N0 -{/*Number of usages:" + uinfo.size() + "*/}-> N0");
      writer.println("Line 0:     N0 -{/*Two examples:*/}-> N0");
      try {
        Pair<UsageInfo, UsageInfo> tmpPair = unsafeDetector.getSomeUnsafePair(uinfo);
        createVisualization(id, tmpPair.getFirst(), writer);
        createVisualization(id, tmpPair.getSecond(), writer);
        /*writer.println("Line 0:     N0 -{_____________________}-> N0");
        writer.println("Line 0:     N0 -{All usages:}-> N0");
        for (UsageInfo ui : uinfo)
          createVisualization(id, ui, writer);
        */
      } catch (HandleCodeException e) {
        //strange, but we didn't find unsafe example. So, return.
        return;
      }
    } else {
    for (UsageInfo ui : uinfo)
      createVisualization(id, ui, writer);
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
		PrintWriter writer = null;
		FileOutputStream file = null;


    /*Collection<GlobalIdentifier> global = new HashSet<GlobalIdentifier>();
    Collection<LocalIdentifier> local = new HashSet<LocalIdentifier>();
    Collection<StructureFieldIdentifier> fields = new HashSet<StructureFieldIdentifier>();*/
    int global = 0, local = 0, fields = 0, pointers = 0;

    try {
      file = new FileOutputStream (outputStatFileName);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + outputStatFileName);
      return;
    }

    int counter = 0;

    for (SingleIdentifier id : Stat.keySet()) {
      counter += Stat.get(id).size();

      if (id.getDereference() == 0) {
        if (id instanceof GlobalVariableIdentifier)
        //global.add((GlobalIdentifier)id);
          global++;
        else if (id instanceof LocalVariableIdentifier)
        //local.add((LocalIdentifier)id);
          local++;
        else if (id instanceof StructureFieldIdentifier)
        //fields.add((StructureFieldIdentifier)id);
          fields++;
      } else {
        pointers++;
      }
    }

    writer.println(global);
    writer.println(local);
    //writer.println("--Structures:           " + structures);
    writer.println(fields);
    writer.println(pointers);

    writer.println(totalVarUsageCounter);
    writer.println(counter);
    writer.println(skippedUsageCounter);

    Set<LockStatisticsLock> mutexes = findAllLocks();

    writer.println(mutexes.size());

    for (LockStatisticsLock lock : mutexes) {
      writer.println(lock.toString());
    }

    Collection<SingleIdentifier> unsafeCases = unsafeDetector.getUnsafes(Stat);
    counter = global = local = fields = pointers = 0;
    for (SingleIdentifier id : unsafeCases) {
      counter += Stat.get(id).size();

      if (id.getDereference() <= 0) {
        if (id instanceof GlobalVariableIdentifier)
        //global.add((GlobalIdentifier)id);
          global++;
        else if (id instanceof LocalVariableIdentifier)
        //local.add((LocalIdentifier)id);
          local++;
        else if (id instanceof StructureFieldIdentifier)
        //fields.add((StructureFieldIdentifier)id);
          fields++;
      } else {
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

    for (SingleIdentifier id : unsafeCases) {
      createVisualization(id, writer, true);
    }

    writer.close();

    covering.generate();
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
