package org.sosy_lab.cpachecker.cpa.predicate;

import java.util.Collection;
import java.util.Set;

import org.sosy_lab.common.Timer;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElementHash;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.cpa.predicate.relevantpredicates.RelevantPredicatesComputer;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.predicates.interfaces.RegionManager;


public class PredicateReducer implements Reducer {

  public static Timer reduceTimer = new Timer();
  public static Timer expandTimer = new Timer();
    
  private final FormulaManager fmgr;
  private final PredicateRefinementManager<?, ?> pmgr;
  private final RelevantPredicatesComputer relevantComputer;
  
  public PredicateReducer(FormulaManager fmgr, PredicateRefinementManager<?, ?> pmgr, RelevantPredicatesComputer relevantComputer) {
    this.fmgr = fmgr;
    this.pmgr = pmgr;
    this.relevantComputer = relevantComputer;
  } 
  
  @Override
  public AbstractElement getVariableReducedElement(
      AbstractElement pExpandedElement, Block pContext,
      CFANode pLocation) {
    
    PredicateAbstractElement predicateElement = (PredicateAbstractElement)pExpandedElement;
    
    if (!(predicateElement instanceof PredicateAbstractElement.AbstractionElement)) {
      return predicateElement;
    }    
    
    reduceTimer.start();
    try {  
      AbstractionFormula abstractionFormula =
          predicateElement.getAbstractionFormula();

      Region oldRegion = predicateElement.getAbstractionFormula().asRegion();

      Collection<AbstractionPredicate> predicates =
          pmgr.extractPredicates(abstractionFormula.asRegion());
      Collection<AbstractionPredicate> removePredicates =
          relevantComputer.getIrrelevantPredicates(pContext, predicates);

      //System.out.println("=> Removing the following predicates: " + removePredicates);

      RegionManager bddRegionManager = BDDRegionManager.getInstance();
      Region newRegion = oldRegion;
      for (AbstractionPredicate predicate : removePredicates) {
        newRegion =
            bddRegionManager.makeExists(newRegion,
                predicate.getAbstractVariable());
      }

      //System.out.println("Resulting region: " + newRegion);

      PathFormula pathFormula = predicateElement.getPathFormula();
      Formula newFormula =
          fmgr.instantiate(pmgr.toConcrete(newRegion), pathFormula.getSsa());

      //System.out.println("New formula: " + newFormula);
      AbstractionFormula newAbstractionFormula =
            new AbstractionFormula(newRegion, newFormula, predicateElement
                .getAbstractionFormula().getBlockFormula());

      return new PredicateAbstractElement.AbstractionElement(pathFormula,
            newAbstractionFormula);
    } finally {
      reduceTimer.stop();
    }
  }

  @Override
  public AbstractElement getVariableExpandedElement(
      AbstractElement pRootElement, Block pRootContext,
      AbstractElement pReducedElement) {
    
    PredicateAbstractElement rootElement = (PredicateAbstractElement)pRootElement;
    PredicateAbstractElement reducedElement = (PredicateAbstractElement)pReducedElement;

    if (!(reducedElement instanceof PredicateAbstractElement.AbstractionElement)) { return reducedElement; }
    //Note: FCCP might introduce some additional abstraction if root region is not a cube 
    expandTimer.start();
    try {      

      AbstractionFormula rootElementAbstractionFormula =
          rootElement.getAbstractionFormula();

      Collection<AbstractionPredicate> rootPredicates =
          pmgr.extractPredicates(rootElementAbstractionFormula.asRegion());
      Collection<AbstractionPredicate> relevantRootPredicates =
          relevantComputer.getRelevantPredicates(pRootContext, rootPredicates);
      //for each removed predicate, we have to lookup the old (expanded) value and insert it to the reducedElements region

      Region reducedRegion = reducedElement.getAbstractionFormula().asRegion();
      Region rootRegion = rootElement.getAbstractionFormula().asRegion();

      RegionManager bddRegionManager = BDDRegionManager.getInstance();
      Region removedInformationRegion = rootRegion;
      for (AbstractionPredicate predicate : relevantRootPredicates) {
        removedInformationRegion =
            bddRegionManager.makeExists(removedInformationRegion,
                predicate.getAbstractVariable());
      }
  
      //System.out.println("Removed information region: " + removedInformationRegion);

      Region expandedRegion =
          bddRegionManager.makeAnd(reducedRegion, removedInformationRegion);

      PathFormula pathFormula = reducedElement.getPathFormula();

      //pathFormula.getSSa() might not contain index for the newly added variables in predicates; while the actual index is not really important at this point,
      //there still should be at least _some_ index for each variable of the abstraction formula.
      SSAMapBuilder builder = pathFormula.getSsa().builder();
      for (String var : rootElement.getPathFormula().getSsa().allVariables()) {
        //if we do not have the index in the reduced map..
        if (pathFormula.getSsa().getIndex(var) == -1) {
          //add an index (with the value of rootSSA)
          builder.setIndex(var,
              rootElement.getPathFormula().getSsa().getIndex(var));
        }
      }
      SSAMap newSSA = builder.build();
      pathFormula = pmgr.getPathFormulaManager().makeNewPathFormula(pathFormula, newSSA);

      Formula newFormula =
          fmgr.instantiate(pmgr.toConcrete(expandedRegion),
              pathFormula.getSsa());
      Formula blockFormula =
          reducedElement.getAbstractionFormula().getBlockFormula();

      AbstractionFormula newAbstractionFormula =
          new AbstractionFormula(expandedRegion, newFormula, blockFormula);
      
      return new PredicateAbstractElement.AbstractionElement(pathFormula,
          newAbstractionFormula);
    } finally {
      expandTimer.stop();
    }
  }

