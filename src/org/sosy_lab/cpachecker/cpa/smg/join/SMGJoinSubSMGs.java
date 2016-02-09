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
package org.sosy_lab.cpachecker.cpa.smg.join;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionCandidate;
import org.sosy_lab.cpachecker.cpa.smg.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.graphs.SMG;
import org.sosy_lab.cpachecker.cpa.smg.objects.SMGObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


final class SMGJoinSubSMGs {
  static private boolean performChecks = false;
  static public void performChecks(boolean pValue) {
    performChecks = pValue;
  }

  private SMGJoinStatus status;
  private boolean defined = false;

  private SMG inputSMG1;
  private SMG inputSMG2;
  private SMG destSMG;

  private SMGNodeMapping mapping1 = null;
  private SMGNodeMapping mapping2 = null;
  private final List<SMGAbstractionCandidate> subSmgAbstractionCandidates;

  public SMGJoinSubSMGs(SMGJoinStatus initialStatus,
                        SMG pSMG1, SMG pSMG2, SMG pDestSMG,
                        SMGNodeMapping pMapping1, SMGNodeMapping pMapping2,
                        SMGObject pObj1, SMGObject pObj2, SMGObject pNewObject) throws SMGInconsistentException{

    SMGJoinFields joinFields = new SMGJoinFields(pSMG1, pSMG2, pObj1, pObj2);

    inputSMG1 = joinFields.getSMG1();
    inputSMG2 = joinFields.getSMG2();

    if (SMGJoinSubSMGs.performChecks) {
      SMGJoinFields.checkResultConsistency(inputSMG1, inputSMG2, pObj1, pObj2);
    }

    destSMG = pDestSMG;
    status = SMGJoinStatus.updateStatus(initialStatus, joinFields.getStatus());
    mapping1 = pMapping1;
    mapping2 = pMapping2;

    /*
     * After joinFields, the objects have identical set of fields. Therefore, to iterate
     * over them, it is sufficient to loop over HV set in the first SMG, and just
     * obtain the (always just single one) corresponding edge from the second
     * SMG.
     */

    SMGEdgeHasValueFilter filterOnSMG1 = SMGEdgeHasValueFilter.objectFilter(pObj1);
    SMGEdgeHasValueFilter filterOnSMG2 = SMGEdgeHasValueFilter.objectFilter(pObj2);

    Set<SMGEdgeHasValue> edgesOnObject1 = Sets.newHashSet(inputSMG1.getHVEdges(filterOnSMG1));

    Map<Integer, List<SMGAbstractionCandidate>> valueAbstractionCandidates = new HashMap<>();
    boolean allValuesDefined = true;

    for (SMGEdgeHasValue hvIn1 : edgesOnObject1) {
      filterOnSMG2.filterAtOffset(hvIn1.getOffset());
      filterOnSMG2.filterByType(hvIn1.getType());
      SMGEdgeHasValue hvIn2 = Iterables.getOnlyElement(inputSMG2.getHVEdges(filterOnSMG2));

      SMGJoinValues joinValues = new SMGJoinValues(status, inputSMG1, inputSMG2, destSMG,
          mapping1, mapping2, hvIn1.getValue(), hvIn2.getValue() /*, ldiff */);

      /* If the join of the values is not defined and can't be
       * recovered through abstraction, the join fails.*/
      if (!joinValues.isDefined() && !joinValues.isRecoverable()) {
        subSmgAbstractionCandidates = ImmutableList.of();
        return;
      }

      status = joinValues.getStatus();
      inputSMG1 = joinValues.getInputSMG1();
      inputSMG2 = joinValues.getInputSMG2();
      destSMG = joinValues.getDestinationSMG();
      mapping1 = joinValues.getMapping1();
      mapping2 = joinValues.getMapping2();

      if (joinValues.isDefined()) {
        SMGEdgeHasValue newHV = new SMGEdgeHasValue(hvIn1.getType(), hvIn1.getOffset(), pNewObject,
            joinValues.getValue());
        destSMG.addHasValueEdge(newHV);

        if(joinValues.subSmgHasAbstractionsCandidates()) {
          valueAbstractionCandidates.put(joinValues.getValue(), joinValues.getAbstractionCandidates());
        }
      } else {
        allValuesDefined = false;
      }
    }

    /* If the join is defined without abstraction candidates in
       sub smgs, we don't need to perform abstraction.*/
    if (allValuesDefined && valueAbstractionCandidates.isEmpty()) {
      defined = true;
      subSmgAbstractionCandidates = ImmutableList.of();
      return;
    }

    SMGJoinAbstractionManager abstractionManager = new SMGJoinAbstractionManager(pObj1, pObj2, inputSMG1, inputSMG2, pNewObject, destSMG);
    subSmgAbstractionCandidates = abstractionManager.calculateCandidates(valueAbstractionCandidates);

    /*If abstraction candidates can be found for this sub Smg, then the join for this sub smg
     *  is defined under the assumption, that the abstraction of one abstraction candidate is executed.*/
    if (!subSmgAbstractionCandidates.isEmpty()) {
      defined = true;
      return;
    }

    /* If no abstraction can be found for this sub Smg, then the join is only defined,
     * if all values are defined. For values that are defined under the assumption,
     * that a abstraction candidate is execued for the destination smg, execute the abstraction
     * so that the join of this sub SMG is complete.*/
    if(!allValuesDefined) {
      defined = false;
      return;
    }

    for(List<SMGAbstractionCandidate> abstractionCandidates : valueAbstractionCandidates.values()) {
      Collections.sort(abstractionCandidates);
      abstractionCandidates.get(0).execute(destSMG);
    }

    defined = true;
  }

  public boolean isDefined() {
    return defined;
  }

  public SMGJoinStatus getStatus() {
    return status;
  }

  public SMG getSMG1() {
    return inputSMG1;
  }

  public SMG getSMG2() {
    return inputSMG2;
  }

  public SMG getDestSMG() {
    return destSMG;
  }

  public SMGNodeMapping getMapping1() {
    return mapping1;
  }

  public SMGNodeMapping getMapping2() {
    return mapping2;
  }

  public List<SMGAbstractionCandidate> getSubSmgAbstractionCandidates() {
    return subSmgAbstractionCandidates;
  }
}