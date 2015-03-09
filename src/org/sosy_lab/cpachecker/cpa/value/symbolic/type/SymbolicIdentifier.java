/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.value.symbolic.type;

import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.value.type.NumericValue;

/**
 * Identifier for basic symbolic values.
 * Symbolic identifiers are used to track equality between
 * variables that have non-deterministic values.
 *
 * <p>Example:
 * <pre>
 *    int a = nondet_int();
 *    int b = a;
 *
 *    if (a != b) {
 * ERROR:
 *    return -1;
 *    }
 * </pre>
 * In the example above, <code>a</code> is assigned a symbolic identifier.
 * <code>b</code> is assigned the same symbolic identifier, so that the condition
 * <code>a != b</code> can be evaluated as <code>false</code>.
 * </p>
 */
public class SymbolicIdentifier implements SymbolicValue, Comparable<SymbolicIdentifier> {

  private static final long serialVersionUID = -3773425414056328601L;

  // stores the next usable id
  private static long nextId = 0;

  // this objects unique id for identifying it
  private final long id;

  public SymbolicIdentifier(long pId) {
    id = pId;
  }

  /**
   * Returns a new instance of a <code>SymbolicIdentifier</code>.
   *
   * <p>Each call to this method returns a new, unique <code>SymbolicIdentifier</code>.</p>
   *
   * @return a new instance of a <code>SymbolicIdentifier</code>
   */
  static SymbolicIdentifier getNewIdentifier() {
    return new SymbolicIdentifier(nextId++);
  }

  @Override
  public <T> T accept(SymbolicValueVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }

  public long getId() {
    return id;
  }

  @Override
  public int hashCode() {
    return (int) (id ^ (id >>> 32));
  }

  @Override
  public boolean equals(Object pOther) {
    return pOther instanceof SymbolicIdentifier && ((SymbolicIdentifier) pOther).id == id;
  }

  @Override
  public String toString() {
    return "SymbolicIdentifier[" + id + "]";
  }

  @Override
  public boolean isNumericValue() {
    return false;
  }

  @Override
  public boolean isUnknown() {
    return false;
  }

  @Override
  public boolean isExplicitlyKnown() {
    return false;
  }

  @Override
  public NumericValue asNumericValue() {
    return null;
  }

  @Override
  public Long asLong(CType type) {
    return null;
  }

  @Override
  public int compareTo(SymbolicIdentifier o) {
    if (getId() == o.getId()) {
      return 0;
    } else if (getId() > o.getId()) {
      return 1;
    } else {
      return -1;
    }
  }


  /**
   * Converter for {@link SymbolicIdentifier} objects.
   * Converts SymbolicIdentifiers to and from Strings.
   */
  public static class Converter {

    private static final Converter SINGLETON = new Converter();

    private static final String PREFIX = "s";

    private Converter() {
      // DO NOTHING
    }

    public static Converter getInstance() {
      return SINGLETON;
    }

    /**
     * Converts a given {@link SymbolicIdentifier} to a String.
     * The returned <code>String</code> contains all information necessary for uniquely identifying the given
     * identifier.
     *
     *  <p>For a given identifier p, <code>convert(convert(p)) = p</code> is always true.
     *
     * @param pIdentifier the <code>SymbolicIdentifier</code> to convert to a string
     * @return a <code>String</code> containing all information necessary for converting it to a identifier
     */
    public String convert(SymbolicIdentifier pIdentifier) {
      return PREFIX + pIdentifier.getId();
    }

    /**
     * Converts a given encoding of a {@link SymbolicIdentifier} to the corresponding
     * <code>SymbolicIdentifier</code>.
     *
     * Only valid encodings, as produced by {@link #convert(SymbolicIdentifier)}, are allowed.
     *
     * @param pIdentifierInformation a <code>String</code> encoding of a <code>SymbolicIdentifier</code>
     * @return the <code>SymbolicIdentifier</code> representing the given encoding
     */
    public SymbolicIdentifier convert(String pIdentifierInformation) {
      final long id = Long.parseLong(pIdentifierInformation.substring(PREFIX.length()));
      return new SymbolicIdentifier(id);
    }

    /**
     * Returns whether the given string is a valid encoding of a {@link SymbolicIdentifier}.
     *
     * @param pName the string to analyse
     * @return <code>true</code> if the given string is a valid encoding of a <code>SymbolicIdentifier</code>,
     *    <code>false</code> otherwise
     */
    public boolean isSymbolicEncoding(String pName) {
      return pName.matches(PREFIX + "[0-9]+");
    }
  }
}
