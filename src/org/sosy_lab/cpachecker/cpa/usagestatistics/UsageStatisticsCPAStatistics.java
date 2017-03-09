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

import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.MissingBlockException;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsTransferRelation;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.RefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usagestatistics.storage.UsagePoint;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.AssumeCase;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

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

  @Option(name="falseUnsafesOutput", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputFalseUnsafes = Paths.get("FalseUnsafes");

  @Option(description="print all unsafe cases in report")
  private boolean printAllUnsafeUsages = false;

  @Option(description="print information about false unsafes")
  private boolean printFalseUnsafes = false;

  /* Previous container is used when internal time limit occurs
   * and we need to store statistics. In current one the information can be not
   * relevant (for example not all ARG was built).
   * It is better to store unsafes from previous iteration of refinement.
   */

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
  //What is true now?
  //private int trueUsagesInTrueUnsafe = 0;
  //private int trueUsagesInAllUnsafes = 0;
  //private int maxTrueUsages = 0;
 // private final ShutdownNotifier shutdownNotifier;

  private UsageContainer container;
  private BAMTransferRelation transfer;
  private UnsafeDetector detector;
  private final LockStatisticsTransferRelation lockTransfer;
  private final Configuration config;

  public final Timer transferRelationTimer = new Timer();
  public final Timer printStatisticsTimer = new Timer();

  private final String outputSuffix;

  public UsageStatisticsCPAStatistics(Configuration pConfig, LogManager pLogger,
      LockStatisticsTransferRelation lTransfer) throws InvalidConfigurationException{
    pConfig.inject(this);
    logger = pLogger;
    lockTransfer = lTransfer;
    config = pConfig;
    outputSuffix = outputStatFileName.toAbsolutePath().getParent().toString();
  }

  private void resetAllCounters() {
    totalUsages = 0;
    totalNumberOfUsagePoints = 0;
    maxNumberOfUsagePoints = 0;
    maxNumberOfUsages = 0;

    totalFailureUsages = 0;
    totalFailureUnsafes = 0;
    totalUnsafesWithFailureUsages = 0;
    totalUnsafesWithFailureUsageInPair = 0;
    trueUnsafes = 0;
  }

  /*
   * looks through all unsafe cases of current identifier and find the example of two lines with different locks,
   * one of them must be 'write'
   */
  private void createVisualization(final SingleIdentifier id, final UsageInfo usage, final Writer writer) throws IOException, CPATransferException, InterruptedException {
    LockStatisticsState Locks = usage.getLockState();

    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    if (Locks != null) {
      writer.append("Line 0:     N0 -{/*" + Locks.toString() + "*/}-> N0\n");
    }
    if (usage.failureFlag) {
      writer.append("Line 0:     N0 -{/*Failure in refinement*/}-> N0\n");
    }
    if (usage.getPath() == null && usage.getKeyState() != null) {
      createPath(usage);
    }
    int callstackDepth = 1;
    /*
     * We must use iterator to be sure, when is the end of the list.
     * I tried to check the edge, it is the last, but it can be repeated during the sequence
     */
    Iterator<CFAEdge> iterator = usage.getPath().iterator();
    while (iterator.hasNext()) {
      CFAEdge edge = iterator.next();
      if (edge instanceof CFunctionCallEdge && iterator.hasNext()) {
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
      if (caption != null && !(edge instanceof CFunctionReturnEdge)) {
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
    if (lockTransfer != null) {
      return lockTransfer.doesChangeTheState(pEdge);
    } else {
      return null;
    }
  }

  private void countUsageStatistics(UnrefinedUsagePointSet l) {
    Iterator<UsagePoint> pointIterator = l.getPointIterator();
    while (pointIterator.hasNext()) {
      UsagePoint point = pointIterator.next();
      totalNumberOfUsagePoints++;
      UsageInfoSet uset = l.getUsageInfo(point);
      for (UsageInfo uinfo : uset.getUsages()) {
        if (uinfo.failureFlag) {
          totalFailureUsages++;
        }
        continue;
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
  }

  private void countStatistics(AbstractUsagePointSet l) {
    int startFailureNum = totalFailureUsages;

    if (l instanceof UnrefinedUsagePointSet) {
      countUsageStatistics((UnrefinedUsagePointSet)l);
    } else if (l instanceof RefinedUsagePointSet) {
      countUsageStatistics((RefinedUsagePointSet)l);
    }

    if (maxNumberOfUsagePoints < l.getNumberOfTopUsagePoints()) {
      maxNumberOfUsagePoints = l.getNumberOfTopUsagePoints();
    }
    if (totalFailureUsages > startFailureNum) {
      totalUnsafesWithFailureUsages++;
    }
  }

  private void createVisualization(final SingleIdentifier id, final Writer pWriter, boolean printOnlyTrueUnsafes) throws IOException, CPATransferException, InterruptedException {
    Writer writer = pWriter;
    final AbstractUsagePointSet uinfo = container.getUsages(id);
    final boolean isTrueUnsafe = (uinfo instanceof RefinedUsagePointSet);

    if (uinfo == null || uinfo.size() == 0) {
      return;
    }
    if (printOnlyTrueUnsafes && !isTrueUnsafe) {
      return;
    }
    Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(uinfo);
    if ((tmpPair.getFirst().failureFlag || tmpPair.getSecond().failureFlag) && printOnlyTrueUnsafes) {
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
      writer = Files.newBufferedWriter(currentPath, StandardOpenOption.WRITE);
    }
    if (isTrueUnsafe) {
    	trueUnsafes++;
      writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
    }
    writer.append("Line 0:     N0 -{/*Number of usage points:" + uinfo.getNumberOfTopUsagePoints() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Number of usages      :" + uinfo.size() + "*/}-> N0" + "\n");
    writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");

    createVisualization(id, tmpPair.getFirst(), writer);
    createVisualization(id, tmpPair.getSecond(), writer);
    dumpGraphMl(id, tmpPair);
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

  private void createPath(UsageInfo usage) {
    assert usage.getKeyState() != null;

    Set<List<Integer>> emptySet = Collections.emptySet();
    BAMMultipleCEXSubgraphComputer subgraphComputer = transfer.createBAMMultipleSubgraphComputer();
    ARGState target = (ARGState)usage.getKeyState();
    BackwardARGState newTreeTarget = new BackwardARGState(target);
    ARGState root;
    try {
      root = subgraphComputer.findPath(newTreeTarget, emptySet);
      ARGPath path = ARGUtils.getRandomPath(root);
      //path is transformed internally
      usage.resetKeyState(path.getInnerEdges());
    } catch (MissingBlockException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  int globalCounter = 0;

  private void dumpGraphMl(SingleIdentifier pId, Pair<UsageInfo, UsageInfo> pTmpPair) {
    UsageInfo firstUsage = pTmpPair.getFirst();
    UsageInfo secondUsage = pTmpPair.getSecond();
    List<CFAEdge> firstPath, secondPath;

    if (firstUsage.getPath() == null) {
      createPath(firstUsage);
    }
    firstPath = firstUsage.getPath();
    if (secondUsage.getPath() == null) {
      createPath(secondUsage);
    }
    secondPath = secondUsage.getPath();

    if (firstPath == null || secondPath == null) {
      return;
    }
    Writer w;
    String nextId, currentId = getId();
    try {
      File name = new File("output/witness." + createUniqueName(pId) + ".graphml");
      w = Files.newBufferedWriter(Paths.get(name.getAbsolutePath()), StandardOpenOption.WRITE);
      String defaultSourcefileName = firstPath.get(0).getFileLocation().getFileName();

      GraphMlBuilder builder = new GraphMlBuilder(WitnessType.VIOLATION_WITNESS, defaultSourcefileName, Language.C,
          MachineModel.LINUX64, new VerificationTaskMetaData(config, Optional.empty()));

      nextId = currentId;

      Iterator<CFAEdge> iterator;// = firstPath.iterator();
      iterator = from(firstPath)
                 .filter(e -> e.getFileLocation()!= null)
                 .iterator();
      Element result = null;

      while (iterator.hasNext()) {
        currentId = nextId;
        nextId = getId();

        do {
          CFAEdge pEdge = iterator.next();
          boolean isWarning = (pEdge.getLineNumber() == firstUsage.getLine().getLine() && pEdge.toString().contains(pId.getName()));
          String warningMessage = "";
          if (isWarning) {
            warningMessage = firstUsage.getWarningMessage();
          }
          result = prepareElement(builder, currentId, nextId, pEdge, defaultSourcefileName, "0", warningMessage);
        } while (result == null && iterator.hasNext());
      }

      iterator = from(secondPath)
          .filter(e -> e.getFileLocation()!= null)
          .iterator();
      while (iterator.hasNext()) {
        currentId = nextId;
        nextId = getId();

        do {
          CFAEdge pEdge = iterator.next();
          boolean isWarning = (pEdge.getLineNumber() == secondUsage.getLine().getLine() && pEdge.toString().contains(pId.getName()));
          String warningMessage = "";
          if (isWarning) {
            warningMessage = secondUsage.getWarningMessage();
          }
          result = prepareElement(builder, currentId, nextId, pEdge, defaultSourcefileName, "1", warningMessage);
        } while (result == null && iterator.hasNext());

        if (!iterator.hasNext()) {
          builder.addDataElementChild(result, NodeFlag.ISVIOLATION.key, "true");
        }
      }

      MoreFiles.writeFile(name.toPath(), Charset.defaultCharset(), builder);
      w.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (DOMException e1) {
      e1.printStackTrace();
    } catch (InvalidConfigurationException e1) {
      e1.printStackTrace();
    }

  }

  int idCounter = 0;
  private String getId() {
    return "A" + idCounter++;
  }

  private Element prepareElement(GraphMlBuilder builder, String currentId, String nextId, CFAEdge pEdge,
      String defaultSourcefileName, String ThreadNum, String warningMessage) {
    Element result = builder.createEdgeElement(currentId, nextId);

    if (pEdge.getSuccessor() instanceof FunctionEntryNode) {
      FunctionEntryNode in = (FunctionEntryNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONENTRY, in.getFunctionName());

    }
    if (pEdge.getSuccessor() instanceof FunctionExitNode) {
      FunctionExitNode out = (FunctionExitNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONEXIT, out.getFunctionName());
    }

    if (pEdge instanceof AssumeEdge) {
      AssumeEdge a = (AssumeEdge) pEdge;
      AssumeCase assumeCase = a.getTruthAssumption() ? AssumeCase.THEN : AssumeCase.ELSE;
      builder.addDataElementChild(result, KeyDef.CONTROLCASE, assumeCase.toString());
    }

    FileLocation location = pEdge.getFileLocation();
    if (location != null) {
      if (!location.getFileName().equals(defaultSourcefileName)) {
        builder.addDataElementChild(result, KeyDef.ORIGINFILE, location.getFileName());
      } else {
        builder.addDataElementChild(result, KeyDef.ORIGINFILE, defaultSourcefileName);
      }
      builder.addDataElementChild(result, KeyDef.STARTLINE, Integer.toString(location.getStartingLineInOrigin()));
      builder.addDataElementChild(result, KeyDef.OFFSET, Integer.toString(location.getNodeOffset()));
    } else {
      return null;
    }

    if (!pEdge.getRawStatement().trim().isEmpty()) {
      builder.addDataElementChild(result, KeyDef.SOURCECODE, pEdge.getRawStatement());
    }

    builder.addDataElementChild(result, KeyDef.THREADID, ThreadNum);

    if (!warningMessage.isEmpty()) {
      builder.addDataElementChild(result, KeyDef.WARNING, warningMessage);
    }

    return builder.createNodeElement(nextId, NodeType.ONPATH);
  }

  public void printUnsafeRawdata(final UnmodifiableReachedSet reached) {
    try {
      printStatisticsTimer.start();
      ARGState firstState = AbstractStates.extractStateByType(reached.getFirstState(), ARGState.class);
      //getLastState() returns not the correct last state
      Collection<ARGState> children = firstState.getChildren();
      if (!children.isEmpty()) {
        //Analysis finished normally
        ARGState lastState = firstState.getChildren().iterator().next();
        UsageStatisticsState USlastState = AbstractStates.extractStateByType(lastState, UsageStatisticsState.class);
        USlastState.updateContainerIfNecessary();
        container = USlastState.getContainer();
      } else {
        container = AbstractStates.extractStateByType(firstState, UsageStatisticsState.class).getContainer();
      }
      detector = container.getUnsafeDetector();
      Writer writer = null;
      try {
        if (outputFileType == OutputFileType.SINGLE_FILE) {
          writer = Files.newBufferedWriter(outputStatFileName, StandardOpenOption.WRITE);
          logger.log(Level.FINE, "Print statistics about unsafe cases");
          printCountStatistics(writer, container.getUnsafeIterator());
        }
        logger.log(Level.FINEST, "Processing unsafe identifiers");
        Iterator<SingleIdentifier> unsafeIterator;
        unsafeIterator = container.getUnsafeIterator();
        while (unsafeIterator.hasNext()) {
          createVisualization(unsafeIterator.next(), writer, container.printOnlyTrueUnsafes());
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
      if (printFalseUnsafes) {
        Set<SingleIdentifier> currentUnsafes = container.getAllUnsafes();
        Set<SingleIdentifier> initialUnsafes = container.getInitialUnsafes();
        Set<SingleIdentifier> falseUnsafes = Sets.difference(initialUnsafes, currentUnsafes);

        if (falseUnsafes.size() > 0) {
          try {
            writer = Files.newBufferedWriter(outputFalseUnsafes, StandardOpenOption.WRITE);
            logger.log(Level.FINE, "Print statistics about false unsafes");
            for (SingleIdentifier id : falseUnsafes) {
              writer.append(createUniqueName(id) + "\n");
            }
            writer.close();
          } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage());
          }
        }
      }
    } finally {
      printStatisticsTimer.stop();
    }
  }

  @Override
  public void printStatistics(final PrintStream out, final Result result, final UnmodifiableReachedSet reached) {
    resetAllCounters();
    //should be the first, as it set the container
    printUnsafeRawdata(reached);
    int unsafeSize = container.getUnsafeSize();
    out.println("Amount of unsafes:                                         " + unsafeSize);
    out.println("Amount of unsafe usages:                                   " + totalUsages + "(avg. " +
        (unsafeSize == 0 ? "0" : (totalUsages/unsafeSize))
        + ", max. " + maxNumberOfUsages + ")");
    out.println("Amount of unsafe usage points:                             " + totalNumberOfUsagePoints + "(avg. " +
        (unsafeSize == 0 ? "0" : (totalNumberOfUsagePoints/unsafeSize))
        + ", max. " + maxNumberOfUsagePoints + ")");
    out.println("Amount of true unsafes:                                    " + trueUnsafes);
    out.println("Amount of unsafes with both failures in pair:              " + totalFailureUnsafes);
    out.println("Amount of unsafes with one failure in pair:                " + totalUnsafesWithFailureUsageInPair);
    out.println("Amount of unsafes with at least once failure in usage list:" + totalUnsafesWithFailureUsages);
    out.println("Amount of usages with failure:                             " + totalFailureUsages);
    container.printUsagesStatistics(out);
    out.println("Time for transfer relation:         " + transferRelationTimer);
    out.println("Time for reseting unsafes:          " + container.resetTimer);
    out.println("Time for printing statistics:       " + printStatisticsTimer);
    out.println("Time for expanding:                 " + UsageStatisticsState.tmpTimer1);
    out.println("Time for joining:                   " + UsageStatisticsState.tmpTimer2);
    out.println("Time for joining2:                  " + UsageStatisticsState.tmpTimer3);
    out.println("Time for effect:                    " + TemporaryUsageStorage.effectTimer);
    out.println("Time for copy:                      " + TemporaryUsageStorage.copyTimer);
    out.println("Number of empty joins:              " + TemporaryUsageStorage.emptyJoin);
    out.println("Number of effect joins:             " + TemporaryUsageStorage.effectJoin);
    out.println("Number of hit joins:                " + TemporaryUsageStorage.hitTimes);
    out.println("Number of miss joins:               " + TemporaryUsageStorage.missTimes);
    out.println("Number of expanding querries:       " + TemporaryUsageStorage.totalUsages);
    out.println("Number of executed querries:        " + TemporaryUsageStorage.expandedUsages);

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
}
