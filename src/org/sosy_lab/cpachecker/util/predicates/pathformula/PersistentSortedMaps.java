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
package org.sosy_lab.cpachecker.util.predicates.pathformula;

import static com.google.common.collect.Iterators.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PersistentSortedMap;

import com.google.common.base.Equivalence;
import com.google.common.collect.Ordering;

/**
 * Utility class for {@link PersistentSortedMap}s.
 *
 * Currently this class provides a merge operation.
 * The result of merging two maps is defined as a map
 * whose keyset is the union of the keyset of both input maps.
 * The values of the resulting map are the corresponding values of the input maps
 * as long as they are not differing.
 * Differing values for one key are resolved by passing them to a callback function.
 */
public class PersistentSortedMaps {

  private PersistentSortedMaps() { } // utility class

  /**
   * A callback that is used when a key with two different values
   * is encountered during the merge of two maps.
   */
  public static interface MergeConflictHandler<K, V> {

    /**
     * Resolve a conflict for one given key.
     * This handler is called only with two values
     * that are not considered equal according to the used {@link Equivalence}.
     * One of the values may be {@code null},
     * which means that the corresponding map contains {@code null} as value
     * for this key.
     * The handler may return {@code null}, and in this case the resulting map
     * will contain a mapping (key -> null).
     * @param key The key.
     * @param value1 The value from the first map.
     * @param value2 The value from the second map.
     * @return The value that should be put into the resulting map.
     */
    V resolveConflict(K key, V value1, V value2);
  }

  /**
   * Returns a {@link MergeConflictHandler} that will always throw an
   * {@link IllegalArgumentException}.
   * Use this in cases where you never expect differing values for one key.
   */
  public static <K, V> MergeConflictHandler<K, V> getExceptionMergeConflictHandler() {
    return new MergeConflictHandler<K, V>() {
      @Override
      public V resolveConflict(K key, V value1, V value2) {
        throw new IllegalArgumentException("Conflicting value when merging maps for key " + key + ": " + value1 + " and " + value2);
      }
    };
  }

  /**
   * Returns a {@link MergeConflictHandler} that will always return the maximum
   * (according to the natural order).
   * This may not be used if the map contains {@code null} as value.
   */
  public static <K, V extends Comparable<? super V>> MergeConflictHandler<K, V> getMaximumMergeConflictHandler() {
    return new MergeConflictHandler<K, V>() {
      @Override
      public V resolveConflict(K key, V value1, V value2) {
        return Ordering.natural().max(value1, value2);
      }
    };
  }

  /**
   * Returns a {@link MergeConflictHandler} that will always return the minimum
   * (according to the natural order).
   * This may not be used if the map contains {@code null} as value.
   */
  public static <K, V extends Comparable<? super V>> MergeConflictHandler<K, V> getMinimumMergeConflictHandler() {
    return new MergeConflictHandler<K, V>() {
      @Override
      public V resolveConflict(K key, V value1, V value2) {
        return Ordering.natural().min(value1, value2);
      }
    };
  }

  private static <K, V> MergeConflictHandler<K, V> inverseMergeConflictHandler(
      final MergeConflictHandler<K, V> delegate) {
    return new MergeConflictHandler<K, V>() {
      @Override
      public V resolveConflict(K pKey, V pValue1, V pValue2) {
        return delegate.resolveConflict(pKey, pValue2, pValue1);
      }
    };
  }

  /**
   * Merge two PersistentSortedMaps.
   * The result has all key-value pairs where the key is only in one of the map,
   * those which are identical in both map,
   * and for those keys that have a different value in both maps a handler is called,
   * and the result is put in the resulting map.
   * @param map1 The first map.
   * @param map2 The second map.
   * @param conflictHandler The handler that is called for a key with two different values.
   * @return The merged map.
   */
  public static <K extends Comparable<? super K>, V> PersistentSortedMap<K, V> merge(
    final PersistentSortedMap<K, V> map1,
    final PersistentSortedMap<K, V> map2,
    final MergeConflictHandler<K, V> conflictHandler) {

    if (map1.size() >= map2.size()) {
      return merge(map1, map2, Equivalence.equals(), conflictHandler, null);
    } else {
      return merge(map2, map1, Equivalence.equals(), inverseMergeConflictHandler(conflictHandler), null);
    }
  }

