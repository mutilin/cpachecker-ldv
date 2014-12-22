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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.sosy_lab.cpachecker.cfa.model.CFANode;
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

import com.google.common.collect.UnmodifiableIterator;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {

  //public Map<SingleIdentifier, UsageList> Stat;

  private final UnsafeDetector unsafeDetector;

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
  
  private int totalFailureUsages = 0;
  private int totalFailureUnsafes = 0;
  private int totalUnsafesWithFailureUsages = 0;
  private int totalUnsafesWithFailureUsageInPair = 0;

  private UsageContainer container;

  public final Timer transferRelationTimer = new Timer();
  public final Timer printStatisticsTimer = new Timer();

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

    Iterator<SingleIdentifier> generalIterator = container.getGeneralIterator();
    while (generalIterator.hasNext()) {
      List<UsageInfo> uset = container.getUsages(generalIterator.next());

      for (UsageInfo uinfo : uset){
        if (uinfo.getLockState() == null) {
          continue;
        }
        Iterator<LockStatisticsLock> lockIterator = uinfo.getLockState().getLockIterator();
    	  while (lockIterator.hasNext()) {
    	    LockStatisticsLock lock = lockIterator.next();
    	    //existsIn() isn't based on equals(), don't remove it
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
  private void createVisualization(final SingleIdentifier id, final UsageInfo usage, final Writer writer) throws IOException {
    final LinkedList<Iterator<TreeLeaf>> leafStack = new LinkedList<>();
    TreeLeaf currentCallstackNode;
    Iterator<TreeLeaf> currentIterator;

    TreeLeaf.clearTrunkState();
    LockStatisticsState Locks = usage.getLockState();
    if (Locks != null) {
      final Iterator<LockStatisticsLock> lockIterator = Locks.getLockIterator();
      while(lockIterator.hasNext()) {
        LockStatisticsLock lock = lockIterator.next();
        UnmodifiableIterator<AccessPoint> accessPointIterator = lock.getAccessPointIterator();
        while (accessPointIterator.hasNext()) {
          AccessPoint accessPoint = accessPointIterator.next();
          currentCallstackNode = createTree(accessPoint.getCallstack());
          currentCallstackNode.add(lock.toString(), accessPoint.getLineInfo().getLine());
        }
      }
    }

    currentCallstackNode = createTree(usage.getCallStack());
    currentCallstackNode.add(usage.createUsageView(id), usage.getLine().getLine());

    //print this tree with aide of dfs
    currentCallstackNode = TreeLeaf.getTrunkState();
    leafStack.clear();
    if (currentCallstackNode.children.size() > 0) {
      currentIterator = currentCallstackNode.children.iterator();
      leafStack.push(currentIterator);
      currentCallstackNode = currentIterator.next();
    } else {
      logger.log(Level.WARNING, "Empty error path, can't proceed");
      return;
    }
    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    writer.append("Line 0:     N0 -{/*" + (Locks == null ? "empty" : Locks.toString()) + "*/}-> N0\n");
    while (currentCallstackNode != null) {
      if (currentCallstackNode.children.size() > 0) {
        writer.append(currentCallstackNode.toString());
        writer.append("Line 0:     N0 -{Function start dummy edge}-> N0" + "\n");
        currentIterator = currentCallstackNode.children.iterator();
        leafStack.push(currentIterator);
        currentCallstackNode = currentIterator.next();
      } else {
        writer.append(currentCallstackNode.toString());
        currentCallstackNode = findFork(writer, leafStack);
      }
    }
  }

  private TreeLeaf findFork(final Writer writer, final LinkedList<Iterator<TreeLeaf>> leafStack) throws IOException {
    Iterator<TreeLeaf> tmpIterator;

    //The first element is root, not an ordinary callstack node
    while (leafStack.size() > 1) {
      tmpIterator = leafStack.peek();
      if (tmpIterator.hasNext()) {
        return tmpIterator.next();
      } else {
        leafStack.removeFirst();
      }
      writer.append("Line 0:     N0 -{return;}-> N0\n");
    }
    return null;
  }

  private TreeLeaf createTree(final CallstackState state) {
    final LinkedList<CallstackState> tmpList = revertCallstack(state);

    //add to tree of calls this path
    TreeLeaf currentLeaf = TreeLeaf.getTrunkState();
    CallstackState tmpState = tmpList.getFirst();
    if (!tmpState.getCallNode().getFunctionName().equals(tmpState.getCurrentFunction())) {
      currentLeaf = currentLeaf.add(tmpList.getFirst().getCallNode().getFunctionName() + "()", 0);
    }
    for (CallstackState callstack : tmpList) {
      currentLeaf = currentLeaf.add(callstack.getCurrentFunction() + "()", callstack.getCallNode().getLeavingEdge(0).getLineNumber());
    }
    return currentLeaf;
  }

  private LinkedList<CallstackState> revertCallstack(CallstackState tmpState) {
    final LinkedList<CallstackState> tmpList = new LinkedList<>();

    while (tmpState != null) {
      tmpList.push(tmpState);
      tmpState = tmpState.getPreviousState();
    }
    return tmpList;
  }
  
  private void countFailureUsages(UsageList l) {
    int startNum = totalFailureUsages;
    for (UsageInfo uinfo : l) {
      if (uinfo.failureFlag) {
        totalFailureUsages++;
      }
    }
    if (totalFailureUsages > startNum) {
      totalUnsafesWithFailureUsages++;
    }
  }

  private void createVisualization(final SingleIdentifier id, final Writer writer) throws IOException {
    final UsageList uinfo = container.getUsages(id);
    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    countFailureUsages(uinfo);
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
      if (tmpPair.getFirst().failureFlag && tmpPair.getSecond().failureFlag) {
        totalFailureUnsafes++;
      } else if (tmpPair.getFirst().failureFlag || tmpPair.getSecond().failureFlag) {
        totalUnsafesWithFailureUsageInPair++;
      }
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
  public void printStatistics(final PrintStream out, final Result result, final ReachedSet reached) {
		printStatisticsTimer.start();
		container = AbstractStates.extractStateByType(reached.getFirstState(), UsageStatisticsState.class)
		    .getContainer();
		//Stat = container.getStatistics();
		final int unsafeSize = container.getUnsafeSize();

    try {
      final Writer writer = Files.openOutputFile(outputStatFileName);
      logger.log(Level.FINE, "Print statistics about unsafe cases");
      printCountStatistics(writer, container.getGeneralIterator());
      printCountStatistics(writer, container.getUnsafeIterator());
      printLockStatistics(writer);
      logger.log(Level.FINEST, "Processing unsafe identifiers");
      Iterator<SingleIdentifier> unsafeIterator = container.getUnsafeIterator();
      while (unsafeIterator.hasNext()) {
        createVisualization(unsafeIterator.next(), writer);
      }
      writer.close();
    } catch(FileNotFoundException e) {
      logger.log(Level.SEVERE, "File " + outputStatFileName + " not found");
      return;
    } catch (IOException e) {
      logger.log(Level.SEVERE, e.getMessage());
      return;
    }
    out.println("Amount of unsafes:             " + unsafeSize);
    out.println("Amount of unsafe usages:       " + totalUsages + "(avg. " +
        (unsafeSize == 0 ? "0" : (totalUsages/unsafeSize))
        + ", max " + maxNumberOfUsages + ")");
    out.println("Amount of unsafes with both failures in pair               " + totalFailureUnsafes);
    out.println("Amount of unsafes with one failure in pair                 " + totalUnsafesWithFailureUsageInPair);
    out.println("Amount of unsafes with at least once failure in usage list " + totalUnsafesWithFailureUsages);
    out.println("Amount of usages with failure                              " + totalFailureUsages);
    container.printUsagesStatistics(out);
    out.println("Time for transfer relation:    " + transferRelationTimer);
    printStatisticsTimer.stop();
    out.println("Time for printing statistics:  " + printStatisticsTimer);
    out.println("Time for reseting unsafes: " + container.resetTimer);
  }

  private void printLockStatistics(final Writer writer) throws IOException {
    final List<LockStatisticsLock> mutexes = findAllLocks();

    Collections.sort(mutexes);
    writer.append(mutexes.size() + "\n");
    for (LockStatisticsLock lock : mutexes) {
      writer.append(lock.toString() + "\n");
    }
  }

  private void printCountStatistics(final Writer writer, final Iterator<SingleIdentifier> idIterator) throws IOException {
    int global = 0, local = 0, fields = 0;
    int globalPointer = 0, localPointer = 0, fieldPointer = 0;
    SingleIdentifier id;

    while (idIterator.hasNext()) {
      id = idIterator.next();
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
