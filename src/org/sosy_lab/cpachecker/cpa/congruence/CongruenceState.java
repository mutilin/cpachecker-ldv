package org.sosy_lab.cpachecker.cpa.congruence;

import com.google.common.collect.ImmutableMap;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.CExpressionReportingState;
import org.sosy_lab.cpachecker.cpa.abe.ABEAbstractedState;
import org.sosy_lab.cpachecker.cpa.abe.ABEIntermediateState;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.templates.Template;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.solver.api.BooleanFormula;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

public class CongruenceState implements
                             Iterable<Entry<Template, Congruence>>,
                             ABEAbstractedState<CongruenceState>,
                             CExpressionReportingState {

  private final ImmutableMap<Template, Congruence> data;
  private final CongruenceManager congruenceManager;
  private final PointerTargetSet pointerTargetSet;
  private final SSAMap ssaMap;
  private final Optional<ABEIntermediateState<CongruenceState>> generatingState;
  private final CFANode node;

  public CongruenceState(
      Map<Template, Congruence> pData,
      CongruenceManager pCongruenceManager,
      PointerTargetSet pPointerTargetSet,
      SSAMap pSsaMap,
      Optional<ABEIntermediateState<CongruenceState>> pGeneratingState,
      CFANode pNode) {
    data = ImmutableMap.copyOf(pData);
    congruenceManager = pCongruenceManager;
    pointerTargetSet = pPointerTargetSet;
    ssaMap = pSsaMap;
    generatingState = pGeneratingState;
    node = pNode;
  }

  public Map<Template, Congruence> getAbstraction() {
    return data;
  }

  public static CongruenceState empty(CongruenceManager pCongruenceManager,
                                      CFANode pNode) {
    return new CongruenceState(
        ImmutableMap.of(),
        pCongruenceManager,
        PointerTargetSet.emptyPointerTargetSet(),
        SSAMap.emptySSAMap(), Optional.empty(), pNode);
  }

  public Optional<Congruence> get(Template template) {
    return Optional.ofNullable(data.get(template));
  }

  @Override
  public Iterator<Entry<Template, Congruence>> iterator() {
    return data.entrySet().iterator();
  }

  public String toDOTLabel() {
    StringBuilder b = new StringBuilder();
    for (Entry<Template, Congruence> e : data.entrySet()) {
      if (e.getValue() == Congruence.EVEN) {
        b.append(e.getKey().toString()).append(" is even\n");
      } else if (e.getValue() == Congruence.ODD) {
        b.append(e.getKey().toString()).append(" is odd\n");
      }
    }
    return b.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CongruenceState)) {
      return false;
    }
    if (o == this) {
      return true;
    }
    CongruenceState other = (CongruenceState) o;
    return other.data.equals(data);
  }

  /**
   * Convert the state to <b>instantiated</b> formula with respect to the
   * PathFormula {@code ref}.
   */
  public BooleanFormula toFormula(
      FormulaManagerView manager, PathFormulaManager pfmgr, PathFormula ref) {
    return congruenceManager.toFormula(pfmgr, manager, this, ref);
  }

  @Override
  public SSAMap getSSAMap() {
    return ssaMap;
  }

  @Override
  public PointerTargetSet getPointerTargetSet() {
    return pointerTargetSet;
  }

  @Override
  public Optional<ABEIntermediateState<CongruenceState>> getGeneratingState() {
    return generatingState;
  }

  @Override
  public CongruenceState cast() {
    return this;
  }

  @Override
  public BooleanFormula instantiate() {
    return congruenceManager.toFormula(this);
  }

  @Override
  public BooleanFormula getFormulaApproximation(FormulaManagerView manager) {
    return congruenceManager.toFormulaUninstantiated(this, manager);
  }

  @Override
  public CFANode getNode() {
    return node;
  }

  @Override
  public String reportInvariantAsCExpression() {
    try {
      return congruenceManager.abstractStateToCExpression(this);
    } catch (InterruptedException pE) {
      throw new UnsupportedOperationException("Failed formatting");
    }
  }
}
