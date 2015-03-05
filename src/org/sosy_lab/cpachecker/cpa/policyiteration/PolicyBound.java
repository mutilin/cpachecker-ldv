package org.sosy_lab.cpachecker.cpa.policyiteration;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.rationals.Rational;

import com.google.common.base.Objects;

/**
 * Policy with a local bound.
 */
public class PolicyBound {

  /**
   * Location of an abstracted state which has caused an update.
   */
  final Location predecessor;

  /**
   * Policy formula. Has to be concave&monotone (no conjunctions in particular).
   */
  final PathFormula formula;

  /**
   * Bound on the policy.
   */
  final Rational bound;

  /**
   * PathFormula which defines the starting {@link SSAMap} and
   * {@link org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet}
   * for {@code formula}.
   */
  final PathFormula startPathFormula;

  private static final Map<Triple<Location, PathFormula, Location>, Integer>
      serializationMap = new HashMap<>();
  private static int pathCounter = -1;

  private PolicyBound(PathFormula pFormula, Rational pBound, Location pPredecessor,
      PathFormula pStartPathFormula) {
    formula = pFormula;
    bound = pBound;
    predecessor = pPredecessor;
    startPathFormula = pStartPathFormula;
  }

  public static PolicyBound of(PathFormula pFormula, Rational bound,
      Location pUpdatedFrom, PathFormula pStartPathFormula) {
    return new PolicyBound(pFormula, bound, pUpdatedFrom, pStartPathFormula);
  }

  public PolicyBound updateValue(Rational newValue) {
    return new PolicyBound(formula, newValue, predecessor, startPathFormula);
  }

  /**
   * @return Unique identifier for the triple
   * {@code fromLocation, PathFormula, fromLocation}.
   * Identifies a path in the value determination sub-CFG.
   */
  public int serializePolicy(Location toLocation) {
    Triple<Location, PathFormula, Location> p = Triple.of(
        predecessor, formula, toLocation);
    Integer serialization = serializationMap.get(p);
    if (serialization == null) {
      serialization = ++pathCounter;
      serializationMap.put(p, serialization);
    }
    return serialization;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(predecessor, bound, formula);
  }

  @Override
  public String toString() {
    return String.format("%s (from: %s)", bound, predecessor);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null) return false;
    if (other.getClass() != this.getClass()) return false;
    PolicyBound o = (PolicyBound) other;
    return predecessor.equals(o.predecessor) && bound.equals(o.bound) && formula.equals(o.formula);
  }
}