  /**
   * Merge two PersistentSortedMaps.
   * The result has all key-value pairs where the key is only in one of the map,
   * those which are identical in both map,
   * and for those keys that have a different value in both maps a handler is called,
   * and the result is put in the resulting map.
   *
   * Optionally you can pass a list that will receive all encountered differences,
   * i.e., keys which are present in only one map, or have different values.
   * The list will contain triples with key and both values,
   * where missing values are replaced by null.
   *
   * Implementation note:
   * It may be faster to call this method with the bigger of the input maps
   * as the first parameter.
   *
   * @param map1 The first map.
   * @param map2 The second map.
   * @param valueEquals The {@link Equivalence} that will determine whether two values are considered equal.
   * @param conflictHandler The handler that is called for a key with two different values.
   * @param collectDifferences Null or a modifiable list into which keys with different values are put.
   * @return The merged map.
   */
  public static <K extends Comparable<? super K>, V> PersistentSortedMap<K, V> merge(
      final PersistentSortedMap<K, V> map1,
      final PersistentSortedMap<K, V> map2,
      final Equivalence<? super V> valueEquals,
      final MergeConflictHandler<? super K, V> conflictHandler,
      final @Nullable List<Triple<K, V, V>> collectDifferences) {

    // Assume map1 is the bigger one, so we use it as the base.
    PersistentSortedMap<K, V> result = map1;

    final Iterator<Map.Entry<K, V>> it1 = map1.entrySet().iterator();
    final Iterator<Map.Entry<K, V>> it2 = map2.entrySet().iterator();

    Map.Entry<K, V> e1 = null;
    Map.Entry<K, V> e2 = null;

    // This loop iterates synchronously through both sets
    // by trying to keep the keys equal.
    // If one iterator falls behind, the other is not forwarded until the first catches up.
    // The advantage of this is it is in O(n log(n))
    // (n iterations, log(n) per update).
    // Invariant: The elements e1 and e2, and all the elements in the iterator
    //            still need to be handled.
    while ((e1 != null || it1.hasNext())
        && (e2 != null || it2.hasNext())) {

      if (e1 == null) {
        e1 = it1.next();
      }
      if (e2 == null) {
        e2 = it2.next();
      }

      final int comp = e1.getKey().compareTo(e2.getKey());

      if (comp < 0) {
        // e1 < e2

        final K key = e1.getKey();
        final V value1 = e1.getValue();

        if (collectDifferences != null) {
          collectDifferences.add(Triple.<K,V,V>of(key, value1, null));
        }

        // forward e1 until it catches up with e2
        e1 = null;

      } else if (comp > 0) {
        // e1 > e2

        final K key = e2.getKey();
        final V value2 = e2.getValue();

        // e2 is not in map
        assert !result.containsKey(key);
        result = result.putAndCopy(key, value2);

        if (collectDifferences != null) {
          collectDifferences.add(Triple.<K,V,V>of(key, null, value2));
        }

        // forward e2 until it catches up with e1
        e2 = null;

      } else {
        // e1 == e2

        final K key = e1.getKey();
        final V value1 = e1.getValue();
        final V value2 = e2.getValue();

        if (!valueEquals.equivalent(value1, value2)) {
          V newValue = conflictHandler.resolveConflict(key, value1, value2);
          result = result.putAndCopy(key, newValue);

          if (collectDifferences != null) {
            collectDifferences.add(Triple.of(key, value1, value2));
          }
        }

        // forward both iterators
        e1 = null;
        e2 = null;
      }
    }

    // Now we would copy the rest of the mappings from s1 (e1 and it1),
    // but we don't need them as s1 was the base of result.
    if (collectDifferences != null) {
      Iterator<Map.Entry<K, V>> rest =
          (e1 != null)
          ? concat(singletonIterator(e1), it1)
          : it1;

      while (rest.hasNext()) {
        e1 = rest.next();

        collectDifferences.add(Triple.<K,V,V>of(e1.getKey(), e1.getValue(), null));
      }
    }

    // Now copy the rest of the mappings from s2 (e2 and it2).
    Iterator<Map.Entry<K, V>> rest =
        (e2 != null)
        ? concat(singletonIterator(e2), it2)
        : it2;

    while (rest.hasNext()) {
      e2 = rest.next();
      K key = e2.getKey();
      V value2 = e2.getValue();

      result = result.putAndCopy(key, value2);

      if (collectDifferences != null) {
        collectDifferences.add(Triple.<K,V,V>of(key, null, value2));
      }
    }

    assert result.size() >= Math.max(map1.size(), map2.size());

    return result;
  }

  /**
   * Merges two {@link PersistentSortedMap}s with the given conflict handler
   * (in the same way as {@link #merge(set1, set2, conflictHandler)} does)
   * and returns two additional {@link PersistentSortedMap}s:
   * one with elements from the first map that do not exist in the second map,
   * and the other with the elements from the second map that do not exist in the first map.
   * If both maps contain the same key with different values,
   * the conflict handler will be used to resolve this,
   * and the key won't be in any of the difference maps.
   * @param set1 the first map
   * @param set2 the second map
   * @param conflictHandler the conflict handler
   * @return The {@link Triple} {@code (from1, from2, union)} where {@code from1} and {@code from2} are the
   * first and the second map mentioned above.
   */
  public static <K extends Comparable<? super K>, V>
  Triple<PersistentSortedMap<K, V>, PersistentSortedMap<K, V>, PersistentSortedMap<K, V>>
  mergeWithKeyDifferences(final PersistentSortedMap<K, V> set1,
      final PersistentSortedMap<K, V> set2,
      final MergeConflictHandler<K, V> conflictHandler) {

    if (set1.size() < set2.size()) {
      // swap order for more efficient implementation
      Triple<PersistentSortedMap<K, V>, PersistentSortedMap<K, V>, PersistentSortedMap<K, V>> result =
          mergeWithKeyDifferences(set2, set1, inverseMergeConflictHandler(conflictHandler));
      return Triple.of(result.getSecond(), result.getFirst(), result.getThird());
    }

    List<Triple<K, V, V>> differences = new ArrayList<>();
    PersistentSortedMap<K, V> union = merge(set1, set2, Equivalence.equals(),
        conflictHandler, differences);

    PersistentSortedMap<K, V> fromSet1 = union.empty();
    PersistentSortedMap<K, V> fromSet2 = union.empty();

    for (Triple<K, V, V> difference : differences) {
      if (difference.getSecond() == null) {
        // first value is null, key was only in second map
        fromSet2 = fromSet2.putAndCopy(difference.getFirst(), difference.getThird());
      } else if (difference.getThird() == null) {
        // second value is null, key was only in first map
        fromSet1 = fromSet1.putAndCopy(difference.getFirst(), difference.getSecond());
      } else {
        // both values present, key was in both maps
      }
    }

    return Triple.of(fromSet1, fromSet2, union);
  }
}
