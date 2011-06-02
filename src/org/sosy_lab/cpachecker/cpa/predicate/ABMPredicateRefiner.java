package org.sosy_lab.cpachecker.cpa.predicate;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.sosy_lab.cpachecker.util.AbstractElements.extractElementByType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.sosy_lab.common.Timer;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.cpa.abm.AbstractABMBasedRefiner;
import org.sosy_lab.cpachecker.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.cpa.art.ARTReachedSet;
import org.sosy_lab.cpachecker.cpa.art.Path;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

import com.google.common.collect.Lists;


/**
 * Implements predicate refinements when using ABM.
 * It is based on the {@link AbstractABMBasedRefiner} and delegates the work to
 * a {@link ExtendedPredicateRefiner}, which is a small extension of the regular
 * {@link PredicateRefiner}.
 *
 * So the hierarchy is as follows:
 *
 *               AbstractARTBasedRefiner
 *                         ^
 *                         |
 *           +-------------+-------------+
 *           |                           |
 * AbstractABMBasedRefiner        PredicateRefiner
 *           ^                           ^
 *           |                           |
 *   ABMPredicateRefiner <--> ExtendedPredicateRefiner
 *
 * Here ^ means inheritance and -> means reference.
 */
public final class ABMPredicateRefiner extends AbstractABMBasedRefiner {

  private final PredicateRefiner refiner;

  public ABMPredicateRefiner(final ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
    super(pCpa);
    this.refiner = new ExtendedPredicateRefiner(pCpa);
  }

  @Override
  protected final boolean performRefinement0(ARTReachedSet pReached, Path pPath)
      throws CPAException, InterruptedException {

    return refiner.performRefinement(pReached, pPath);
  }

  @Override
  protected final Path getTargetPath(Path pPath) {
    return refiner.getTargetPath(pPath);
  }

  /**
   * This is a small extension of PredicateRefiner that overrides
   * {@link #transformPath(Path)} so that it respects ABM.
   */
  final class ExtendedPredicateRefiner extends PredicateRefiner {

    final Timer ssaRenamingTimer = new Timer();

    private final FormulaManager fmgr;
    private final PathFormulaManager pfmgr;

    private ExtendedPredicateRefiner(ConfigurableProgramAnalysis pCpa) throws CPAException, InvalidConfigurationException {
      super(pCpa);

      ABMPredicateCPA predicateCpa = this.getArtCpa().retrieveWrappedCpa(ABMPredicateCPA.class);
      if (predicateCpa == null) {
        throw new CPAException(getClass().getSimpleName() + " needs a PredicateCPA");
      }

      this.fmgr = predicateCpa.getFormulaManager();
      this.pfmgr = predicateCpa.getPathFormulaManager();
    }

    @Override
    protected final boolean performRefinement(ARTReachedSet pReached, Path pPath) throws CPAException, InterruptedException {
      boolean result = super.performRefinement(pReached, pPath);

      if (result) {
        lastErrorPath = null; // TODO why this?
      }

      return result;
    }

    @Override
    protected final List<Triple<ARTElement, CFANode, PredicateAbstractElement>> transformPath(Path pPath) throws CPATransferException {
      // the elements in the path are not expanded, so they contain the path formulas
      // with the wrong indices
      // we need to re-create all path formulas in the flattened ART

      List<Triple<ARTElement, CFANode, PredicateAbstractElement>> notRenamedPath = super.transformPath(pPath);

      ssaRenamingTimer.start();
      try {
        Map<ARTElement, Formula> renamedBlockFormulas = computeBlockFormulas(pPath.getFirst().getFirst());

        return replaceFormulasInPath(notRenamedPath, renamedBlockFormulas);

      } finally {
        ssaRenamingTimer.stop();
      }
    }

    private Map<ARTElement, Formula> computeBlockFormulas(ARTElement pRoot) throws CPATransferException {

      Map<ARTElement, PathFormula> formulas = new HashMap<ARTElement, PathFormula>();
      Map<ARTElement, Formula> abstractionFormulas = new HashMap<ARTElement, Formula>();
      Deque<ARTElement> todo = new ArrayDeque<ARTElement>();

      // initialize
      assert pRoot.getParents().isEmpty();
      formulas.put(pRoot, pfmgr.makeEmptyPathFormula());
      todo.addAll(pRoot.getChildren());

      // iterate over all elements in the ART with BFS
      outer: while (!todo.isEmpty()) {
        ARTElement currentElement = todo.pollFirst();
        if (formulas.containsKey(currentElement)) {
          continue; // already handled
        }

        // collect formulas for current location
        List<PathFormula> currentFormulas = new ArrayList<PathFormula>(currentElement.getParents().size());
        for (ARTElement parentElement : currentElement.getParents()) {
          PathFormula parentFormula = formulas.get(parentElement);
          if (parentFormula == null) {
            // parent not handled yet, re-queue current element
            todo.addLast(currentElement);
            continue outer;

          } else {
            CFAEdge edge = parentElement.getEdgeToChild(currentElement);
            PathFormula currentFormula = pfmgr.makeAnd(parentFormula, edge);
            currentFormulas.add(currentFormula);
          }
        }
        assert currentFormulas.size() >= 1;

        PredicateAbstractElement predicateElement = extractElementByType(currentElement, PredicateAbstractElement.class);
        if (predicateElement instanceof PredicateAbstractElement.AbstractionElement) {
          // abstraction element
          PathFormula currentFormula = getOnlyElement(currentFormulas);
          abstractionFormulas.put(currentElement, currentFormula.getFormula());

          // start new block with empty formula
          assert todo.isEmpty() : "todo should be empty because of the special ART structure";
          formulas.clear(); // free some memory

          formulas.put(currentElement, pfmgr.makeEmptyPathFormula(currentFormula));

        } else {
          // merge the formulas
          Iterator<PathFormula> it = currentFormulas.iterator();
          PathFormula currentFormula = it.next();
          while (it.hasNext()) {
            currentFormula = pfmgr.makeOr(currentFormula, it.next());
          }

          formulas.put(currentElement, currentFormula);
        }

        todo.addAll(currentElement.getChildren());
      }
      return abstractionFormulas;
    }

    private List<Triple<ARTElement, CFANode, PredicateAbstractElement>> replaceFormulasInPath(
        List<Triple<ARTElement, CFANode, PredicateAbstractElement>> notRenamedPath,
        Map<ARTElement, Formula> blockFormulas) {

      List<Triple<ARTElement, CFANode, PredicateAbstractElement>> result = Lists.newArrayListWithExpectedSize(notRenamedPath.size());

      assert notRenamedPath.size() == blockFormulas.size();

      Region fakeRegion = new Region() { };

      for (Triple<ARTElement, CFANode, PredicateAbstractElement> abstractionPoint : notRenamedPath) {
        ARTElement oldARTElement = abstractionPoint.getFirst();

        Formula blockFormula = blockFormulas.get(oldARTElement);
        assert blockFormula != null;
        AbstractionFormula abs = new AbstractionFormula(fakeRegion, fmgr.makeTrue(), blockFormula);
        PredicateAbstractElement predicateElement = new PredicateAbstractElement.AbstractionElement(pfmgr.makeEmptyPathFormula(), abs);

        result.add(Triple.of(oldARTElement,
                             abstractionPoint.getSecond(),
                             predicateElement));
      }
      return result;
    }
  }
}