/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.cover;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Files;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;

@Options
public class CoverCPAStatistics implements Statistics {

  @Option(name="coverage.output", description="print information about coverage")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputFile = Paths.get("coverage.info");

  private final String originFile;
  private final CFA cfa;
  private final Set<String> CoveredFunctions;
  private final Set<Integer> DeclarationLines;

  private final static String TEXTNAME = "TN";
  private final static String SOURCEFILE = "SF";
  private final static String FUNCTION = "FN";
  private final static String FUNCTIONDATA = "FNDA";
  private final static String FUNCTIONFOUND = "FNF";
  private final static String FUNCTIONCOVERED = "FNH";
  private final static String LINEDATA = "DA";

  CoverCPAStatistics(Configuration config, String file, CFA pCfa, Set<String> used, Set<Integer> lines) throws InvalidConfigurationException {
    config.inject(this);
    originFile = file;
    cfa = pCfa;
    CoveredFunctions = used;
    DeclarationLines = lines;
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {
    Boolean inCoveredFunction = false;
    int lineNumber = 0;
    int FNH = 0;
    Set<Integer> lineUsage = new HashSet<>();
    Map<Integer, Boolean> functionHeads = new HashMap<>();

    CoveredFunctions.add(cfa.getMainFunction().getFunctionName());

    try {
      BufferedReader in = new BufferedReader(new FileReader(originFile));

      try {
        PrintWriter out = new PrintWriter(Files.openOutputFile(outputFile));

        out.println(TEXTNAME + ":");
        out.println(SOURCEFILE + ":" + originFile);

        for (FunctionEntryNode entry : cfa.getAllFunctionHeads()) {
          functionHeads.put(entry.getLineNumber(), CoveredFunctions.contains(entry.getFunctionName()));
          out.println(FUNCTION + ":" + entry.getLineNumber() + "," + entry.getFunctionName());
          //Information about function end isn't used by lcov, but it is useful for some postprocessing
          //But lcov ignores all unknown lines, so, this additional information can't affect on its work
          out.println("#" + FUNCTION + ":" + entry.getExitNode().getLineNumber());
        }

        Set<String> allFunctionNames = cfa.getAllFunctionNames();
        for (String name : CoveredFunctions) {
          if (allFunctionNames.contains(name)) {
            out.println(FUNCTIONDATA + ":" + "1," + name);
            FNH++;
          }
        }

        out.println(FUNCTIONFOUND + ":" + cfa.getNumberOfFunctions());
        out.println(FUNCTIONCOVERED + ":" + FNH);


        /* Now, get all lines, which are used in cfa (except comments, empty lines and so on)
         *
         * Here we could add to lineUsage only 'node.getLineNumber()',
         * but after experiments we made this part of code more complicated,
         * because some interesting lines (such as 'return 0') wasn't included.
         */
        for (CFANode node : cfa.getAllNodes()) {
          //We add line number, if it isn't ExitNode
          if (node instanceof FunctionExitNode && ((FunctionExitNode)node).getNumEnteringEdges() > 0) {
            //'return' lines are missed, so add line number of entering edges
            FunctionExitNode exit = (FunctionExitNode)node;
            for (int i = 0; i < exit.getNumEnteringEdges(); i++) {
              CFAEdge tmpEdge = ((FunctionExitNode)node).getEnteringEdge(i);
              lineUsage.add(tmpEdge.getLineNumber());
            }
          } else {
            lineUsage.add(node.getLineNumber());
          }
        }

        /* Now save information about lines
         *
         * Function is covered => all its lines are covered
         * Global declarations are covered always
         */
        while (in.readLine() != null) {
          lineNumber++;

          if (functionHeads.containsKey(lineNumber)) {
            inCoveredFunction = functionHeads.get(lineNumber);
            if (inCoveredFunction) {
              out.println(LINEDATA + ":" + lineNumber +",1");
            } else {
              out.println(LINEDATA + ":" + lineNumber +",0");
            }

          } else if (lineUsage.contains(lineNumber)) {
            if (inCoveredFunction || DeclarationLines.contains(lineNumber)) {
              out.println(LINEDATA + ":" + lineNumber +",1");
            } else {
              out.println(LINEDATA + ":" + lineNumber +",0");
            }
          }
        }
        out.println("end_of_record");
        out.close();
      } catch(FileNotFoundException e) {
        System.err.println("Cannot open output file " + outputFile);
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        in.close();
      }
    } catch(FileNotFoundException e) {
      System.out.println("Cannot open input file " + originFile);
    } catch(IOException e) {
      e.printStackTrace();
    }

  }

  @Override
  public String getName() {
    //we don't write any statistics to 'pOut'
    return null;
  }

}
