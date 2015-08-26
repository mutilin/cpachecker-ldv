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
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.RefinedUsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.RefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnsafeDetector;
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

  public static enum OutputFileType {
    SINGLE_FILE,
    MULTIPLE_FILES
  }

  @Option(name="outputType", description="all variables should be printed to the one file or to the different")
  private OutputFileType outputFileType = OutputFileType.SINGLE_FILE;

  @Option(name="output", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  @Option(description="print all unsafe cases in report")
  private boolean printAllUnsafeUsages = false;

  /* Previous container is used when internal time limit occurs
   * and we need to store statistics. In current one the information can be not
   * relevant (for example not all ARG was built).
   * It is better to store unsafes from previous iteration of refinement.
   */
  private UsageContainer previousContainer;

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
  private final ShutdownNotifier shutdownNotifier;

  private UsageContainer container;
  private BAMTransferRelation transfer;
  private UnsafeDetector detector;
  private final LockStatisticsTransferRelation lockTransfer;

  public final Timer transferRelationTimer = new Timer();
  public final Timer printStatisticsTimer = new Timer();

  private final String outputSuffix;

  public UsageStatisticsCPAStatistics(Configuration config, LogManager pLogger,
      LockStatisticsTransferRelation lTransfer, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException{
    config.inject(this);
    logger = pLogger;
    lockTransfer = lTransfer;
    shutdownNotifier = pShutdownNotifier;
    //I don't know any normal way to know the output directory
    outputSuffix = outputStatFileName.getAbsolutePath().replace(outputStatFileName.getName(), "");
    previousContainer = null;
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
            return false;
          }
        } else if (pInput.getSuccessor().getFunctionName().equals("ldv_main")
            && pInput instanceof CAssumeEdge) {
          //Remove infinite switch, it's too long
          return false;
        }
        return true;
      }
    }).toList();
    int callstackDepth = 1;
    /*
     * We must use iterator to be sure, when is the end of the list.
     * I tried to check the edge, it is the last, but it can be repeated during the sequence
     */
    Iterator<CFAEdge> iterator = edges.iterator();
    while (iterator.hasNext()) {
      CFAEdge edge = iterator.next();
      if (edge instanceof CFunctionCallEdge && edges.get(edges.size() - 1) != edge) {
        callstackDepth++;
      } else if (edge instanceof CFunctionReturnEdge) {
        callstackDepth--;
      } else if (edge instanceof CReturnStatementEdge && !iterator.hasNext()) {
        assert callstackDepth > 0;
        callstackDepth--;
      } else if (edge instanceof BlankEdge && edge.getDescription().contains("return") && !iterator.hasNext()) {
        //Evil hack, but this is how etv works
        assert callstackDepth > 0;
        callstackDepth--;
      }
      String caption = shouldBeHighlighted(edge);
      if (caption != null) {
        writer.write("Line 0:     N0 -{/*" + caption + "*/}-> N0\n");
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      } else if (edge.getLineNumber() == usage.getLine().getLine() && edge.toString().contains(id.getName())) {
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      }
      writer.write(edge.toString() + "\n");
    }
    for (int i = 0; i < callstackDepth; i++) {
      writer.append("Line 0:     N0 -{return;}-> N0\n");
    }
    writer.write("\n");
  }

  private String shouldBeHighlighted(CFAEdge pEdge) {
    return lockTransfer.changeTheState(pEdge);
  }

  private void countUsageStatistics(UnrefinedUsagePointSet l) {
    Iterator<UsagePoint> pointIterator = l.getPointIterator();
    while (pointIterator.hasNext()) {
      UsagePoint point = pointIterator.next();
      totalNumberOfUsagePoints++;
      AbstractUsageInfoSet uset = l.getUsageInfo(point);
      if (uset instanceof RefinedUsageInfoSet) {
        //Refined and contains only one usage, which realizes this point
        trueUsagesInAllUnsafes++;
        if (detector.isTrueUnsafe(l)) {
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
    Pair<UsageInfo, UsageInfo> unsafe = detector.getUnsafePair(l);
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

  private void createVisualization(final SingleIdentifier id, final Writer pWriter) throws IOException, CPATransferException, InterruptedException {
    Writer writer = pWriter;
    final AbstractUsagePointSet uinfo = container.getUsages(id);
    final boolean isTrueUnsafe = detector.isTrueUnsafe(uinfo);

    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    countStatistics(uinfo);
    totalUsages += uinfo.size();
    if (uinfo.size() > maxNumberOfUsages) {
      maxNumberOfUsages = uinfo.size();
    }
    if (writer != null) {
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
    } else {
      assert outputFileType == OutputFileType.MULTIPLE_FILES;
      //Special format for Multi error traces in LDV
      Path currentPath = Paths.get(outputSuffix + "ErrorPath." + createUniqueName(id) + ".txt");
      writer = Files.openOutputFile(currentPath);
    }
    if (isTrueUnsafe) {
    	trueUnsafes++;
      writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
    }
    writer.append("Line 0:     N0 -{/*Number of usage points:" + uinfo.getNumberOfTopUsagePoints() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Number of usages      :" + uinfo.size() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");
    Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(uinfo);
    createVisualization(id, tmpPair.getFirst(), writer);
    createVisualization(id, tmpPair.getSecond(), writer);
    if (tmpPair.getFirst().failureFlag && tmpPair.getSecond().failureFlag) {
      totalFailureUnsafes++;
    } else if (tmpPair.getFirst().failureFlag || tmpPair.getSecond().failureFlag) {
      totalUnsafesWithFailureUsageInPair++;
    }
    if (pWriter == null) {
      writer.close();
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
		UsageStatisticsState firstState = AbstractStates.extractStateByType(reached.getFirstState(), UsageStatisticsState.class);
	  //Evil hack, but in normal case the analysis is terminated also with shutdown
    //The shutdowns can be distinguished only by reasons
		if (!shutdownNotifier.getReason().equals("Analysis terminated")) {
      //False unsafes are lost if shutdown is occurred
		  logger.log(Level.INFO, "Consider shutdown due to timeout, get previous usage container");
		  if (previousContainer == null) {
		    logger.log(Level.WARNING, "Timeout occurs during first iteration. Nothing to store.");
		    return;
		  }
		  container = previousContainer;
		} else {
		  container = firstState.getContainer();
		}
		detector = container.getUnsafeDetector();
		final int unsafeSize = container.getUnsafeSize();
		Writer writer = null;
    try {
      if (outputFileType == OutputFileType.SINGLE_FILE) {
        writer = Files.openOutputFile(outputStatFileName);
        logger.log(Level.FINE, "Print statistics about unsafe cases");
        printCountStatistics(writer, container.getUnsafeIterator());
      }
      logger.log(Level.FINEST, "Processing unsafe identifiers");
      Iterator<SingleIdentifier> unsafeIterator = container.getUnsafeIterator();
      while (unsafeIterator.hasNext()) {
        createVisualization(unsafeIterator.next(), writer);
      }
      if (writer != null) {
        writer.close();
      }
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

  private String createUniqueName(SingleIdentifier id) {
    String name = id.getType().toASTString(id.getName());
    name = name.replace(" ", "_");
    return name;
  }

  public void setPreviousContainer(UsageContainer pContainer) {
    previousContainer = pContainer;
  }
}
