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
package org.sosy_lab.cpachecker.util.coverage;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.sosy_lab.common.io.Paths;

/**
 * Generate coverage information in Gcov format
 * (http://gcc.gnu.org/onlinedocs/gcc/Gcov.html).
 */
class CoveragePrinterGcov implements CoveragePrinter {

  private static class FunctionInfo {
    private final String name;
    private final int firstLine;
    private final int lastLine;

    FunctionInfo(String pName, int pFirstLine, int pLastLine) {
      name = pName;
      firstLine = pFirstLine;
      lastLine = pLastLine;
    }
  }

  private final BitSet visitedLines = new BitSet();
  private final Set<Integer> allLines = new HashSet<>();
  private final Set<String> visitedFunctions = new HashSet<>();
  private final Set<FunctionInfo> allFunctions = new HashSet<>();

  //String constants from gcov format
  private final static String TEXTNAME = "TN:";
  private final static String SOURCEFILE = "SF:";
  private final static String FUNCTION = "FN:";
  private final static String FUNCTIONDATA = "FNDA:";
  private final static String LINEDATA = "DA:";

  @Override
  public void addVisitedFunction(String pName) {
    visitedFunctions.add(pName);
  }

  @Override
  public void addExistingFunction(String pName, int pFirstLine, int pLastLine) {
    allFunctions.add(new FunctionInfo(pName, pFirstLine, pLastLine));
  }

  @Override
  public void addVisitedLine(int pLine) {
    checkArgument(pLine > 0);
    visitedLines.set(pLine);
  }

  @Override
  public void addExistingLine(int pLine) {
    checkArgument(pLine > 0);
    allLines.add(pLine);
  }

  @Override
  public void print(Appendable out, String originFile) throws IOException {
    //Convert ./test.c -> /full/path/test.c
    out.append(TEXTNAME + "\n");
    out.append(SOURCEFILE + Paths.get(originFile).getAbsolutePath() + "\n");

    for (FunctionInfo info : allFunctions) {
      out.append(FUNCTION + info.firstLine + "," + info.name + "\n");
      //Information about function end isn't used by lcov, but it is useful for some postprocessing
      //But lcov ignores all unknown lines, so, this additional information can't affect on its work
      out.append("#" + FUNCTION + info.lastLine + "\n");
    }

    for (String name : visitedFunctions) {
      out.append(FUNCTIONDATA + "1," + name + "\n");
    }

    /* Now save information about lines
     */
    for (Integer line : allLines) {
      out.append(LINEDATA + line + "," + (visitedLines.get(line) ? 1 : 0) + "\n");
    }
    out.append("end_of_record\n");
  }
}
