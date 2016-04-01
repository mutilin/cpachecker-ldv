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
package org.sosy_lab.cpachecker.cpa.smg;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.objects.dls.SMGDoublyLinkedListCandidateFinder;
import org.sosy_lab.cpachecker.cpa.smg.objects.sll.SMGSingleLinkedListFinder;

public class SMGAbstractionManager {
  private final CLangSMG smg;
  private final SMGState smgState;
  private List<SMGAbstractionCandidate> abstractionCandidates = new ArrayList<>();
  private final boolean onlyDll;

  public SMGAbstractionManager(CLangSMG pSMG, SMGState pSMGstate) {
    smg = pSMG;
    onlyDll = false;
    smgState = pSMGstate;
  }

  public SMGAbstractionManager(CLangSMG pSMG, boolean pOnlyDll, SMGState pSMGstate) {
    smg = pSMG;
    onlyDll = pOnlyDll;
    smgState = pSMGstate;
  }

  private boolean hasCandidates() throws SMGInconsistentException {
    SMGDoublyLinkedListCandidateFinder dllCandidateFinder =
        new SMGDoublyLinkedListCandidateFinder();

    Set<SMGAbstractionCandidate> candidates = dllCandidateFinder.traverse(smg, smgState);
    abstractionCandidates.addAll(candidates);

    if (!onlyDll) {
      SMGSingleLinkedListFinder sllCandidateFinder =
          new SMGSingleLinkedListFinder();
      abstractionCandidates.addAll(sllCandidateFinder.traverse(smg, smgState));
    }

    return (!abstractionCandidates.isEmpty());
  }

  private SMGAbstractionCandidate getBestCandidate() {

    SMGAbstractionCandidate bestCandidate = abstractionCandidates.get(0);

    for (SMGAbstractionCandidate candidate : abstractionCandidates) {
      if (candidate.getScore() > bestCandidate.getScore()) {
        bestCandidate = candidate;
      }
    }

    return bestCandidate;
  }

  public void execute() throws SMGInconsistentException {
    while (hasCandidates()) {
      SMGAbstractionCandidate best = getBestCandidate();
      best.execute(smg, smgState);
      invalidateCandidates();
    }
  }

  private void invalidateCandidates() {
    abstractionCandidates.clear();
  }
}
