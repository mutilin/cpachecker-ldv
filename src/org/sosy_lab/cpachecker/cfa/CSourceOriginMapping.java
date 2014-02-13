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
package org.sosy_lab.cpachecker.cfa;

import org.sosy_lab.common.Pair;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;


public enum CSourceOriginMapping {
  INSTANCE;

  private Boolean oneInputLinePerToken = null;
  private boolean frozen = false;

  public final RangeMap<Integer, String> lineToFilenameMapping = TreeRangeMap.create();
  public final RangeMap<Integer, Integer> lineDeltaMapping = TreeRangeMap.create();
  public final RangeMap<Integer, Integer> tokenToLineMapping = TreeRangeMap.create();

  public boolean getHasOneInputLinePerToken() {
    Preconditions.checkNotNull(oneInputLinePerToken);
    return oneInputLinePerToken;
  }

  public void setHasOneInputLinePerToken(boolean pOneInputLinePerToken) {
    if (frozen) {
      return;
    }

    oneInputLinePerToken = pOneInputLinePerToken;
  }

  public void mapTokenRangeToInputLine(int fromTokenNumber, int toTokenNumber, int inputLineNumber) {
    Preconditions.checkNotNull(oneInputLinePerToken);
    if (frozen) {
      return;
    }

    Range<Integer> tokenRange = Range.openClosed(fromTokenNumber-1, toTokenNumber);
    tokenToLineMapping.put(tokenRange, inputLineNumber);
  }

  public void mapInputLineRangeToDelta(String originFilename, int fromInputLineNumber, int toInputLineNumber, int deltaLinesToOrigin) {
    Preconditions.checkNotNull(oneInputLinePerToken);
    if (frozen) {
      return;
    }

    Range<Integer> lineRange = Range.openClosed(fromInputLineNumber-1, toInputLineNumber);
    lineToFilenameMapping.put(lineRange, originFilename);
    lineDeltaMapping.put(lineRange, deltaLinesToOrigin);
  }

  public Pair<String, Integer> getOriginLineFromAnalysisCodeLine(int analysisCodeLine) {
    int inputLine = analysisCodeLine;
    if (oneInputLinePerToken) {
      inputLine = tokenToLineMapping.get(analysisCodeLine);
    }
    return Pair.of(lineToFilenameMapping.get(analysisCodeLine), inputLine + lineDeltaMapping.get(inputLine));
  }

  public synchronized void freeze() {
    frozen = true;
  }
}
