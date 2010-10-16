/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;

import org.sosy_lab.common.Files;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

import com.google.common.base.Joiner;

@Options
class MainCPAStatistics implements Statistics {

    @Option(name="reachedSet.export")
    private boolean exportReachedSet = true;

    @Option(name="reachedSet.file", type=Option.Type.OUTPUT_FILE)
    private File outputFile = new File("reached.txt");

    private final LogManager logger;
    private final Collection<Statistics> subStats;
    private long programStartingTime;
    private long analysisStartingTime;
    private long analysisEndingTime;

    public MainCPAStatistics(Configuration config, LogManager logger) throws InvalidConfigurationException {
        config.inject(this);

        this.logger = logger;
        subStats = new ArrayList<Statistics>();
        programStartingTime = 0;
        analysisStartingTime = 0;
        analysisEndingTime = 0;
    }

    public void startProgramTimer() {
        programStartingTime = System.currentTimeMillis();
    }

    public void startAnalysisTimer() {
        analysisStartingTime = System.currentTimeMillis();
    }

    public void stopAnalysisTimer() {
        analysisEndingTime = System.currentTimeMillis();
    }

    public Collection<Statistics> getSubStatistics() {
      return subStats;
  }

    @Override
    public String getName() {
        return "CPAchecker";
    }

    @Override
    public void printStatistics(PrintStream out, Result result, ReachedSet reached) {
        if (analysisEndingTime == 0) {
          stopAnalysisTimer();
        }

        if (exportReachedSet && outputFile != null) {
          try {
            Files.writeFile(outputFile, Joiner.on('\n').join(reached));
          } catch (IOException e) {
            logger.log(Level.WARNING,
                "Could not write reached set to file (", e.getMessage(), ")");
          } catch (OutOfMemoryError e) {
            logger.log(Level.WARNING,
                "Could not write reached set to file due to memory problems (", e.getMessage(), ")");
          }
        }

        long totalTimeInMillis = analysisEndingTime - analysisStartingTime;
        long totalAbsoluteTimeMillis = analysisEndingTime - programStartingTime;

        out.println("\nCPAchecker general statistics:");
        out.println("------------------------------");
        out.println("Size of reached set: " + reached.size());
        out.println("Total Time Elapsed: " + toTime(totalTimeInMillis));
        out.println("Total Time Elapsed including CFA construction: " +
                toTime(totalAbsoluteTimeMillis));

        for (Statistics s : subStats) {
            String name = s.getName();
            if (name != null && !name.isEmpty()) {
              out.println("");
              out.println(name);
              char[] c = new char[name.length()];
              Arrays.fill(c, '-');
              out.println(String.copyValueOf(c));
            }
            s.printStatistics(out, result, reached);
        }

        out.println("");
        out.print("Error location(s) reached? ");
        switch (result) {
        case UNKNOWN:
          out.println("UNKNOWN, analysis has not completed\n\n" +
              "***********************************************************************\n" +
              "* WARNING: Analysis interrupted!! The statistics might be unreliable! *\n" +
              "***********************************************************************"
            );
          break;
        case UNSAFE:
          out.println("YES, there is a BUG!");
          break;
        case SAFE:
          out.println("NO, the system is considered safe by the chosen CPAs");
          break;
        default:
          out.println("UNKNOWN result: " + result);
        }
        out.flush();
    }

    private String toTime(long timeMillis) {
        return String.format("% 5d.%03ds", timeMillis/1000, timeMillis%1000);
    }
}