  @Override
  public boolean isEqual(AbstractElement pReducedTargetElement,
      AbstractElement pCandidateElement) {

    PredicateAbstractElement reducedTargetElement = (PredicateAbstractElement)pReducedTargetElement;
    PredicateAbstractElement candidateElement = (PredicateAbstractElement)pCandidateElement;
    
    return candidateElement.getAbstractionFormula().asRegion().equals(reducedTargetElement.getAbstractionFormula().asRegion());
  }

  @Override
  public AbstractElementHash getHashCodeForElement(AbstractElement pElementKey,
      Precision pPrecisionKey, Block pContext, BlockPartitioning pPartitioning) {
    
    PredicateAbstractElement element = (PredicateAbstractElement)pElementKey;
    PredicatePrecision precision = (PredicatePrecision)pPrecisionKey;
    
    return new PredicateElementHash(element, precision, pContext, pPartitioning);
  }

  private class PredicateElementHash implements AbstractElementHash {
    private final Block context;
    private final Region region;
    private final PredicatePrecision precision;
    private final BlockPartitioning partitioning;
    
    PredicateElementHash(PredicateAbstractElement element, PredicatePrecision precision, Block context, BlockPartitioning partitioning) {      
      this.precision = precision;
      this.context = context;
      this.region = element.getAbstractionFormula().asRegion();
      this.partitioning = partitioning;
    }
    
    @Override
    public boolean equals(Object other) {
      if(!(other instanceof PredicateElementHash)) {
       return false; 
      }
      PredicateElementHash hOther = (PredicateElementHash)other;
      if(!region.equals(hOther.region)) {
        return false;
      }        
      assert context.equals(hOther.context);
      return relevantComparePrecisions(hOther.precision);
    }
    
    private boolean relevantComparePrecisions(PredicatePrecision otherPrecision) {
      
      Set<CFANode> functionNodes = context.getNodes();   
      
      Collection<AbstractionPredicate> globalPreds1 = relevantComputer.getRelevantPredicates(context, precision.getGlobalPredicates());
      Collection<AbstractionPredicate> globalPreds2 = relevantComputer.getRelevantPredicates(context, otherPrecision.getGlobalPredicates());
      if(!globalPreds1.equals(globalPreds2)) {
        return false;
      }
      
      for(CFANode node : functionNodes) {
        if(precision.getPredicateMap().keySet().contains(node) || otherPrecision.getPredicateMap().keySet().contains(node)) {
          Collection<AbstractionPredicate> set1 = precision.getPredicates(node);
          Collection<AbstractionPredicate> set2 = otherPrecision.getPredicates(node);
          if(partitioning.isCallNode(node) || partitioning.isReturnNode(node)) {
            set1 = relevantComputer.getRelevantPredicates(context, precision.getPredicates(node));
            set2 = relevantComputer.getRelevantPredicates(context, otherPrecision.getPredicates(node)); 
          } 
          
          if(!set1.equals(set2)) {
            return false;
          }
        }          
      }
      return true;
     }
    
    @Override
    public int hashCode() {
      return region.hashCode() * 17 + relevantComputeHashCode();
    }
    
    private int relevantComputeHashCode() {   
      int h = 1;
      Set<CFANode> functionNodes = context.getNodes();
      
      Set<AbstractionPredicate> globalPredicates = precision.getGlobalPredicates();
      globalPredicates = (Set<AbstractionPredicate>) relevantComputer.getRelevantPredicates(context, globalPredicates);
      h += globalPredicates.hashCode();
      
      for(CFANode node : precision.getPredicateMap().keySet()) {
        if(functionNodes.contains(node)) {
          Collection<AbstractionPredicate> set = precision.getPredicates(node);
          if(partitioning.isCallNode(node) || partitioning.isReturnNode(node)) {
            set = relevantComputer.getRelevantPredicates(context, set);
          }
          
          for(AbstractionPredicate predicate : set) {
            if(!globalPredicates.contains(predicate))
              h += set.hashCode();
          }
        }
      }
      return h;
    }
    
    @Override
    public String toString() {
      return region.toString();
    }
  }
}
