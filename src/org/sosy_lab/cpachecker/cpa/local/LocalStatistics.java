/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.local;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.util.AbstractStates;


public class LocalStatistics implements Statistics {

  private String outputFileName = "output/localsave";
  private final LogManager logger;

  public LocalStatistics(Configuration pConfig, LogManager pLogger) {
    logger = pLogger;
    String fName = pConfig.getProperty("precision.path");
    if (fName != null) {
      outputFileName = fName;
    }
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
    try {
      Map<CFANode, LocalState> reachedStatistics = new TreeMap<>();
      Path p = Paths.get(outputFileName);
      Writer writer = Files.openOutputFile(Paths.get(p.getAbsolutePath()));
      logger.log(Level.FINE, "Write precision to " + outputFileName);
      for (AbstractState state : pReached.asCollection()) {
        CFANode node = AbstractStates.extractLocation(state);
        LocalState lState = AbstractStates.extractStateByType(state, LocalState.class);
        if (!reachedStatistics.containsKey(node)) {
          reachedStatistics.put(node, lState);
        } else {
          LocalState previousState = reachedStatistics.get(node);
          reachedStatistics.put(node, previousState.join(lState));
        }
      }
      for (CFANode node : reachedStatistics.keySet()) {
        writer.append(node.toString() + "\n");
        writer.append(reachedStatistics.get(node).toLog() + "\n");
      }
      writer.close();
    } catch(FileNotFoundException e) {
      System.out.println("Cannot open file " + outputFileName);
      return;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getName() {
    return "LocalCPA";
  }

}
