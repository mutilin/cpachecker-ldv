package org.sosy_lab.cpachecker.fllesh.cpa.composite;

import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.JoinOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class CompoundJoinOperator implements JoinOperator {

  private List<JoinOperator> mJoinOperators;
  
  public CompoundJoinOperator(List<JoinOperator> pJoinOperator) {
    mJoinOperators = new ArrayList<JoinOperator>(pJoinOperator);
  }
  
  @Override
  public AbstractElement join(AbstractElement pElement1,
      AbstractElement pElement2) throws CPAException {
    CompoundElement lElement1 = (CompoundElement)pElement1;
    CompoundElement lElement2 = (CompoundElement)pElement2;
    
    List<AbstractElement> lJoinedElements = new ArrayList<AbstractElement>(lElement1.size());
    
    for (int lIndex = 0; lIndex < lElement1.size(); lIndex++) {
      lJoinedElements.add(mJoinOperators.get(lIndex).join(lElement1.getSubelement(lIndex), lElement2.getSubelement(lIndex)));
    }
    
    return new CompoundElement(lJoinedElements);
  }

}
