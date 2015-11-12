/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usagestatistics.refinement;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.Pair;


public class RefinementResult {
  public enum RefinementStatus {
    TRUE,
    FALSE,
    UNKNOWN
  }
  private final Map<Class<? extends RefinementInterface>, Object> auxiliaryInfo = new HashMap<>();
  private final Pair<ExtendedARGPath, ExtendedARGPath> trueRace;
  RefinementStatus status;

  private RefinementResult(RefinementStatus rStatus, ExtendedARGPath firstPath, ExtendedARGPath secondPath) {
    status = rStatus;
    trueRace = Pair.of(firstPath, secondPath);
  }

  public void addInfo(Class<? extends RefinementInterface> caller, Object info) {
    auxiliaryInfo.put(caller, info);
  }

  public Object getInfo(Class<? extends RefinementInterface> caller) {
    return auxiliaryInfo.get(caller);
  }

  public boolean isTrue() {
    return status == RefinementStatus.TRUE;
  }

  public boolean isFalse() {
    return status == RefinementStatus.FALSE;
  }

  public boolean isUnknown() {
    return status == RefinementStatus.UNKNOWN;
  }

  public static RefinementResult createTrue() {
    //TODO remove this method and use smth else - true verdict should be always with info about races
    return new RefinementResult(RefinementStatus.TRUE, null, null);
  }

  public static RefinementResult createTrue(ExtendedARGPath firstPath) {
    return new RefinementResult(RefinementStatus.TRUE, firstPath, null);
  }

  public static RefinementResult createTrue(ExtendedARGPath firstPath, ExtendedARGPath secondPath) {
    return new RefinementResult(RefinementStatus.TRUE, firstPath, secondPath);
  }

  public static RefinementResult createFalse() {
    return new RefinementResult(RefinementStatus.FALSE, null, null);
  }

  public static RefinementResult createUnknown() {
    return new RefinementResult(RefinementStatus.UNKNOWN, null, null);
  }

  public Pair<ExtendedARGPath, ExtendedARGPath> getTrueRace() {
    return trueRace;
  }

  @Override
  public String toString() {
    return status.name();
  }
}
