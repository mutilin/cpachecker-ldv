package org.sosy_lab.cpachecker.cpa.cache;

import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/*
 * CAUTION: The cache for precision adjustment is only correct for CPAs that do 
 * _NOT_ depend on the reached set when performing prec.
 * 
 */
public class CachePrecisionAdjustment implements PrecisionAdjustment {

  private final PrecisionAdjustment mCachedPrecisionAdjustment;
  //private final Map<AbstractElement, Map<Precision, Triple<AbstractElement, Precision, Action>>> mCache;
  private final Map<Precision, Map<AbstractElement, Triple<AbstractElement, Precision, Action>>> mCache;
  
  public CachePrecisionAdjustment(PrecisionAdjustment pCachedPrecisionAdjustment) {
    mCachedPrecisionAdjustment = pCachedPrecisionAdjustment;
    //mCache = new HashMap<AbstractElement, Map<Precision, Triple<AbstractElement, Precision, Action>>>();
    mCache = new HashMap<Precision, Map<AbstractElement, Triple<AbstractElement, Precision, Action>>>();
  }
  
  @Override
  public Triple<AbstractElement, Precision, Action> prec(
      AbstractElement pElement, Precision pPrecision,
      UnmodifiableReachedSet pElements) throws CPAException {
    /*Map<Precision, Triple<AbstractElement, Precision, Action>> lCache = mCache.get(pElement);
    
    if (lCache == null) {
      lCache = new HashMap<Precision, Triple<AbstractElement, Precision, Action>>();
      mCache.put(pElement, lCache);
    }
    
    Triple<AbstractElement, Precision, Action> lResult = lCache.get(pPrecision);
    
    if (lResult == null) {
      lResult = mCachedPrecisionAdjustment.prec(pElement, pPrecision, pElements);
      lCache.put(pPrecision, lResult);
    }
    
    return lResult;*/
    
    Map<AbstractElement, Triple<AbstractElement, Precision, Action>> lCache = mCache.get(pPrecision);
    
    if (lCache == null) {
      lCache = new HashMap<AbstractElement, Triple<AbstractElement, Precision, Action>>();
      mCache.put(pPrecision, lCache);
    }
    
    Triple<AbstractElement, Precision, Action> lResult = lCache.get(pElement);
    
    if (lResult == null) {
      lResult = mCachedPrecisionAdjustment.prec(pElement, pPrecision, pElements);
      lCache.put(pElement, lResult);
    }
    
    return lResult;
  }

}
