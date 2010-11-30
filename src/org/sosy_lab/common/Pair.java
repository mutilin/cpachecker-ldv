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
package org.sosy_lab.common;

import com.google.common.base.Function;


/**
 * A generic Pair class. Code borrowed from here:
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6229146
 * @author alb
 *
 * @param <A>
 * @param <B>
 */
public class Pair<A, B> {
    private final A first;
    private final B second;

    private Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }
    
    public static <A, B> Pair<A, B> of(A first, B second) {
      return new Pair<A, B>(first, second);
    }

    public A getFirst() { return first; }
    public B getSecond() { return second; }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    private static boolean equals(Object x, Object y) {
        return (x == null && y == null) || (x != null && x.equals(y));
    }

    @Override
    public boolean equals(Object other) {
    return (other instanceof Pair<?,?>)
        && equals(first,  ((Pair<?,?>)other).first)
        && equals(second, ((Pair<?,?>)other).second);
    }

    @Override
    public int hashCode() {
        if (first == null) return (second == null) ? 0 : second.hashCode() + 1;
        else if (second == null) return first.hashCode() + 2;
        else return first.hashCode() * 17 + second.hashCode();
    }
    
    private static final Function<?,?> PROJECTION_TO_FIRST = new Function<Pair<?, ?>, Object>() {
      @Override
      public Object apply(Pair<?, ?> pArg0) {
        return pArg0.getFirst();
      }
    };
    
    public static <T> Function<Pair<T,?>,T> getProjectionToFirst() {
      @SuppressWarnings("unchecked")
      Function<Pair<T,?>,T> result = (Function<Pair<T,?>,T>)PROJECTION_TO_FIRST;
      return result;
    }
    
    private static final Function<?,?> PROJECTION_TO_SECOND = new Function<Pair<?, ?>, Object>() {
      @Override
      public Object apply(Pair<?, ?> pArg0) {
        return pArg0.getSecond();
      }
    };
    
    public static <T,V> Function<Pair<V,T>,T> getProjectionToSecond() {
      @SuppressWarnings("unchecked")
      Function<Pair<V,T>,T> result = (Function<Pair<V,T>,T>)PROJECTION_TO_SECOND;
      return result;
    }
}
