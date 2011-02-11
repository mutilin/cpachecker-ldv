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
package org.sosy_lab.cpachecker.cpa.assumptions.progressobserver.heuristics;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cpa.assumptions.progressobserver.StopHeuristicsData;
import org.sosy_lab.cpachecker.util.assumptions.HeuristicToFormula.PreventingHeuristicType;

/**
 * @author g.theoduloz
 */
public class TimeOutHeuristicsData implements StopHeuristicsData {

  private final boolean stop;
  private final long time;
  private long threshold;

  public TimeOutHeuristicsData(long pTime, boolean pStop) {
    stop = pStop;
    time = pTime;
  }

  @Override
  public boolean isBottom() {
    return stop;
  }

  @Override
  public boolean isLessThan(StopHeuristicsData data) {
    return stop || !((TimeOutHeuristicsData)data).stop;
  }

  @Override
  public boolean isTop() {
    return !stop;
  }

  public long getTime() {
    return time;
  }

  public static final TimeOutHeuristicsData BOTTOM = new TimeOutHeuristicsData(0, true);

  @Override
  public boolean shouldTerminateAnalysis() {
    return true;
  }

  protected void setThreshold(long pThreshold) {
    threshold = pThreshold;
  }
  
  @Override
  public Pair<PreventingHeuristicType, Long> getPreventingCondition() {
    if(stop)
      return Pair.of(PreventingHeuristicType.TIMEOUT, threshold);
    return null;
  }

}
