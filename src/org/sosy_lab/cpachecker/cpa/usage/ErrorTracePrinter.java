/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.BackwardARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMSubgraphComputer.MissingBlockException;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.RefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnrefinedUsagePointSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UnsafeDetector;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageInfoSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

@Options(prefix="cpa.usage")
public abstract class ErrorTracePrinter {

  @Option(name="falseUnsafesOutput", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputFalseUnsafes = Paths.get("FalseUnsafes");

  @Option(description="print all unsafe cases in report")
  private boolean printFalseUnsafes = false;

  private final BAMTransferRelation transfer;
  private UnsafeDetector detector;

  private int totalUsages = 0;
  private int totalNumberOfUsagePoints = 0;
  private int maxNumberOfUsagePoints = 0;
  private int maxNumberOfUsages = 0;

  private int totalFailureUsages = 0;
  private int totalFailureUnsafes = 0;
  private int totalUnsafesWithFailureUsages = 0;
  private int totalUnsafesWithFailureUsageInPair = 0;
  private int trueUnsafes = 0;

  protected final Configuration config;
  protected UsageContainer container;
  protected final LogManager logger;

  protected final static Predicate<CFAEdge> FILTER_EMPTY_FILE_LOCATIONS =
      e -> e.getFileLocation()!= null && (Files.exists(Paths.get(e.getFileLocation().getFileName())));


  public ErrorTracePrinter(Configuration c, BAMTransferRelation t, LogManager l) {
    transfer = t;
    logger = l;
    config = c;
  }

  protected void createPath(UsageInfo usage) {
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
      usage.setRefinedPath(path.getInnerEdges());
    } catch (MissingBlockException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  protected String createUniqueName(SingleIdentifier id) {
    String name = id.getType().toASTString(id.getName());
    name = name.replace(" ", "_");
    return name;
  }

  public void printErrorTraces(UnmodifiableReachedSet reached) {
    ARGState firstState = AbstractStates.extractStateByType(reached.getFirstState(), ARGState.class);
    //getLastState() returns not the correct last state
    Collection<ARGState> children = firstState.getChildren();
    if (!children.isEmpty()) {
      //Analysis finished normally
      ARGState lastState = firstState.getChildren().iterator().next();
      UsageState USlastState = AbstractStates.extractStateByType(lastState, UsageState.class);
      USlastState.updateContainerIfNecessary();
      container = USlastState.getContainer();
    } else {
      container = AbstractStates.extractStateByType(firstState, UsageState.class).getContainer();
    }
    detector = container.getUnsafeDetector();

    logger.log(Level.FINEST, "Processing unsafe identifiers");
    Iterator<SingleIdentifier> unsafeIterator;
    unsafeIterator = container.getUnsafeIterator();
    boolean printOnlyTrueUnsafes = container.printOnlyTrueUnsafes();

    init();
    while (unsafeIterator.hasNext()) {
      SingleIdentifier id = unsafeIterator.next();
      final AbstractUsagePointSet uinfo = container.getUsages(id);
      final boolean isTrueUnsafe = (uinfo instanceof RefinedUsagePointSet);

      if (uinfo == null || uinfo.size() == 0) {
        continue;
      }
      if (printOnlyTrueUnsafes && !isTrueUnsafe) {
        continue;
      }

      Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(uinfo);
      if ((tmpPair.getFirst().isLooped() || tmpPair.getSecond().isLooped()) && printOnlyTrueUnsafes) {
        continue;
      }
      countStatistics(uinfo);
      totalUsages += uinfo.size();
      if (uinfo.size() > maxNumberOfUsages) {
        maxNumberOfUsages = uinfo.size();
      }
      if (isTrueUnsafe) {
        trueUnsafes++;
      }
      if (tmpPair.getFirst().isLooped() && tmpPair.getSecond().isLooped()) {
        totalFailureUnsafes++;
      } else if (tmpPair.getFirst().isLooped() || tmpPair.getSecond().isLooped()) {
        totalUnsafesWithFailureUsageInPair++;
      }
      printUnsafe(id, tmpPair);
    }
    if (printFalseUnsafes) {
      Set<SingleIdentifier> currentUnsafes = container.getAllUnsafes();
      Set<SingleIdentifier> initialUnsafes = container.getInitialUnsafes();
      Set<SingleIdentifier> falseUnsafes = Sets.difference(initialUnsafes, currentUnsafes);

      if (falseUnsafes.size() > 0) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(outputFalseUnsafes.toString()), Charset.defaultCharset())) {
          logger.log(Level.FINE, "Print statistics about false unsafes");
          for (SingleIdentifier id : falseUnsafes) {
            writer.append(createUniqueName(id) + "\n");
          }
        } catch (IOException e) {
          logger.log(Level.SEVERE, e.getMessage());
        }
      }
    }
    finish();
  }

  private void countUsageStatistics(UnrefinedUsagePointSet l) {
    Iterator<UsagePoint> pointIterator = l.getPointIterator();
    while (pointIterator.hasNext()) {
      UsagePoint point = pointIterator.next();
      totalNumberOfUsagePoints++;
      UsageInfoSet uset = l.getUsageInfo(point);
      for (UsageInfo uinfo : uset.getUsages()) {
        if (uinfo.isLooped()) {
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
    if (first.isLooped()) {
      totalFailureUsages++;
    }
    if (second.isLooped() && first != second) {
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

  public void printStatistics(final PrintStream out) {

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
    out.println("Time for reseting unsafes:          " + container.resetTimer);
  }

  protected abstract void printUnsafe(SingleIdentifier id, Pair<UsageInfo, UsageInfo> pair);
  protected void init() {}
  protected void finish() {}
}
