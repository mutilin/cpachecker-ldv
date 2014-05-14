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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
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

  /*@Option(values={"PAIR", "SETDIFF"},toUppercase=true,
      description="which data process we should use")
  private String unsafeDetectorType = "PAIR";*/

  @Option(description="print all unsafe cases in report")
  private boolean printAllUnsafeUsages = false;

  private final LogManager logger;
  private int totalUsages = 0;
  private int maxNumberOfUsages = 0;

  public Timer transferRelationTimer = new Timer();
  public Timer printStatisticsTimer = new Timer();

  public UsageStatisticsCPAStatistics(Configuration config, LogManager pLogger) throws InvalidConfigurationException{
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
  private void createVisualization(SingleIdentifier id, UsageInfo usage, Writer writer) throws IOException {
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

  private TreeLeaf findFork(Writer writer, TreeLeaf pCurrentLeaf, LinkedList<TreeLeaf> leafStack) throws IOException {
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

  private void createVisualization(SingleIdentifier id, Writer writer) throws IOException {
    UsageSet uinfo = Stat.get(id);
    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    totalUsages += uinfo.size();
    if (uinfo.size() > maxNumberOfUsages) {
      maxNumberOfUsages = uinfo.size();
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
		Writer writer = null;
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
    out.println("Amount of unsafes:             " + unsafes.size());
    out.println("Amount of unsafe usages:       " + totalUsages + "(avg. " +
        (unsafes.size() == 0 ? "0" : (totalUsages/unsafes.size()))
        + ", max " + maxNumberOfUsages + ")");
    int allUsages = 0, maxUsage = 0;
    for (SingleIdentifier id : Stat.keySet()) {
      allUsages += Stat.get(id).size();
      if (maxUsage < Stat.get(id).size()) {
        maxUsage = Stat.get(id).size();
      }
    }
    out.println("Total amount of variables:     " + Stat.keySet().size());
    out.println("Total amount of usages:        " + allUsages + "(avg. " +
        (Stat.keySet().size() == 0 ? "0" : (allUsages/Stat.keySet().size()))
        + ", max " + maxUsage + ")");
    out.println("Time for transfer relation:    " + transferRelationTimer);
    printStatisticsTimer.stop();
    out.println("Time for printing statistics:  " + printStatisticsTimer);
  }

  private void printLockStatistics(Writer writer) throws IOException {
    List<LockStatisticsLock> mutexes = findAllLocks();

    Collections.sort(mutexes);
    writer.append(mutexes.size() + "\n");
    for (LockStatisticsLock lock : mutexes) {
      writer.append(lock.toString() + "\n");
    }
  }

  private void printCountStatistics(Writer writer, Collection<SingleIdentifier> idSet) throws IOException {
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
}
