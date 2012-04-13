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
package org.sosy_lab.cpachecker.cpa.art;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * This class is a modifiable live view of a reached set, which shows the ART
 * relations between the elements, and enforces a correct ART when the set is
 * modified through this wrapper.
 */
public class ARTReachedSet {

  private final ReachedSet mReached;
//  private final UnmodifiableReachedSet mUnmodifiableReached;

  public ARTReachedSet(ReachedSet pReached) {
    mReached = checkNotNull(pReached);
//    mUnmodifiableReached = new UnmodifiableReachedSetWrapper(mReached);
  }

  public ReachedSet asReachedSet() {
    return mReached;
  }

  /**
   * Remove an element and all elements below it from the tree. Re-add all those
   * elements to the waitlist which have children which are either removed or were
   * covered by removed elements.
   *
   * @param e The root of the removed subtree, may not be the initial element.
   */
  public void removeSubtree(ARTElement e) {
    Set<ARTElement> toWaitlist = removeSubtree0(e);

    for (ARTElement ae : toWaitlist) {
      mReached.reAddToWaitlist(ae);
    }
  }

  /**
   * Like {@link #removeSubtree(ARTElement)}, but when re-adding elements to the
   * waitlist adapts precisions with respect to the supplied precision p (see
   * {@link #adaptPrecision(ARTElement, Precision)}).
   * @param e The root of the removed subtree, may not be the initial element.
   * @param p The new precision.
   */
  public void removeSubtree(ARTElement e, Precision p) {
    Set<ARTElement> toWaitlist = removeSubtree0(e);

    for (ARTElement ae : toWaitlist) {
      mReached.updatePrecision(ae, adaptPrecision(ae, p));
      mReached.reAddToWaitlist(ae);
    }
  }

  /**
   * Adapts the precision stored in the reached set for lARTElement.
   * If the stored precision is a wrapper precision, pNewPrecision replaces the
   * component of the wrapper precision that corresponds to pNewPrecision.
   * Otherwise, pNewPrecision replaces the stored precision.
   * @param pARTElement Reached element for which the precision has to be adapted.
   * @param pNewPrecision New precision.
   * @return The adapted precision.
   */
  private Precision adaptPrecision(ARTElement pARTElement, Precision pNewPrecision) {
    Precision lOldPrecision = mReached.getPrecision(pARTElement);

    return Precisions.replaceByType(lOldPrecision, pNewPrecision, pNewPrecision.getClass());
  }

  private Set<ARTElement> removeSubtree0(ARTElement e) {
    Preconditions.checkNotNull(e);
    Preconditions.checkArgument(!e.getParents().isEmpty(), "May not remove the initial element from the ART/reached set");

    Set<ARTElement> toUnreach = e.getSubtree();

    // collect all elements covered by the subtree
    List<ARTElement> newToUnreach = new ArrayList<ARTElement>();

    for (ARTElement ae : toUnreach) {
      newToUnreach.addAll(ae.getCoveredByThis());
    }
    toUnreach.addAll(newToUnreach);

    mReached.removeAll(toUnreach);

    Set<ARTElement> toWaitlist = removeSet(toUnreach);

    return toWaitlist;
  }

  /**
   * Remove a set of elements from the ART. There are no sanity checks.
   *
   * The result will be a set of elements that need to be added to the waitlist
   * to re-discover the removed elements. These are the parents of the removed
   * elements which are not removed themselves.
   *
   * @param elements the elements to remove
   * @return the elements to re-add to the waitlist
   */
  private static Set<ARTElement> removeSet(Set<ARTElement> elements) {
    Set<ARTElement> toWaitlist = new LinkedHashSet<ARTElement>();
    for (ARTElement ae : elements) {

      for (ARTElement parent : ae.getParents()) {
        if (!elements.contains(parent)) {
          toWaitlist.add(parent);
        }
      }

      ae.removeFromART();
    }
    return toWaitlist;
  }

  /**
   * Remove all covering relations from a node so that this node does not cover
   * any other node anymore.
   * Also adds any now uncovered leaf nodes to the waitlist.
   *
   * Call this method when you have changed (strengthened) an abstract element.
   */
  public void removeCoverageOf(ARTElement v) {
    for (ARTElement coveredByChildOfV : ImmutableList.copyOf(v.getCoveredByThis())) {
      uncover(coveredByChildOfV);
    }
    assert v.getCoveredByThis().isEmpty();
  }

  /**
   * Mark a covered element as non-covered.
   * This method also re-adds all leaves in that part of the ART to the waitlist.
   *
   * @param element The covered ARTElement to uncover.
   */
  public void uncover(ARTElement element) {
    element.uncover();

    // this is the subtree of elements which now become uncovered
    Set<ARTElement> uncoveredSubTree = element.getSubtree();

    for (ARTElement e : uncoveredSubTree) {
      assert !e.isCovered();

      e.setCovering();

      if (!e.wasExpanded()) {
        // its a leaf
        mReached.reAddToWaitlist(e);
      }
    }
  }

  public static class ForwardingARTReachedSet extends ARTReachedSet {

    protected final ARTReachedSet delegate;

    public ForwardingARTReachedSet(ARTReachedSet pReached) {
      super(pReached.mReached);
      delegate = pReached;
    }

    @Override
    public ReachedSet asReachedSet() {
      return delegate.asReachedSet();
    }

    @Override
    public void removeSubtree(ARTElement pE) {
      delegate.removeSubtree(pE);
    }

    @Override
    public void removeSubtree(ARTElement pE, Precision pP) {
      delegate.removeSubtree(pE, pP);
    }
  }
}
