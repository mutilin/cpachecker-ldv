/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.lockstatistics.effects;

import org.sosy_lab.cpachecker.cpa.lockstatistics.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsState.LockStatisticsStateBuilder;

import com.google.common.base.Preconditions;


public class AcquireLockEffect extends LockEffect {

  private final static AcquireLockEffect instance = new AcquireLockEffect();

  private final int maxRecursiveCounter;

  private AcquireLockEffect(LockIdentifier id, int t) {
    super(id);
    maxRecursiveCounter = t;
  }

  private AcquireLockEffect() {
    this(null, Integer.MAX_VALUE);
  }

  @Override
  public void effect(LockStatisticsStateBuilder pBuilder) {
    Preconditions.checkArgument(target != null, "Lock identifier must be set");
    int previousP = pBuilder.getOldState().getCounter(target);
    if (maxRecursiveCounter > previousP) {
      pBuilder.add(target);
    }
  }

  public static AcquireLockEffect getInstance() {
    return instance;
  }

  public static AcquireLockEffect createEffectForId(LockIdentifier id, int counter) {
    return new AcquireLockEffect(id, counter);
  }

  @Override
  public AcquireLockEffect cloneWithTarget(LockIdentifier id) {
    return createEffectForId(id, this.maxRecursiveCounter);
  }
}
