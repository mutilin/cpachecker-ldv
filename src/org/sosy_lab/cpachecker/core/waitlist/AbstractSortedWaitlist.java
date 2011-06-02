/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.waitlist;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/**
 * Default implementation of a sorted waitlist.
 * The key that is used for sorting is defined by sub-classes (it's type is
 * the type parameter of this class).
 *
 * There may be several abstract elements with the same key, so this class
 * delegates the decision which of those should be chosen to a second waitlist
 * implementation. A factory for this implementation needs to be given to the
 * constructor.
 *
 * The iterators created by this class are unmodifiable.
 */
public abstract class AbstractSortedWaitlist<K extends Comparable<K>> implements Waitlist {

  private final WaitlistFactory wrappedWaitlist;

  // invariant: all entries in this map are non-empty
  private final NavigableMap<K, Waitlist> waitlist = new TreeMap<K, Waitlist>();

  private int size = 0;

  /**
   * Constructor that needs a factory for the waitlist implementation that
   * should be used to store elements with the same sorting key.
   */
  protected AbstractSortedWaitlist(WaitlistFactory pSecondaryStrategy) {
    wrappedWaitlist = Preconditions.checkNotNull(pSecondaryStrategy);
  }

  /**
   * Method that generates the sorting key for any abstract element.
   * This method may not return null.
   * If this method throws an exception, no guarantees about the state of the
   * current instance of this class are made.
   */
  protected abstract K getSortKey(AbstractElement pElement);

  @Override
  public void add(AbstractElement pElement) {
    K key = getSortKey(pElement);
    Waitlist localWaitlist = waitlist.get(key);
    if (localWaitlist == null) {
      localWaitlist = wrappedWaitlist.createWaitlistInstance();
      waitlist.put(key, localWaitlist);
    } else {
      assert !localWaitlist.isEmpty();
    }
    localWaitlist.add(pElement);
    size++;
  }

  @Override
  public boolean contains(AbstractElement pElement) {
    K key = getSortKey(pElement);
    Waitlist localWaitlist = waitlist.get(key);
    if (localWaitlist == null) {
      return false;
    }
    assert !localWaitlist.isEmpty();
    return localWaitlist.contains(pElement);
  }

  @Override
  public void clear() {
    waitlist.clear();
    size = 0;
  }

  @Override
  public boolean isEmpty() {
    assert waitlist.isEmpty() == (size == 0);
    return waitlist.isEmpty();
  }

  @Override
  public Iterator<AbstractElement> iterator() {
    return Iterables.concat(waitlist.values()).iterator();
  }

  @Override
  public AbstractElement pop() {
    Entry<K, Waitlist> highestEntry = waitlist.lastEntry();
    Waitlist localWaitlist = highestEntry.getValue();
    assert !localWaitlist.isEmpty();
    AbstractElement result = localWaitlist.pop();
    if (localWaitlist.isEmpty()) {
      waitlist.remove(highestEntry.getKey());
    }
    size--;
    return result;
  }

  @Override
  public boolean remove(AbstractElement pElement) {
    K key = getSortKey(pElement);
    Waitlist localWaitlist = waitlist.get(key);
    if (localWaitlist == null) {
      return false;
    }
    assert !localWaitlist.isEmpty();
    boolean result = localWaitlist.remove(pElement);
    if (result) {
      if (localWaitlist.isEmpty()) {
        waitlist.remove(key);
      }
      size--;
    }
    return result;
  }

  @Override
  public int size() {
    return size;
  }
}
