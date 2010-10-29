package org.sosy_lab.cpachecker.util.ecp;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;

public class ECPEdgeSet implements ECPAtom, Iterable<CFAEdge> {

  private Set<CFAEdge> mCFAEdges = new HashSet<CFAEdge>();
  
  public ECPEdgeSet(Collection<CFAEdge> pCFAEdges) {
    mCFAEdges.addAll(pCFAEdges);
  }
  
  public ECPEdgeSet(CFAEdge pCFAEdge) {
    mCFAEdges.add(pCFAEdge);
  }
  
  public boolean contains(CFAEdge pCFAEdge) {
    return mCFAEdges.contains(pCFAEdge);
  }
  
  /** copy constructor */
  public ECPEdgeSet(ECPEdgeSet pEdgeSet) {
    this(pEdgeSet.mCFAEdges);
  }
  
  public ECPEdgeSet startIn(ECPNodeSet pNodeSet) {
    HashSet<CFAEdge> lResult = new HashSet<CFAEdge>();
    
    for (CFAEdge lEdge : mCFAEdges) {
      if (pNodeSet.contains(lEdge.getPredecessor())) {
        lResult.add(lEdge);
      }
    }
    
    return new ECPEdgeSet(lResult);
  }
  
  public ECPEdgeSet endIn(ECPNodeSet pNodeSet) {
    HashSet<CFAEdge> lResult = new HashSet<CFAEdge>();
    
    for (CFAEdge lEdge : mCFAEdges) {
      if (pNodeSet.contains(lEdge.getSuccessor())) {
        lResult.add(lEdge);
      }
    }
    
    return new ECPEdgeSet(lResult);
  }
  
  public ECPEdgeSet intersect(ECPEdgeSet pOther) {
    HashSet<CFAEdge> lIntersection = new HashSet<CFAEdge>();
    lIntersection.addAll(mCFAEdges);
    lIntersection.retainAll(pOther.mCFAEdges);
    
    return new ECPEdgeSet(lIntersection);
  }
  
  public ECPEdgeSet union(ECPEdgeSet pOther) {
    HashSet<CFAEdge> lUnion = new HashSet<CFAEdge>();
    lUnion.addAll(mCFAEdges);
    lUnion.addAll(pOther.mCFAEdges);
    
    return new ECPEdgeSet(lUnion);
  }
  
  @Override
  public int hashCode() {
    return mCFAEdges.hashCode();
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
      ECPEdgeSet lOther = (ECPEdgeSet)pOther;
      
      return mCFAEdges.equals(lOther.mCFAEdges);
    }
    
    return false;
  }
  
  public int size() {
    return mCFAEdges.size();
  }
  
  public boolean isEmpty() {
    return mCFAEdges.isEmpty();
  }
  
  @Override
  public String toString() {
    return mCFAEdges.toString();
  }
  
  @Override
  public Iterator<CFAEdge> iterator() {
    return mCFAEdges.iterator();
  }

  @Override
  public <T> T accept(ECPVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }

}
