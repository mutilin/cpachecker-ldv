/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.blockator;

import org.sosy_lab.cpachecker.core.interfaces.AbstractEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;

public class BlockatorApplyOperator implements ApplyOperator {
  private BlockatorCPATM parent;
  private ApplyOperator applyOperator;

  public BlockatorApplyOperator(
      BlockatorCPATM pParent,
      ApplyOperator pApplyOperator) {
    parent = pParent;
    applyOperator = pApplyOperator;
  }

  @Override
  public AbstractState apply(AbstractState pState1, AbstractState pState2) {
    AbstractState ret = applyOperator.apply(pState1, pState2);
    parent.getStateRegistry().copy(pState1, ret);
    return ret;
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild) {
    AbstractState ret = applyOperator.project(pParent, pChild);
    parent.getStateRegistry().copy(pParent, ret);
    return ret;
  }

  @Override
  public AbstractState project(AbstractState pParent, AbstractState pChild, AbstractEdge pEdge) {
    AbstractState ret = applyOperator.project(pParent, pChild, pEdge);
    parent.getStateRegistry().copy(pParent, ret);
    return ret;
  }

  @Override
  public boolean isInvariantToEffects(AbstractState pState) {
    return applyOperator.isInvariantToEffects(pState);
  }

  @Override
  public boolean canBeAnythingApplied(AbstractState pState) {
    return applyOperator.canBeAnythingApplied(pState);
  }
}
