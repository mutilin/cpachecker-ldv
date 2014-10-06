package org.sosy_lab.cpachecker.cpa.stator.policy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.PathFormulaReportingState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.OptEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.rationals.ExtendedRational;
import org.sosy_lab.cpachecker.util.rationals.LinearConstraint;
import org.sosy_lab.cpachecker.util.rationals.LinearExpression;

import com.google.common.collect.ImmutableMap;

/**
 * Transfer relation for policy iteration.
 */
@Options(prefix="cpa.stator.policy")
public class PolicyTransferRelation  extends
    SingleEdgeTransferRelation implements TransferRelation {

  private final PathFormulaManager pfmgr;
  private final FormulaManagerFactory formulaManagerFactory;
  private final LinearConstraintManager lcmgr;
  private final LogManager logger;
  private final PolicyAbstractDomain abstractDomain;
  private final FormulaManagerView fmgr;
  private final TemplateManager templateManager;

  /**
   * Lazy evaluation: postpones the analysis until the communication
   * phase with other states.
   */
  public static class LazyState implements AbstractState {
    final PolicyAbstractState previousState;
    public LazyState(PolicyAbstractState pState) {
      previousState = pState;
    }
  }

  @SuppressWarnings("unused")
  public PolicyTransferRelation(
          Configuration config,
          FormulaManagerView formulaManager,
          FormulaManagerFactory formulaManagerFactory,
          PathFormulaManager pfmgr,
          LogManager logger,
          PolicyAbstractDomain abstractDomain,
          LinearConstraintManager lcmgr,
          TemplateManager templateManager
      )
      throws InvalidConfigurationException {

    config.inject(this, PolicyTransferRelation.class);
    fmgr = formulaManager;
    this.pfmgr = pfmgr;
    this.formulaManagerFactory = formulaManagerFactory;
    this.lcmgr = lcmgr;
    this.logger = logger;
    this.abstractDomain = abstractDomain;
    this.templateManager = templateManager;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState,
      Precision precision,
      CFAEdge edge
      ) throws CPATransferException, InterruptedException {

    // Lazy evaluation: postpone the analysis until {@code strengthen} is called.
    return Collections.singleton(new LazyState((PolicyAbstractState) pState));
  }

  /**
   * Strengthening is used for communicating the analysis details between
   * various CPAs.
   */
  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState state,
      List<AbstractState> otherStates,
      CFAEdge cfaEdge,
      Precision precision) throws CPATransferException, InterruptedException {
    LazyState previousState = (LazyState) state;
    List<PathFormulaReportingState> reportingStates = new LinkedList<>();
    for (AbstractState otherState : otherStates) {
      if (otherState instanceof PathFormulaReportingState) {
        PathFormulaReportingState fState = (PathFormulaReportingState) otherState;
        reportingStates.add(fState);
      }
    }
    return getAbstractSuccessors(previousState.previousState,
        cfaEdge, reportingStates);
  }

  public Collection<PolicyAbstractState> getAbstractSuccessors(
      PolicyAbstractState prevState,
      CFAEdge edge,
      List<PathFormulaReportingState> reportingStates
  ) throws CPATransferException, InterruptedException {

    logger.log(Level.FINE, ">>> Processing statement: ", edge.getCode(),
        " for to-node: ", edge.getSuccessor());

    CFANode toNode = edge.getSuccessor();

    // Formula representing the edge.
    PathFormula edgeFormula = pfmgr.makeFormulaForPath(
        Collections.singletonList(edge));

    /** Propagating templates */
    PolicyAbstractState.Templates toTemplates = templateManager.updatePrecisionForEdge(
        prevState.getTemplates(), edge);

    /** Propagate the invariants */
    ImmutableMap.Builder<LinearExpression, PolicyTemplateBound> newStateData;
    newStateData = ImmutableMap.builder();

    for (LinearExpression template : toTemplates) {
      try (OptEnvironment solver = formulaManagerFactory.newOptEnvironment()) {

        SSAMap outputSSA;
        SSAMap inputSSA = SSAMap.emptySSAMap().withDefault(1);
        List<BooleanFormula> constraints = new LinkedList<>();

        // Constraints imposed by other CPAs.
        if (reportingStates.size() != 0) {

          // TODO: dealing with multiple reporting states.
          SSAMap ssaMap = edgeFormula.getSsa();

          PathFormulaReportingState state = reportingStates.iterator().next();
          PathFormula pathFormula = state.getFormulaApproximation(
              fmgr, ssaMap.withDefault(1), inputSSA);
          constraints.add(pathFormula.getFormula());
          outputSSA = pathFormula.getSsa();
        } else {
          outputSSA = edgeFormula.getSsa();
        }

        logger.log(Level.FINE, "# Got SSA map: ", outputSSA);

        // Constraints from the previous state.
        for (Map.Entry<LinearExpression, PolicyTemplateBound> item : prevState) {
          // TODO: Do not re-add the constraints for each optimization.
          // If anything we can store them in a list.
          LinearExpression expr = item.getKey();
          ExtendedRational bound = item.getValue().bound;

          LinearConstraint constraint = new LinearConstraint(expr, bound);
          constraints.add(lcmgr.linearConstraintToFormula(constraint, inputSSA));
        }

        constraints.add(edgeFormula.getFormula());

        for (BooleanFormula constraint : constraints) {
          solver.addConstraint(constraint);
        }

        ExtendedRational value = lcmgr.maximize(solver, template, outputSSA);

        // If the state is not reachable, bail early.
        if (value == ExtendedRational.NEG_INFTY) {
          logger.log(Level.FINE, "# Stopping, unfeasible branch.");
          return Collections.emptyList();
        } else if (value != ExtendedRational.INFTY) {
          PolicyTemplateBound constraint = PolicyTemplateBound.of(edge, value);
          logger.log(Level.FINE, "# Updating constraint on node", toNode,
              " template ", template, " to ", constraint);
          newStateData.put(template, constraint);
        }

        // Note: this is a hack.
        // If the policy for the old-state is prevailing, the join
        // operator will reset the global object to the "good" state.
        abstractDomain.setPolicyForTemplate(toNode, template, edge);

      } catch (Exception e) {
        throw new CPATransferException("Failed solving", e);
      }
    }

    PolicyAbstractState newState = PolicyAbstractState.withState(
        newStateData.build(), toTemplates, toNode);

    logger.log(Level.FINE, "# New state = ", newState);
    return Collections.singleton(newState);
  }
}
