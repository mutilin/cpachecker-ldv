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

import static com.google.common.collect.FluentIterable.from;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import javax.annotation.Nullable;

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
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.RefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;

import com.google.common.base.Predicate;

@Options(prefix="cpa.usagestatistics")
public class UsageStatisticsCPAStatistics implements Statistics {

  @Option(name="output", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  @Option(description="print all unsafe cases in report")
  private boolean printAllUnsafeUsages = false;

  private final LogManager logger;
  private int totalUsages = 0;
  private int totalNumberOfUsagePoints = 0;
  private int maxNumberOfUsagePoints = 0;
  private int maxNumberOfUsages = 0;

  private int totalFailureUsages = 0;
  private int totalFailureUnsafes = 0;
  private int totalUnsafesWithFailureUsages = 0;
  private int totalUnsafesWithFailureUsageInPair = 0;
  private int trueUnsafes = 0;
  private int trueUsagesInTrueUnsafe = 0;
  private int trueUsagesInAllUnsafes = 0;
  private int maxTrueUsages = 0;

  private UsageContainer container;
  private BAMTransferRelation transfer;

  public final Timer transferRelationTimer = new Timer();
  public final Timer printStatisticsTimer = new Timer();

  public UsageStatisticsCPAStatistics(Configuration config, LogManager pLogger) throws InvalidConfigurationException{
    config.inject(this);
    logger = pLogger;
  }

  /*
   * looks through all unsafe cases of current identifier and find the example of two lines with different locks,
   * one of them must be 'write'
   */
  private void createVisualization(final SingleIdentifier id, final UsageInfo usage, final Writer writer) throws IOException, CPATransferException, InterruptedException {
    LockStatisticsState Locks = usage.getLockState();

    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    writer.append("Line 0:     N0 -{/*" + Locks.toString() + "*/}-> N0\n");
    if (usage.failureFlag) {
      writer.append("Line 0:     N0 -{/*Failure in refinement*/}-> N0\n");
    }
    List<CFAEdge> edges;
    if (usage.getKeyState() != null) {
      ARGState root = transfer.findPath((ARGState)usage.getKeyState(), new HashMap<ARGState, ARGState>());
      ARGPath path = ARGUtils.getRandomPath(root);
      edges = path.getInnerEdges();
    } else {
      edges = usage.getPath();
    }
    edges = from(edges).filter(new Predicate<CFAEdge>() {
      @Override
      public boolean apply(@Nullable CFAEdge pInput) {
        if (pInput instanceof CDeclarationEdge) {
          if (((CDeclarationEdge)pInput).getDeclaration().isGlobal() ||
              pInput.getSuccessor().getFunctionName().equals("ldv_main")) {
          }
          return false;
        } else if (pInput.getSuccessor().getFunctionName().equals("ldv_main")
            && pInput instanceof CAssumeEdge) {
          //Remove infinite switch, it's too long
          return false;
        } else {
          return true;
        }
      }
    }).toList();
    int callstackDepth = 1;
    for (CFAEdge edge : edges) {
      if (edge instanceof CFunctionCallEdge && edges.get(edges.size() - 1) != edge) {
        callstackDepth++;
      } else if (edge instanceof CFunctionReturnEdge) {
        assert callstackDepth > 0;
        callstackDepth--;
      } else if (edge instanceof BlankEdge && edge.getDescription().equals("default return") && edges.get(edges.size() - 1) == edge) {
        assert callstackDepth > 0;
        callstackDepth--;
      }
      if (edge.getLineNumber() == usage.getLine().getLine()) {
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      }
      writer.write(edge.toString() + "\n");
    }
    for (int i = 0; i < callstackDepth; i++) {
      writer.append("Line 0:     N0 -{return;}-> N0\n");
    }
    writer.write("\n");
  }

  private void countUsageStatistics(UnrefinedUsagePointSet l) {
    Iterator<UsagePoint> pointIterator = l.getPointIterator();
    while (pointIterator.hasNext()) {
      UsagePoint point = pointIterator.next();
      totalNumberOfUsagePoints++;
      AbstractUsageInfoSet uset = l.getUsageInfo(point);
      if (point.isTrue()) {
        //Refined and contains only one usage, which realizes this point
        trueUsagesInAllUnsafes++;
        if (l.isTrueUnsafe()) {
          trueUsagesInTrueUnsafe++;
        }
        if (uset.getOneExample().failureFlag) {
          totalFailureUsages++;
        }
        continue;
      }
      for (UsageInfo uinfo : uset.getUsages()){
        if (uinfo.failureFlag) {
          totalFailureUsages++;
        }
      }
    }
  }

  private void countUsageStatistics(RefinedUsagePointSet l) {
    Pair<UsageInfo, UsageInfo> unsafe = l.getUnsafePair();
    UsageInfo first = unsafe.getFirst();
    UsageInfo second = unsafe.getSecond();
    if (first.failureFlag) {
      totalFailureUsages++;
    }
    if (second.failureFlag && first != second) {
      totalFailureUsages++;
    }
    totalNumberOfUsagePoints += l.size();
    trueUsagesInAllUnsafes += l.size();
    trueUsagesInTrueUnsafe += l.size();
  }

  private void countStatistics(AbstractUsagePointSet l) {
    int startFailureNum = totalFailureUsages;
    int startTrueNum = trueUsagesInTrueUnsafe;

    if (l instanceof UnrefinedUsagePointSet) {
      countUsageStatistics((UnrefinedUsagePointSet)l);
    } else if (l instanceof RefinedUsagePointSet) {
      countUsageStatistics((RefinedUsagePointSet)l);
    }

    int d = trueUsagesInTrueUnsafe - startTrueNum;
    if (d > maxTrueUsages) {
      maxTrueUsages = d;
    }
    if (maxNumberOfUsagePoints < l.getNumberOfTopUsagePoints()) {
      maxNumberOfUsagePoints = l.getNumberOfTopUsagePoints();
    }
    if (totalFailureUsages > startFailureNum) {
      totalUnsafesWithFailureUsages++;
    }
  }

  private void createVisualization(final SingleIdentifier id, final Writer writer) throws IOException, CPATransferException, InterruptedException {
    final AbstractUsagePointSet uinfo = container.getUsages(id);
    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    countStatistics(uinfo);
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
    	trueUnsafes++;
      writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
    }
    writer.append("Line 0:     N0 -{/*Number of usage points:" + uinfo.getNumberOfTopUsagePoints() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Number of usages      :" + uinfo.size() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");
    Pair<UsageInfo, UsageInfo> tmpPair = uinfo.getUnsafePair();
    createVisualization(id, tmpPair.getFirst(), writer);
    createVisualization(id, tmpPair.getSecond(), writer);
    if (tmpPair.getFirst().failureFlag && tmpPair.getSecond().failureFlag) {
      totalFailureUnsafes++;
    } else if (tmpPair.getFirst().failureFlag || tmpPair.getSecond().failureFlag) {
      totalUnsafesWithFailureUsageInPair++;
    }
    /*if (printAllUnsafeUsages) {
      writer.append("Line 0:     N0 -{_____________________}-> N0" + "\n");
      writer.append("Line 0:     N0 -{All usages:}-> N0" + "\n");
      Iterator<UsagePoint> pointIterator = uinfo.getPointIterator();
      while (pointIterator.hasNext()) {
        UsagePoint point = pointIterator.next();
        for (UsageInfo ui : uinfo.getUsageInfo(point).getUsages()){
          createVisualization(id, ui, writer);
        }
      }
    }*/
  }

