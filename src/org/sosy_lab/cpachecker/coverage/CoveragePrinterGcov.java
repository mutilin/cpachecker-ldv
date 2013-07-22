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
package org.sosy_lab.cpachecker.coverage;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class CoveragePrinterGcov implements CoveragePrinter {

  public class FunctionInfo {
    final String name;
    final int firstLine;
    final int lastLine;

    public FunctionInfo(String pName, int pFirstLine, int pLastLine) {
      name = pName;
      firstLine = pFirstLine;
      lastLine = pLastLine;
    }
  }

  Set<Integer> visitedLines;
  Set<Integer> allLines;
  Set<String> visitedFunctions;
  Set<FunctionInfo> allFunctions;
  Set<Integer> functionBeginnings;

  private final static String TEXTNAME = "TN";
  private final static String SOURCEFILE = "SF";
  private final static String FUNCTION = "FN";
  private final static String FUNCTIONDATA = "FNDA";
  private final static String LINEDATA = "DA";

  public CoveragePrinterGcov() {
    visitedLines = new HashSet<>();
    allLines = new HashSet<>();
    visitedFunctions = new HashSet<>();
    allFunctions = new HashSet<>();
    functionBeginnings = new HashSet<>();
  }

  @Override
  public void addVisitedFunction(String pName) {
    visitedFunctions.add(pName);
  }

  @Override
  public void addExistedFunction(String pName, int pFirstLine, int pLastLine) {
    allFunctions.add(new FunctionInfo(pName, pFirstLine, pLastLine));
    functionBeginnings.add(pFirstLine);
  }

  @Override
  public void addVisitedLine(int pLine) {
    visitedLines.add(pLine);
  }

  @Override
  public void addExistedLine(int pLine) {
    allLines.add(pLine);
  }

  @Override
  public void print(String outputFile, String originFile) {
    try {
      PrintWriter out = new PrintWriter(outputFile);

      out.println(TEXTNAME + ":");
      out.println(SOURCEFILE + ":" + originFile);

      for (FunctionInfo info : allFunctions) {
        out.println(FUNCTION + ":" + info.firstLine + "," + info.name);
        //Information about function end isn't used by lcov, but it is useful for some postprocessing
        //But lcov ignores all unknown lines, so, this additional information can't affect on its work
        out.println("#" + FUNCTION + ":" + info.lastLine);
      }

      for (String name : visitedFunctions) {
        out.println(FUNCTIONDATA + ":" + "1," + name);
      }

      /* Now, get all lines, which are used in cfa (except comments, empty lines and so on)
       *
       * Here we could add to lineUsage only 'node.getLineNumber()',
       * but after experiments we made this part of code more complicated,
       * because some interesting lines (such as 'return 0') wasn't included.
       *
       * Also, it is important, that all nodes from MultiEdge are deleted from cfa!
       * We should add them to our statistics, because they are visited by CPA.
       */
      /*for (CFANode node : cfa.getAllNodes()) {
        //we shouldn't consider declarations
        if (isDeclarationNode(node)) {
          continue;
        }

        //All locations, which are in MultiEdge are thrown away, so, we should return them by ourselves
        for (int i = 0; i < node.getNumLeavingEdges(); i++) {
          CFAEdge tmpEdge = node.getLeavingEdge(i);
          if (tmpEdge instanceof MultiEdge) {
            ImmutableList<CFAEdge> edges = ((MultiEdge)tmpEdge).getEdges();
            for (CFAEdge singleEdge : edges) {
              if (!(singleEdge instanceof CDeclarationEdge)) {
                lineUsage.add(singleEdge.getLineNumber());
              }
            }
          }
        }
        //We add line number, if it isn't ExitNode
        if (node instanceof FunctionExitNode && ((FunctionExitNode)node).getNumEnteringEdges() > 0) {
          //'return' lines are missed, so add line number of entering edges
          FunctionExitNode exit = (FunctionExitNode)node;
          for (int i = 0; i < exit.getNumEnteringEdges(); i++) {
            CFAEdge tmpEdge = exit.getEnteringEdge(i);
            lineUsage.add(tmpEdge.getLineNumber());
          }
        } else {
          lineUsage.add(node.getLineNumber());
        }
      }*/

      /* Now save information about lines
       */
      for (Integer line : allLines) {
        /* Some difficulties: all function beginnings are visited at the beginning of analysis
         * without entering function.
         * So, we should mark these lines, as visited, if the function is really visited later.
         */
        if (functionBeginnings.contains(line)) {
          //We should mark it, as visited, if the function is analyzed
          //TODO make it better
          for (FunctionInfo info : allFunctions) {
            if (info.firstLine == line) {
              out.println(LINEDATA + ":" + line + "," + (visitedFunctions.contains(info.name) ? 1 : 0));
            }
          }
        } else {
          out.println(LINEDATA + ":" + line + "," + (visitedLines.contains(line) ? 1 : 0));
        }
      }
      out.println("end_of_record");
      out.close();
    } catch(FileNotFoundException e) {
      System.err.println("Cannot open output file " + outputFile);
    }
  }


}
