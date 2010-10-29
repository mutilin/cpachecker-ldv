package org.sosy_lab.cpachecker.util.ecp.translators;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.util.ecp.ECPEdgeSet;
import org.sosy_lab.cpachecker.util.ecp.ECPGuard;

public class GuardedEdgeLabel extends GuardedLabel {

  private static Map<ECPEdgeSet, Integer> mIds = new HashMap<ECPEdgeSet, Integer>();
  
  private final ECPEdgeSet mEdgeSet;
  
  public GuardedEdgeLabel(ECPEdgeSet pEdgeSet) {
    super();
    
    mEdgeSet = pEdgeSet;
  }
  
  public GuardedEdgeLabel(ECPEdgeSet pEdgeSet, ECPGuard pGuard) {
    super(pGuard);
    
    mEdgeSet = pEdgeSet;
  }

  public GuardedEdgeLabel(ECPEdgeSet pEdgeSet, Set<ECPGuard> pGuards) {
    super(pGuards);
    
    mEdgeSet = pEdgeSet;
  }

  /** copy constructor */
  public GuardedEdgeLabel(GuardedEdgeLabel pGuard) {
    this(pGuard.mEdgeSet, pGuard.getGuards());
  }
  
  public ECPEdgeSet getEdgeSet() {
    return mEdgeSet;
  }
  
  public boolean contains(CFAEdge pCFAEdge) {
    return mEdgeSet.contains(pCFAEdge);
  }
  
  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) {
      return true;
    }
    
    if (pOther == null) {
      return false;
    }
    
    if (pOther.getClass().equals(getClass())) {
      GuardedEdgeLabel lLabel = (GuardedEdgeLabel)pOther;
      
      return getGuards().equals(lLabel.getGuards()) && mEdgeSet.equals(lLabel.mEdgeSet);
    }
    
    return false;
  }
  
  @Override
  public int hashCode() {
    return getGuards().hashCode() + mEdgeSet.hashCode() + 234209;
  }

  @Override
  public <T> T accept(GuardedLabelVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }
  
  @Override
  public String toString() {
    if (!mIds.containsKey(mEdgeSet)) {
      mIds.put(mEdgeSet, mIds.size());
    }
    
    String lIdString = null;
    
    if (mEdgeSet.size() == 1) {
      lIdString = mEdgeSet.toString();
    }
    else {
      lIdString = "E" + mIds.get(mEdgeSet);
    }
    
    return lIdString + " " + getGuards().toString();
  }

}
