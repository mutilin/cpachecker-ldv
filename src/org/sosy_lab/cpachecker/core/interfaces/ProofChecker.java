/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.interfaces;

import java.util.Collection;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

/**
 * Interface for classes representing a analysis that can be proof checked.
 */
public interface ProofChecker
{
  /**
   * Checks whether the given set of abstract successors correctly over-approximates the set of concrete successors the
   * concretisations of the given abstract element has with respect to the given CFA edge. If the given edge is <code>null</code>
   * all CFA edges have to be considered.
   * @param element abstract element with current state
   * @param cfaEdge null or an edge of the CFA
   * @param successors list of all successors of the current state (may be empty)
   * @return <code>true</code> if successors are valid over-approximation; <code>false</code>, otherwise.
   */
  public boolean areAbstractSuccessors(AbstractElement element, CFAEdge cfaEdge, Collection<? extends AbstractElement> successors)
    throws CPATransferException, InterruptedException;

  /**
   * Checks whether the given element is covered by an other element. That is, the set of concretisations of the element
   * has to be a subset of the set of concretisations of the other element.
   */
  public boolean isCoveredBy(AbstractElement element, AbstractElement otherElement) throws CPAException;
}
