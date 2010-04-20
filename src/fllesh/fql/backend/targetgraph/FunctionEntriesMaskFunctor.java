package fllesh.fql.backend.targetgraph;

import org.jgrapht.graph.MaskFunctor;

import cfa.objectmodel.BlankEdge;
import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAEdgeType;
import cfa.objectmodel.CFANode;

public class FunctionEntriesMaskFunctor implements MaskFunctor<Node, Edge> {

  private static FunctionEntriesMaskFunctor mInstance = new FunctionEntriesMaskFunctor();
  
  private FunctionEntriesMaskFunctor() {
    
  }
  
  public static FunctionEntriesMaskFunctor getInstance() {
    return mInstance;
  }
  
  private boolean isFunctionEntryEdge(CFAEdge lEdge) {
    assert(lEdge != null);
    
    if (lEdge.getEdgeType().equals(CFAEdgeType.BlankEdge)) {
      BlankEdge lBlankEdge = (BlankEdge)lEdge;
      
      return lBlankEdge.getRawStatement().equals("Function start dummy edge");
    }
    
    return false;
  }
  
  @Override
  public boolean isEdgeMasked(Edge pArg0) {
    assert(pArg0 != null);
    
    return !isFunctionEntryEdge(pArg0.getCFAEdge());
  }

  @Override
  public boolean isVertexMasked(Node pArg0) {
    assert(pArg0 != null);
    
    CFANode lCFANode = pArg0.getCFANode();
    
    for (int lIndex = 0; lIndex < lCFANode.getNumEnteringEdges(); lIndex++) {
      if (isFunctionEntryEdge(lCFANode.getEnteringEdge(lIndex))) {
        return false;
      }
    }
    
    for (int lIndex = 0; lIndex < lCFANode.getNumLeavingEdges(); lIndex++) {
      if (isFunctionEntryEdge(lCFANode.getLeavingEdge(lIndex))) {
        return false;
      }
    }
    
    return true;
  }

}
