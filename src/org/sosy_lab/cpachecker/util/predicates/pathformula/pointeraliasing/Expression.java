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
package org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing;

import static com.google.common.base.MoreObjects.toStringHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sosy_lab.solver.api.Formula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.Expression.Location.AliasedLocation;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.Expression.Location.UnaliasedLocation;

abstract class Expression {
  static abstract class Location extends Expression {
    static class AliasedLocation extends Location {

      private AliasedLocation(final Formula address) {
        this.address = address;
      }

      public Formula getAddress() {
        return address;
      }

      @Override
      public Kind getKind() {
        return Kind.ALIASED_LOCATION;
      }

      @Override
      public String toString() {
        return toStringHelper(this)
                      .add("address", address)
                      .toString();
      }

      private final Formula address;
    }

    static class UnaliasedLocation extends Location {

      private UnaliasedLocation(final String variableName) {
        this.variableName = variableName;
      }

      public String getVariableName() {
        return variableName;
      }

      @Override
      public Kind getKind() {
        return Kind.UNALIASED_LOCATION;
      }

      @Override
      public String toString() {
        return toStringHelper(this)
                      .add("variable", variableName)
                      .toString();
      }

      private final String variableName;
    }

    public static AliasedLocation ofAddress(final @Nonnull Formula address) {
      return new AliasedLocation(address);
    }

    public static UnaliasedLocation ofVariableName(final @Nonnull String variableName) {
      return new UnaliasedLocation(variableName);
    }

    public boolean isAliased() {
      return this instanceof AliasedLocation;
    }

    public AliasedLocation asAliased() {
      if (this instanceof AliasedLocation) {
        return (AliasedLocation) this;
      } else {
        return null;
      }
    }

    public UnaliasedLocation asUnaliased() {
      if (this instanceof UnaliasedLocation) {
        return (UnaliasedLocation) this;
      } else {
        return null;
      }
    }
  }

  static class Value extends Expression {
    static class Nondet extends Value {
      private Nondet() {
        super(null);
      }

      @Override
      public Formula getValue() {
        return null;
      }

      @Override
      public boolean isNondet() {
        return true;
      }

      @Override
      public Kind getKind() {
        return Kind.NONDET;
      }

      @Override
      public String toString() {
        return toStringHelper(this)
                      .toString();
      }
    }

    private Value(final Formula value) {
      this.value = value;
    }

    public Formula getValue() {
      return value;
    }

    public boolean isNondet() {
      return false;
    }

    @Override
    public Kind getKind() {
      return Kind.DET_VALUE;
    }

    @Override
    public String toString() {
      return toStringHelper(this)
                    .add("value", value)
                    .toString();
    }

    private final Formula value;
    private static final Value nondet = new Nondet();
  }

  public static Value ofValue(final @Nullable Formula value) {
    return value != null ? new Value(value) : null;
  }

  public static Value nondetValue() {
    return Value.nondet;
  }

  public boolean isLocation() {
    return this instanceof Location;
  }

  public boolean isValue() {
    return this instanceof Value;
  }

  public boolean isNondetValue() {
    return this == Value.nondet;
  }

  public boolean isAliasedLocation() {
    return this.isLocation() && this.asLocation().isAliased();
  }

  public boolean isUnaliasedLocation() {
    return this.isLocation() && !this.asLocation().isAliased();
  }

  public Location asLocation() {
    if (this instanceof Location) {
      return (Location) this;
    } else {
      return null;
    }
  }

  public AliasedLocation asAliasedLocation() {
    if (this.isLocation()) {
      return this.asLocation().asAliased();
    } else {
      return null;
    }
  }

  public UnaliasedLocation asUnaliasedLocation() {
    if (this.isLocation()) {
      return this.asLocation().asUnaliased();
    } else {
      return null;
    }
  }

  public Value asValue() {
    if (this instanceof Value) {
      return (Value) this;
    } else {
      return null;
    }
  }

  public abstract Kind getKind();

  static enum Kind {ALIASED_LOCATION, UNALIASED_LOCATION, DET_VALUE, NONDET}
}
