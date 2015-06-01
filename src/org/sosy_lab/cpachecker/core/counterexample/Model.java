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
package org.sosy_lab.cpachecker.core.counterexample;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.util.predicates.AssignableTerm;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.predicates.TermType;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Objects;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

/**
 * This class represents an assignment of concrete values to program variables
 * along a path. Each variable can have several assignments with different
 * SSA indices if it gets re-assigned along the path.
 *
 * The value of each variable can be an arbitrary object, but usually,
 * this is a {@link Number}.
 */
public class Model extends ForwardingMap<AssignableTerm, Object> implements Appender {

  public static class Variable implements AssignableTerm {
    private final String name;
    private final TermType type;

    public Variable(final String name, final TermType type) {
      this.name = name;
      this.type = type;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public TermType getType() {
      return type;
    }

    @Override
    public String toString() {
      return name + " : " + type;
    }

    @Override
    public int hashCode() {
      return 324 + name.hashCode() + type.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
      if (this == other) {
        return true;
      }
      if (other == null) {
        return false;
      }
      if (!getClass().equals(other.getClass())) {
        return false;
      }
      final Variable otherConstant = (Variable) other;
      return name.equals(otherConstant.name)
          && type.equals(otherConstant.type);
    }
  }

  /**
   * A function call can have a concrete return value.
   * TODO: Describe why handling pointers/references is not needed in this case
   */
  public static class Function implements AssignableTerm {

    private final String mName;
    private final TermType mReturnType;
    private final List<Object> mArguments;
    private final int mHashCode;

    public Function(String pName, TermType pReturnType, Object[] pArguments) {
      mName = pName;
      mReturnType = pReturnType;
      mArguments = ImmutableList.copyOf(pArguments);
      mHashCode = Objects.hashCode(pName, pReturnType, Arrays.hashCode(pArguments));
    }

    @Override
    public String getName() {
      return mName;
    }

    @Override
    public TermType getType() {
      return mReturnType;
    }

    public int getArity() {
      return mArguments.size();
    }

    public Object getArgument(int lArgumentIndex) {
      return mArguments.get(lArgumentIndex);
    }

    @Override
    public String toString() {
      return mName + "(" + Joiner.on(',').join(mArguments) + ") : " + mReturnType;
    }

    @Override
    public int hashCode() {
      return mHashCode;
    }

    @Override
    public boolean equals(Object pOther) {
      if (this == pOther) {
        return true;
      }

      if (pOther == null) {
        return false;
      }

      if (!getClass().equals(pOther.getClass())) {
        return false;
      }

      Function lFunction = (Function)pOther;

      return (lFunction.mName.equals(mName)
          && lFunction.mReturnType.equals(mReturnType)
          && lFunction.mArguments.equals(mArguments));
    }
  }

  private final Map<AssignableTerm, Object> mModel;

  private final CFAPathWithAssumptions assignments;
  private final Multimap<CFAEdge, AssignableTerm> assignableTermsPerCFAEdge;

  @Override
  protected Map<AssignableTerm, Object> delegate() {
    return mModel;
  }

  public static Model empty() {
    return new Model();
  }

  private Model() {
    mModel = ImmutableMap.of();
    assignments = new CFAPathWithAssumptions();
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  public Model(Map<AssignableTerm, Object> content) {
    mModel = ImmutableMap.copyOf(content);
    assignments = new CFAPathWithAssumptions();
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  public Model(Map<AssignableTerm, Object> content, CFAPathWithAssumptions pAssignments) {
    mModel = ImmutableMap.copyOf(content);
    assignments = pAssignments;
    assignableTermsPerCFAEdge = ImmutableListMultimap.of();
  }

  public Model(Map<AssignableTerm, Object> content, CFAPathWithAssumptions pAssignments,
      Multimap<CFAEdge, AssignableTerm> pAssignableTermsPerCFAEdge) {
    mModel = ImmutableMap.copyOf(content);
    assignments = pAssignments;
    assignableTermsPerCFAEdge = pAssignableTermsPerCFAEdge;
  }

  /**
   * Return a new model that is equal to the current one,
   * but additionally has information about when each variable was assigned.
   */
  public Model withAssignmentInformation(CFAPathWithAssumptions pAssignments) {
    checkState(assignments.isEmpty());
    return new Model(mModel, pAssignments);
  }

  /**
   * Return a path that indicates which variables where assigned which values at
   * what edge. Note that not every value for every variable is available.
   */
  @Nullable
  public CFAPathWithAssumptions getCFAPathWithAssignments() {
    return assignments;
  }

  /**
   * Returns a collection of {@link AssignableTerm}} terms that were assigned a the given
   * {@link CFAEdge} edge.
   *
   * @param pEdge All terms that were assigned at this edge are returned-
   * @return A collection of terms assigned at the given edge.
   */
  public Collection<AssignableTerm> getAllAssignedTerms(CFAEdge pEdge) {
    return assignableTermsPerCFAEdge.get(pEdge);
  }

  @Nullable
  public Map<ARGState, CFAEdgeWithAssumptions> getExactVariableValues(ARGPath pPath) {

    if (assignments.isEmpty()) {
      return null;
    }

    return assignments.getExactVariableValues(pPath);
  }

  @Nullable
  public CFAPathWithAssumptions getExactVariableValuePath(List<CFAEdge> pPath) {

    if (assignments.isEmpty()) {
      return null;
    }

    return assignments.getExactVariableValues(pPath);
  }

  private static final MapJoiner joiner = Joiner.on(System.lineSeparator()).withKeyValueSeparator(": ");

  @Override
  public void appendTo(Appendable output) throws IOException {
    Map<AssignableTerm, Object> sorted = ImmutableSortedMap.copyOf(mModel,
        Ordering.usingToString());
    joiner.appendTo(output, sorted);
  }

  @Override
  public String toString() {
    return Appenders.toString(this);
  }
}