  @Override
  public void printStatistics(final PrintStream out, final Result result, final ReachedSet reached) {
		printStatisticsTimer.start();
		container = AbstractStates.extractStateByType(reached.getFirstState(), UsageStatisticsState.class)
		    .getContainer();
		final int unsafeSize = container.getUnsafeSize();

    try {
      final Writer writer = Files.openOutputFile(outputStatFileName);
      logger.log(Level.FINE, "Print statistics about unsafe cases");
      printCountStatistics(writer, container.getUnsafeIterator());
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
    } catch (CPATransferException e) {
      logger.log(Level.SEVERE, "Exception during visualization");
      e.printStackTrace();
      return;
    } catch (InterruptedException e) {
      logger.log(Level.SEVERE, "Printing statistics was interrupted");
      e.printStackTrace();
      return;
    }
    out.println("Amount of unsafes:                                         " + unsafeSize);
    out.println("Amount of unsafe usages:                                   " + totalUsages + "(avg. " +
        (unsafeSize == 0 ? "0" : (totalUsages/unsafeSize))
        + ", max. " + maxNumberOfUsages + ")");
    out.println("Amount of unsafe usage points:                             " + totalNumberOfUsagePoints + "(avg. " +
        (unsafeSize == 0 ? "0" : (totalNumberOfUsagePoints/unsafeSize))
        + ", max. " + maxNumberOfUsagePoints + ")");
    out.println("Amount of true unsafes:                                    " + trueUnsafes);
    out.println("Amount of true usages in true unsafes:                     " + trueUsagesInTrueUnsafe + "(avg. " +
        (trueUnsafes == 0 ? "0" : (trueUsagesInTrueUnsafe/trueUnsafes))
        + ", max. " + maxTrueUsages + ")");
    out.println("Amount of true usages in all unsafes:                      " + trueUsagesInAllUnsafes);
    out.println("Amount of unsafes with both failures in pair:              " + totalFailureUnsafes);
    out.println("Amount of unsafes with one failure in pair:                " + totalUnsafesWithFailureUsageInPair);
    out.println("Amount of unsafes with at least once failure in usage list:" + totalUnsafesWithFailureUsages);
    out.println("Amount of usages with failure:                             " + totalFailureUsages);
    container.printUsagesStatistics(out);
    out.println("Time for transfer relation:         " + transferRelationTimer);
    out.println("Time for reseting unsafes:          " + container.resetTimer);
    printStatisticsTimer.stop();
    out.println("Time for printing statistics:       " + printStatisticsTimer);
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

  public void setBAMTransfer(BAMTransferRelation t) {
    transfer = t;
  }
}
