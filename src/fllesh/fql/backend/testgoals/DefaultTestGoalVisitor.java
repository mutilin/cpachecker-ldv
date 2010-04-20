package fllesh.fql.backend.testgoals;

import fllesh.fql.backend.targetgraph.Edge;
import fllesh.fql.backend.targetgraph.Node;

public class DefaultTestGoalVisitor<T> implements TestGoalVisitor<T> {

  @Override
  public T visit(Node pNode) {
    throw new UnsupportedOperationException("visit(Node pNode) not implemented!");
  }

  @Override
  public T visit(Edge pEdge) {
    throw new UnsupportedOperationException("visit(Edge pEdge) not implemented!");
  }

  @Override
  public T visit(EdgeSequence pEdgeSequence) {
    throw new UnsupportedOperationException("visit(EdgeSequence pEdgeSequence) not implemented");
  }

}
