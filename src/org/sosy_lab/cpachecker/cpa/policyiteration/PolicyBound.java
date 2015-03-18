package org.sosy_lab.cpachecker.cpa.policyiteration;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.rationals.Rational;

import com.google.common.base.Objects;

/**
 * Policy with a local bound.
 */
public interface PolicyBound {
  public Location getPredecessor();

  public PathFormula getFormula();

  public Rational getBound();

  public PathFormula getStartPathFormula();

  public boolean dependsOnInitial();

  public int serializePolicy(Location toLocation);

  public PolicyBound updateValue(Rational newValue);

  /**
   * Dummy class to refer to the previous bound.
   */
  public static class PolicyBoundDummy implements PolicyBound {

    private final Rational value;

    private PolicyBoundDummy(Rational pValue) {
      value = pValue;
    }

    public static PolicyBoundDummy of(Rational pValue) {
      return new PolicyBoundDummy(pValue);
    }

    @Override
    public Location getPredecessor() {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }

    @Override
    public PathFormula getFormula() {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }

    @Override
    public Rational getBound() {
      return value;
    }

    @Override
    public PathFormula getStartPathFormula() {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }

    @Override
    public boolean dependsOnInitial() {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }

    @Override
    public int serializePolicy(Location toLocation) {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }

    @Override
    public PolicyBound updateValue(Rational newValue) {
      throw new UnsupportedOperationException("Dummy policy bound only has a value");
    }
  }

  public static class PolicyBoundImpl implements PolicyBound {

    /**
     * Location of an abstracted state which has caused an update.
     */
    private final Location predecessor;

    /**
     * Policy formula. Has to be concave and monotone (no conjunctions in
     * particular).
     */
    private final PathFormula formula;

    /**
     * Bound on the policy.
     */
    private final Rational bound;

    /**
     * PathFormula which defines the starting {@link SSAMap} and
     * {@link org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet}
     * for {@code formula}.
     */
    private final PathFormula startPathFormula;

    /**
     * Whether the bound can change with changing initial conditions.
     */
    private final boolean dependsOnInitial;

    private static final Map<Triple<Location, BooleanFormula, Location>, Integer>
        serializationMap = new HashMap<>();
    private static final UniqueIdGenerator pathCounter = new UniqueIdGenerator();

    private PolicyBoundImpl(PathFormula pFormula, Rational pBound,
        Location pPredecessor,
        PathFormula pStartPathFormula, boolean pDependsOnInitial) {
      formula = pFormula;
      bound = pBound;
      predecessor = pPredecessor;
      startPathFormula = pStartPathFormula;
      dependsOnInitial = pDependsOnInitial;
    }

    public static PolicyBound of(PathFormula pFormula, Rational bound,
        Location pUpdatedFrom, PathFormula pStartPathFormula,
        boolean dependsOnInitial) {
      return new PolicyBoundImpl(pFormula, bound, pUpdatedFrom, pStartPathFormula,
          dependsOnInitial);
    }

    @Override
    public PolicyBound updateValue(Rational newValue) {
      return new PolicyBoundImpl(formula, newValue, predecessor, startPathFormula,
          dependsOnInitial);
    }

    /**
     * @return Unique identifier for value determination.
     *
     * Based on triple (from, to, policy).
     */
    @Override
    public int serializePolicy(Location toLocation) {
      Triple<Location, BooleanFormula, Location> p = Triple.of(
          predecessor, formula.getFormula(), toLocation);
      Integer serialization = serializationMap.get(p);
      if (serialization == null) {
        serialization = pathCounter.getFreshId();
        serializationMap.put(p, serialization);
      }
      return serialization;
    }

    @Override
    public Location getPredecessor() {
      return predecessor;
    }

    @Override
    public PathFormula getFormula() {
      return formula;
    }

    @Override
    public Rational getBound() {
      return bound;
    }

    @Override
    public PathFormula getStartPathFormula() {
      return startPathFormula;
    }

    @Override
    public boolean dependsOnInitial() {
      return dependsOnInitial;
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
      PolicyBoundImpl o = (PolicyBoundImpl) other;
      return predecessor.equals(o.predecessor) && bound.equals(o.bound)
          && formula.equals(o.formula);
    }
  }
}

