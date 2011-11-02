/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.abm;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.AbstractElements.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Timer;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.cpa.art.ARTReachedSet;
import org.sosy_lab.cpachecker.cpa.art.Path;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractElements;
import org.sosy_lab.cpachecker.util.Precisions;

import com.google.common.collect.Iterables;

@Options(prefix="cpa.abm")
public class ABMTransferRelation implements TransferRelation {

  private class AbstractElementHash {

    private final Object wrappedHash;
    private final Block context;

    private final AbstractElement predicateKey;
    private final Precision precisionKey;

    public AbstractElementHash(AbstractElement pPredicateKey, Precision pPrecisionKey, Block pContext) {
      wrappedHash = wrappedReducer.getHashCodeForElement(pPredicateKey, pPrecisionKey);
      context = checkNotNull(pContext);

      predicateKey = pPredicateKey;
      precisionKey = pPrecisionKey;
    }

    @Override
    public boolean equals(Object pObj) {
      if (!(pObj instanceof AbstractElementHash)) {
        return false;
      }
      AbstractElementHash other = (AbstractElementHash)pObj;
      equalsTimer.start();
      try {
        return context.equals(other.context)
            && wrappedHash.equals(other.wrappedHash);
      } finally {
        equalsTimer.stop();
      }
    }

    @Override
    public int hashCode() {
      hashingTimer.start();
      try {
        return wrappedHash.hashCode() * 17 + context.hashCode();
      } finally {
        hashingTimer.stop();
      }
    }

    @Override
    public String toString() {
      return "AbstractElementHash [hash=" + hashCode() + ", wrappedHash=" + wrappedHash + ", context="
          + context + ", predicateKey=" + predicateKey + ", precisionKey="
          + precisionKey + "]";
    }
  }

  private class Cache {

    private final Map<AbstractElementHash, ReachedSet> preciseReachedCache = new HashMap<AbstractElementHash, ReachedSet>();
    private final Map<AbstractElementHash, ReachedSet> unpreciseReachedCache = new HashMap<AbstractElementHash, ReachedSet>();

    private final Map<AbstractElementHash, Collection<AbstractElement>> returnCache = new HashMap<AbstractElementHash, Collection<AbstractElement>>();

    private AbstractElementHash getHashCode(AbstractElement predicateKey, Precision precisionKey, Block context) {
      return new AbstractElementHash(predicateKey, precisionKey, context);
    }

    private void put(AbstractElement predicateKey, Precision precisionKey, Block context, ReachedSet item) {
      AbstractElementHash hash = getHashCode(predicateKey, precisionKey, context);
      assert !preciseReachedCache.containsKey(hash);
      preciseReachedCache.put(hash, item);
    }

    private void put(AbstractElement predicateKey, Precision precisionKey, Block context, Collection<AbstractElement> item) {
      AbstractElementHash hash = getHashCode(predicateKey, precisionKey, context);
      assert allElementsContainedInReachedSet(item, preciseReachedCache.get(hash));
      returnCache.put(hash, item);
    }

    private boolean allElementsContainedInReachedSet(Collection<AbstractElement> pElements, ReachedSet reached) {
      for(AbstractElement e : pElements) {
        if(!reached.contains(e)) {
          return false;
        }
      }
      return true;
    }

    private void removeReturnEntry(AbstractElement predicateKey, Precision precisionKey, Block context) {
      returnCache.remove(getHashCode(predicateKey, precisionKey, context));
    }

    private Pair<ReachedSet, Collection<AbstractElement>> get(AbstractElement predicateKey, Precision precisionKey, Block context) {
      AbstractElementHash hash = getHashCode(predicateKey, precisionKey, context);

      ReachedSet result = preciseReachedCache.get(hash);
      if(result != null) {
        return Pair.of(result, returnCache.get(hash));
      }

      result = unpreciseReachedCache.get(hash);
      if(result != null) {
        return Pair.of(result, returnCache.get(getHashCode(predicateKey, result.getPrecision(result.getFirstElement()), context)));
      }

      //search for similar entry
      Pair<ReachedSet, Collection<AbstractElement>> pair = lookForSimilarElement(predicateKey, precisionKey, context);
      if(pair != null) {
        //found similar element, use this
        unpreciseReachedCache.put(hash, pair.getFirst());
        return pair;
      }

      return Pair.of(null, null);
    }

    private Pair<ReachedSet, Collection<AbstractElement>> lookForSimilarElement(AbstractElement pPredicateKey, Precision pPrecisionKey, Block pContext) {
      searchingTimer.start();
      try {
        int min = Integer.MAX_VALUE;
        Pair<ReachedSet, Collection<AbstractElement>> result = null;

        for(AbstractElementHash cacheKey : preciseReachedCache.keySet()) {
          //searchKey != cacheKey, check whether it is the same if we ignore the precision
          AbstractElementHash ignorePrecisionSearchKey = getHashCode(pPredicateKey, cacheKey.precisionKey, pContext);
          if(ignorePrecisionSearchKey.equals(cacheKey)) {
            int distance = wrappedReducer.measurePrecisionDifference(pPrecisionKey, cacheKey.precisionKey);
            if(distance < min) { //prefer similar precisions
              min = distance;
              result = Pair.of(preciseReachedCache.get(ignorePrecisionSearchKey), returnCache.get(ignorePrecisionSearchKey));
            }
          }
        }

        return result;
      } finally {
        searchingTimer.stop();
      }
    }

    private void findCacheMissCause(AbstractElement pPredicateKey, Precision pPrecisionKey, Block pContext) {
      AbstractElementHash searchKey = getHashCode(pPredicateKey, pPrecisionKey, pContext);
      for(AbstractElementHash cacheKey : preciseReachedCache.keySet()) {
        assert !searchKey.equals(cacheKey);
        //searchKey != cacheKey, check whether it is the same if we ignore the precision
        AbstractElementHash ignorePrecisionSearchKey = getHashCode(pPredicateKey, cacheKey.precisionKey, pContext);
        if(ignorePrecisionSearchKey.equals(cacheKey)) {
          precisionCausedMisses++;
          return;
        }
        //precision was not the cause. Check abstraction.
        AbstractElementHash ignoreAbsSearchKey = getHashCode(cacheKey.predicateKey, pPrecisionKey, pContext);
        if(ignoreAbsSearchKey.equals(cacheKey)) {
          abstractionCausedMisses++;
          return;
        }
      }
      noSimilarCausedMisses++;
    }

    private void clear() {
      preciseReachedCache.clear();
      unpreciseReachedCache.clear();
      returnCache.clear();
    }

    private boolean containsPreciseKey(AbstractElement predicateKey, Precision precisionKey, Block context) {
      AbstractElementHash hash = getHashCode(predicateKey, precisionKey, context);
      return preciseReachedCache.containsKey(hash);
    }

    public void updatePrecisionForEntry(AbstractElement predicateKey, Precision precisionKey, Block context, Precision newPrecisionKey) {
      AbstractElementHash hash = getHashCode(predicateKey, precisionKey, context);
      ReachedSet reachedSet = preciseReachedCache.get(hash);
      if(reachedSet != null) {
        preciseReachedCache.remove(hash);
        preciseReachedCache.put(getHashCode(predicateKey, newPrecisionKey, context), reachedSet);
      }
    }
  }


  private final Cache artCache = new Cache();

  private final Map<AbstractElement, ReachedSet> abstractElementToReachedSet = new HashMap<AbstractElement, ReachedSet>();
  private final Map<AbstractElement, AbstractElement> expandedToReducedCache = new HashMap<AbstractElement, AbstractElement>();

  private Block currentBlock;
  private BlockPartitioning partitioning;
  private int depth = 0;

  private final LogManager logger;
  private final CPAAlgorithm algorithm;
  private final TransferRelation wrappedTransfer;
  private final ReachedSetFactory reachedSetFactory;
  private final Reducer wrappedReducer;

  private Map<AbstractElement, Precision> forwardPrecisionToExpandedPrecision;

  //Stats
  @Option(description="if enabled, the reached set cache is analysed for each cache miss to find the cause of the miss.")
  boolean gatherCacheMissStatistics = false;
  int cacheMisses = 0;
  int partialCacheHits = 0;
  int fullCacheHits = 0;
  int maxRecursiveDepth = 0;
  int abstractionCausedMisses = 0;
  int precisionCausedMisses = 0;
  int noSimilarCausedMisses = 0;

  final Timer hashingTimer = new Timer();
  final Timer equalsTimer = new Timer();
  final Timer recomputeARTTimer = new Timer();
  final Timer removeCachedSubtreeTimer = new Timer();
  final Timer removeSubtreeTimer = new Timer();
  final Timer searchingTimer = new Timer();

  public ABMTransferRelation(Configuration pConfig, LogManager pLogger, ABMCPA abmCpa, ReachedSetFactory pReachedSetFactory) throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    algorithm = new CPAAlgorithm(abmCpa, logger);
    reachedSetFactory = pReachedSetFactory;
    wrappedTransfer = abmCpa.getWrappedCpa().getTransferRelation();
    wrappedReducer = abmCpa.getReducer();
    assert wrappedReducer != null;
  }


  void setForwardPrecisionToExpandedPrecision(
      Map<AbstractElement, Precision> pForwardPrecisionToExpandedPrecision) {
    forwardPrecisionToExpandedPrecision = pForwardPrecisionToExpandedPrecision;
  }

  void setBlockPartitioning(BlockPartitioning pManager) {
    partitioning = pManager;
    currentBlock = partitioning.getMainBlock();
  }

  public BlockPartitioning getBlockPartitioning() {
    assert partitioning != null;
    return partitioning;
  }

  @Override
  public Collection<? extends AbstractElement> getAbstractSuccessors(
      AbstractElement pElement, Precision pPrecision, CFAEdge edge)
      throws CPATransferException, InterruptedException {

    forwardPrecisionToExpandedPrecision.clear();

    if (edge == null) {
      CFANode node = extractLocation(pElement);

      if (partitioning.isCallNode(node)) {
        //we have to start a recursive analysis
        if(partitioning.getBlockForCallNode(node).equals(currentBlock)) {
          //we are already in same context
          //thus we already did the recursive call or we a recursion in the cachedSubtrees
          //the latter isnt supported yet, but in the the former case we can classicaly do the post operation
          return wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, edge);
        }

        if (isHeadOfMainFunction(node)) {
          //skip main function
          return wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, edge);
        }

        //Create ReachSet with node as initial element (+ add corresponding Location+CallStackElement)
        //do an CPA analysis to get the complete reachset
        //if lastElement is error State
        // -> return lastElement and break at precision adjustment
        //else
        // -> compute which states refer to return nodes
        // -> return these states as successor
        // -> cache the result

        logger.log(Level.FINER, "Starting recursive analysis of depth", ++depth);
        logger.log(Level.ALL, "Starting element:", pElement);
        maxRecursiveDepth = Math.max(depth, maxRecursiveDepth);

        Block outerSubtree = currentBlock;
        currentBlock = partitioning.getBlockForCallNode(node);
        Collection<Pair<AbstractElement, Precision>> reducedResult = performCompositeAnalysis(pElement, pPrecision, node);

        logger.log(Level.FINER, "Recursive analysis of depth", depth--, "finished");
        logger.log(Level.ALL, "Resulting elements:", reducedResult);

        List<AbstractElement> expandedResult = new ArrayList<AbstractElement>(reducedResult.size());
        for (Pair<AbstractElement, Precision> reducedPair: reducedResult) {
          AbstractElement reducedElement = reducedPair.getFirst();
          Precision reducedPrecision = reducedPair.getSecond();

          ARTElement expandedElement = (ARTElement)wrappedReducer.getVariableExpandedElement(pElement, currentBlock, reducedElement);
          expandedToReducedCache.put(expandedElement, reducedElement);

          Precision expandedPrecision = wrappedReducer.getVariableExpandedPrecision(pPrecision, outerSubtree, reducedPrecision);

          expandedElement.addParent((ARTElement)pElement);
          expandedResult.add(expandedElement);

          forwardPrecisionToExpandedPrecision.put(expandedElement, expandedPrecision);
        }

        logger.log(Level.ALL, "Expanded results:", expandedResult);

        currentBlock = outerSubtree;

        return expandedResult;
      }
      else {
        List<AbstractElement> result = new ArrayList<AbstractElement>();
        for (int i = 0; i < node.getNumLeavingEdges(); i++) {
          CFAEdge e = node.getLeavingEdge(i);
          result.addAll(getAbstractSuccessors0(pElement, pPrecision, e));
        }
        return result;
      }
    } else {
      return getAbstractSuccessors0(pElement, pPrecision, edge);
    }
  }

  private Collection<? extends AbstractElement> getAbstractSuccessors0(AbstractElement pElement, Precision pPrecision, CFAEdge edge) throws CPATransferException, InterruptedException {
    assert edge != null;

    CFANode currentNode = edge.getPredecessor();

    Block currentNodeBlock = partitioning.getBlockForReturnNode(currentNode);
    if(currentNodeBlock != null && !currentBlock.equals(currentNodeBlock) && currentNodeBlock.getNodes().contains(edge.getSuccessor())) {
      // we are not analyzing the block corresponding to currentNode (currentNodeBlock) but the currentNodeBlock is inside of this block
      // avoid a reanalysis
      return Collections.emptySet();
    }

    if (currentBlock.isReturnNode(currentNode) && !currentBlock.getNodes().contains(edge.getSuccessor())) {
      // do not perform analysis beyond the current block
      return Collections.emptySet();
    }

    return wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, edge);
  }


  private boolean isHeadOfMainFunction(CFANode currentNode) {
    return currentNode instanceof CFAFunctionDefinitionNode && currentNode.getNumEnteringEdges() == 0;
  }


  private Collection<Pair<AbstractElement, Precision>> performCompositeAnalysis(AbstractElement initialElement, Precision initialPrecision, CFANode node) throws InterruptedException, RecursiveAnalysisFailedException {
    try {
      AbstractElement reducedInitialElement = wrappedReducer.getVariableReducedElement(initialElement, currentBlock, node);
      Precision reducedInitialPrecision = wrappedReducer.getVariableReducedPrecision(initialPrecision, currentBlock);
      Pair<ReachedSet, Collection<AbstractElement>> pair = artCache.get(reducedInitialElement, reducedInitialPrecision, currentBlock);
      ReachedSet reached = pair.getFirst();
      Collection<AbstractElement> returnElements = pair.getSecond();

      abstractElementToReachedSet.put(initialElement, reached);

      if (returnElements != null) {
        assert reached != null;
        fullCacheHits++;
        return imbueAbstractElementsWithPrecision(reached, returnElements);
      }

      if (reached != null) {
        //at least we have partly computed reach set cached
        partialCacheHits++;
      } else {
        //compute the subgraph specification from scratch
        cacheMisses++;

        if(gatherCacheMissStatistics) {
          artCache.findCacheMissCause(reducedInitialElement, reducedInitialPrecision, currentBlock);
        }

        reached = createInitialReachedSet(reducedInitialElement, reducedInitialPrecision);
        artCache.put(reducedInitialElement, reducedInitialPrecision, currentBlock, reached);
        abstractElementToReachedSet.put(initialElement, reached);
      }

      algorithm.run(reached);

      // if the element is an error element
      AbstractElement lastElement = reached.getLastElement();
      if (isTargetElement(lastElement)) {
        //found a target element inside a recursive subgraph call
        //this needs to be propagated to outer subgraph (till main is reached)
        returnElements = Collections.singletonList(lastElement);

      } else {

        returnElements = new ArrayList<AbstractElement>();
        for(CFANode returnNode : currentBlock.getReturnNodes()) {
          Iterables.addAll(returnElements, AbstractElements.filterLocation(reached, returnNode));
        }
      }

      artCache.put(reducedInitialElement, reached.getPrecision(reached.getFirstElement()), currentBlock, returnElements);

      return imbueAbstractElementsWithPrecision(reached, returnElements);
    } catch (CPAException e) {
      throw new RecursiveAnalysisFailedException(e);
    }
  }


  private List<Pair<AbstractElement, Precision>> imbueAbstractElementsWithPrecision(
      ReachedSet pReached, Collection<AbstractElement> pElements) {
    List<Pair<AbstractElement, Precision>> result = new ArrayList<Pair<AbstractElement,Precision>>();
    for(AbstractElement ele : pElements) {
      result.add(Pair.of(ele, pReached.getPrecision(ele)));
    }
    return result;
  }

  private ReachedSet createInitialReachedSet(AbstractElement initialElement, Precision initialPredicatePrecision) {
    ReachedSet reached = reachedSetFactory.create();
    reached.add(initialElement, initialPredicatePrecision);
    return reached;
  }

  void removeSubtree(ARTReachedSet mainReachedSet, Path pPath, ARTElement element, Precision newPrecision, Map<ARTElement, ARTElement> pPathElementToReachedElement) {
    removeSubtreeTimer.start();

    List<ARTElement> path = trimPath(pPath, element);
    assert path.get(path.size()-1).equals(element);

    Set<ARTElement> relevantCallNodes = getRelevantDefinitionNodes(path);

    Set<Pair<ARTReachedSet, ARTElement>> neededRemoveSubtreeCalls = new HashSet<Pair<ARTReachedSet,ARTElement>>();
    Set<Pair<ARTElement, ARTElement>> neededRemoveCachedSubtreeCalls = new HashSet<Pair<ARTElement, ARTElement>>();

    ARTElement lastElement = null;
    //iterate from root to element and remove all subtrees for subgraph calls
    for(ARTElement pathElement : Iterables.skip(path, 1)) {
      if(pathElement.equals(element)) {
        break;
      }

      if(relevantCallNodes.contains(pathElement)) {
        ARTElement currentElement = pPathElementToReachedElement.get(pathElement);

        if (lastElement == null) {
          neededRemoveSubtreeCalls.add(Pair.of(mainReachedSet, currentElement));
        } else {
          neededRemoveCachedSubtreeCalls.add(Pair.of(lastElement, currentElement));
        }

        lastElement = currentElement;
      }
    }

    ensureExactCacheHitsOnPath(mainReachedSet, pPath, element, newPrecision, pPathElementToReachedElement, neededRemoveCachedSubtreeCalls);

    for(Pair<ARTReachedSet, ARTElement> removeSubtreeArguments : neededRemoveSubtreeCalls) {
      removeSubtree(removeSubtreeArguments.getFirst(), removeSubtreeArguments.getSecond());
    }

    for(Pair<ARTElement, ARTElement> removeCachedSubtreeArguments : neededRemoveCachedSubtreeCalls) {
      removeCachedSubtree(removeCachedSubtreeArguments.getFirst(), removeCachedSubtreeArguments.getSecond(), null);
    }

    if(lastElement == null) {
      removeSubtree(mainReachedSet, pPathElementToReachedElement.get(element), newPrecision);
    } else {
      removeCachedSubtree(lastElement, pPathElementToReachedElement.get(element), newPrecision);
    }

    removeSubtreeTimer.stop();
  }

  private void ensureExactCacheHitsOnPath(ARTReachedSet mainReachedSet, Path pPath, ARTElement pElement, Precision newPrecision, Map<ARTElement, ARTElement> pPathElementToReachedElement, Set<Pair<ARTElement, ARTElement>> neededRemoveCachedSubtreeCalls) {
    Map<ARTElement, UnmodifiableReachedSet> pathElementToOuterReachedSet = new HashMap<ARTElement, UnmodifiableReachedSet>();
    Pair<Set<ARTElement>, Set<ARTElement>> pair = getCallAndReturnNodes(pPath, pathElementToOuterReachedSet, mainReachedSet.asReachedSet(), pPathElementToReachedElement);
    Set<ARTElement> callNodes = pair.getFirst();
    Set<ARTElement> returnNodes = pair.getSecond();

    Deque<ARTElement> remainingPathElements = new LinkedList<ARTElement>();
    for(int i = 0; i < pPath.size(); i++) {
      remainingPathElements.addLast(pPath.get(i).getFirst());
    }

    boolean starting = false;
    while(!remainingPathElements.isEmpty()) {
      ARTElement currentElement = remainingPathElements.pop();

      if (currentElement.equals(pElement)) {
        starting = true;
      }

      if(starting) {
        if(callNodes.contains(currentElement)) {
          ARTElement currentReachedElement = pPathElementToReachedElement.get(currentElement);
          CFANode node = currentReachedElement.retrieveLocationElement().getLocationNode();
          Block currentBlock = partitioning.getBlockForCallNode(node);
          AbstractElement reducedElement = wrappedReducer.getVariableReducedElement(currentReachedElement, currentBlock, node);

          removeUnpreciseCacheEntriesOnPath(currentElement, reducedElement, newPrecision, currentBlock, remainingPathElements, pPathElementToReachedElement, callNodes, returnNodes, pathElementToOuterReachedSet, neededRemoveCachedSubtreeCalls);
        }
      }
    }
  }

  private boolean removeUnpreciseCacheEntriesOnPath(ARTElement rootElement, AbstractElement reducedRootElement, Precision newPrecision, Block rootBlock, Deque<ARTElement> remainingPathElements, Map<ARTElement, ARTElement> pPathElementToReachedElement, Set<ARTElement> callNodes, Set<ARTElement> returnNodes, Map<ARTElement, UnmodifiableReachedSet> pathElementToOuterReachedSet, Set<Pair<ARTElement, ARTElement>> neededRemoveCachedSubtreeCalls) {
    UnmodifiableReachedSet outerReachedSet = pathElementToOuterReachedSet.get(rootElement);

    Precision rootPrecision = outerReachedSet.getPrecision(pPathElementToReachedElement.get(rootElement));
    Precision reducedNewPrecision = wrappedReducer.getVariableReducedPrecision(Precisions.replaceByType(rootPrecision, newPrecision, newPrecision.getClass()), rootBlock);

    UnmodifiableReachedSet innerReachedSet = abstractElementToReachedSet.get(pPathElementToReachedElement.get(rootElement));
    Precision usedPrecision = innerReachedSet.getPrecision(innerReachedSet.getFirstElement());

    //add precise key for new precision if needed
    if(!artCache.containsPreciseKey(reducedRootElement, reducedNewPrecision, rootBlock)) {
      ReachedSet reachedSet = createInitialReachedSet(reducedRootElement, reducedNewPrecision);
      artCache.put(reducedRootElement, reducedNewPrecision, rootBlock, reachedSet);
    }

    boolean isNewPrecisionEntry = usedPrecision.equals(reducedNewPrecision);

    //fine, this block will not lead to any problems anymore, but maybe inner blocks will?
    //-> check other (inner) blocks on path
    boolean foundInnerUnpreciseEntries = false;
    while(!remainingPathElements.isEmpty()) {
      ARTElement currentElement = remainingPathElements.pop();

      if(callNodes.contains(currentElement)) {
        ARTElement currentReachedElement = pPathElementToReachedElement.get(currentElement);
        CFANode node = currentReachedElement.retrieveLocationElement().getLocationNode();
        Block currentBlock = partitioning.getBlockForCallNode(node);
        AbstractElement reducedElement = wrappedReducer.getVariableReducedElement(currentReachedElement, currentBlock, node);

        boolean removedUnpreciseInnerBlock = removeUnpreciseCacheEntriesOnPath(currentElement, reducedElement, newPrecision, currentBlock, remainingPathElements, pPathElementToReachedElement, callNodes, returnNodes, pathElementToOuterReachedSet, neededRemoveCachedSubtreeCalls);
        if(removedUnpreciseInnerBlock) {
          //System.out.println("Innner context of " + rootBlock + " removed some unprecise entry");
          //ok we indeed found an inner block that was unprecise
          if(isNewPrecisionEntry && !foundInnerUnpreciseEntries) {
            //if we are in a reached set that already uses the new precision and this is the first such entry we have to remove the subtree starting from currentElement in the rootReachedSet
            neededRemoveCachedSubtreeCalls.add(Pair.of(pPathElementToReachedElement.get(rootElement), currentReachedElement));
            foundInnerUnpreciseEntries = true;
          }
        }
      }

      if(returnNodes.contains(currentElement)) {
        //our block ended. Leave..
        return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
      }
    }

    return foundInnerUnpreciseEntries || !isNewPrecisionEntry;
  }


  private void removeCachedSubtree(ARTElement rootElement, ARTElement removeElement, Precision newPrecision) {
    removeCachedSubtreeTimer.start();

    try {
      CFANode rootNode = rootElement.retrieveLocationElement().getLocationNode();

      logger.log(Level.FINER, "Remove cached subtree for ", removeElement, " (rootNode: ", rootNode, ") issued");

      Block rootSubtree = partitioning.getBlockForCallNode(rootNode);
      AbstractElement reducedRootElement = wrappedReducer.getVariableReducedElement(rootElement, rootSubtree, rootNode);
      ReachedSet reachedSet = abstractElementToReachedSet.get(rootElement);

      if(!reachedSet.contains(removeElement)) {
        //apparently, removeElement was removed due to prior deletions
        return;
      }

      Precision removePrecision = reachedSet.getPrecision(removeElement);
      Precision newReducedRemovePrecision = null;
      if(newPrecision != null) {
        newReducedRemovePrecision = wrappedReducer.getVariableReducedPrecision(Precisions.replaceByType(removePrecision, newPrecision, newPrecision.getClass()), rootSubtree);
      }

      assert !removeElement.getParents().isEmpty();

      Precision reducedRootPrecision = reachedSet.getPrecision(reachedSet.getFirstElement());
      artCache.removeReturnEntry(reducedRootElement, reducedRootPrecision, rootSubtree);

      logger.log(Level.FINEST, "Removing subtree, adding a new cached entry, and removing the former cached entries");

      if(removeSubtree(reachedSet, removeElement, newReducedRemovePrecision)) {
        artCache.updatePrecisionForEntry(reducedRootElement, reducedRootPrecision, rootSubtree, newReducedRemovePrecision);
      }

    }
    finally {
      removeCachedSubtreeTimer.stop();
    }
  }

  /**
   *
   * @param reachedSet
   * @param artElement
   * @param newPrecision
   * @return <code>true</code>, if the precision of the first element of the given reachedSet changed by this operation; <code>false</code>, otherwise.
   */
  private static boolean removeSubtree(ReachedSet reachedSet, ARTElement artElement, Precision newPrecision) {
    ARTReachedSet artReachSet = new ARTReachedSet(reachedSet);
    boolean updateCacheNeeded = artElement.getParents().contains(reachedSet.getFirstElement());
    removeSubtree(artReachSet, artElement, newPrecision);
    return updateCacheNeeded;
  }

  private static void removeSubtree(ARTReachedSet reachedSet, ARTElement artElement) {
    reachedSet.removeSubtree(artElement);
  }

  private static void removeSubtree(ARTReachedSet reachedSet, ARTElement artElement, Precision newPrecision) {
    if(newPrecision == null) {
      removeSubtree(reachedSet, artElement);
    } else {
      reachedSet.removeSubtree(artElement, newPrecision);
    }
  }

  private List<ARTElement> trimPath(Path pPath, ARTElement pElement) {
    List<ARTElement> result = new ArrayList<ARTElement>();

    for (Pair<ARTElement, CFAEdge> e : pPath) {
      result.add(e.getFirst());
      if (e.getFirst().equals(pElement)) {
        return result;
      }
    }
    throw new IllegalArgumentException("Element " + pElement + " could not be found in path " + pPath + ".");
  }

  private Set<ARTElement> getRelevantDefinitionNodes(List<ARTElement> path) {
    Deque<ARTElement> openCallElements = new ArrayDeque<ARTElement>();
    Deque<Block> openSubtrees = new ArrayDeque<Block>();

    ARTElement prevElement = path.get(1);
    for (ARTElement currentElement : Iterables.skip(path, 2)) {
      CFANode currNode = currentElement.retrieveLocationElement().getLocationNode();
      CFANode prevNode = prevElement.retrieveLocationElement().getLocationNode();
      if (partitioning.isCallNode(prevNode)
          && !partitioning.getBlockForCallNode(prevNode).equals(openSubtrees.peek())) {
        if (!(isHeadOfMainFunction(prevNode))) {
          openCallElements.push(prevElement);
          openSubtrees.push(partitioning.getBlockForCallNode(prevNode));
        }

      }
      while (!openSubtrees.isEmpty()
           && openSubtrees.peek().isReturnNode(prevNode)
           && !openSubtrees.peek().getNodes().contains(currNode)) {
        openCallElements.pop();
        openSubtrees.pop();
      }
      prevElement = currentElement;
    }

    ARTElement lastElement = path.get(path.size()-1);
    if(partitioning.isCallNode(lastElement.retrieveLocationElement().getLocationNode())) {
      openCallElements.push(lastElement);
    }

    return new HashSet<ARTElement>(openCallElements);
  }

  private Pair<Set<ARTElement>, Set<ARTElement>> getCallAndReturnNodes(Path path, Map<ARTElement, UnmodifiableReachedSet> pathElementToOuterReachedSet, UnmodifiableReachedSet mainReachedSet, Map<ARTElement, ARTElement> pPathElementToReachedElement) {
    Set<ARTElement> callNodes = new HashSet<ARTElement>();
    Set<ARTElement> returnNodes = new HashSet<ARTElement>();

    Deque<Block> openSubtrees = new ArrayDeque<Block>();

    Deque<UnmodifiableReachedSet> openReachedSets = new ArrayDeque<UnmodifiableReachedSet>();
    openReachedSets.push(mainReachedSet);

    ARTElement prevElement = path.get(1).getFirst();
    for (Pair<ARTElement, CFAEdge> currentElementPair : Iterables.skip(path, 2)) {
      ARTElement currentElement = currentElementPair.getFirst();
      CFANode currNode = currentElement.retrieveLocationElement().getLocationNode();
      CFANode prevNode = prevElement.retrieveLocationElement().getLocationNode();

      pathElementToOuterReachedSet.put(prevElement, openReachedSets.peek());

      if (partitioning.isCallNode(prevNode)
          && !partitioning.getBlockForCallNode(prevNode).equals(openSubtrees.peek())) {
        if (!(isHeadOfMainFunction(prevNode))) {
          openSubtrees.push(partitioning.getBlockForCallNode(prevNode));
          openReachedSets.push(abstractElementToReachedSet.get(pPathElementToReachedElement.get(prevElement)));
          callNodes.add(prevElement);
        }
      }

      while (!openSubtrees.isEmpty()
           && openSubtrees.peek().isReturnNode(prevNode)
           && !openSubtrees.peek().getNodes().contains(currNode)) {
        openSubtrees.pop();
        openReachedSets.pop();
        returnNodes.add(prevElement);
      }

      prevElement = currentElement;
    }

    ARTElement lastElement = path.get(path.size()-1).getFirst();
    if(partitioning.isReturnNode(lastElement.retrieveLocationElement().getLocationNode())) {
      returnNodes.add(lastElement);
    }
    pathElementToOuterReachedSet.put(lastElement, openReachedSets.peek());

    return Pair.of(callNodes, returnNodes);
  }

  //returns root of a subtree leading from the root element of the given reachedSet to the target element
  //subtree is represented using children and parents of ARTElements, where newTreeTarget is the ARTElement
  //in the constructed subtree that represents target
  ARTElement computeCounterexampleSubgraph(ARTElement target, ARTReachedSet reachedSet, ARTElement newTreeTarget, Map<ARTElement, ARTElement> pPathElementToReachedElement) throws InterruptedException, RecursiveAnalysisFailedException {
    assert reachedSet.asReachedSet().contains(target);

    //start by creating ARTElements for each node needed in the tree
    Map<ARTElement, ARTElement> elementsMap = new HashMap<ARTElement, ARTElement>();
    Stack<ARTElement> openElements = new Stack<ARTElement>();
    ARTElement root = null;

    pPathElementToReachedElement.put(newTreeTarget, target);
    elementsMap.put(target, newTreeTarget);
    openElements.push(target);
    while(!openElements.empty()) {
      ARTElement currentElement = openElements.pop();

      assert reachedSet.asReachedSet().contains(currentElement);

      for(ARTElement parent : currentElement.getParents()) {
        if(!elementsMap.containsKey(parent)) {
          //create node for parent in the new subtree
          elementsMap.put(parent, new ARTElement(parent.getWrappedElement(), null));
          pPathElementToReachedElement.put(elementsMap.get(parent), parent);
          //and remember to explore the parent later
          openElements.push(parent);
        }
        CFAEdge edge = ABMARTUtils.getEdgeToChild(parent, currentElement);
        if(edge == null) {
          //this is a summarized call and thus an direct edge could not be found
          //we have the transfer function to handle this case, as our reachSet is wrong
          //(we have to use the cached ones)
          ARTElement innerTree = computeCounterexampleSubgraph(parent, reachedSet.asReachedSet().getPrecision(parent), elementsMap.get(currentElement), pPathElementToReachedElement);
          if(innerTree == null) {
            removeSubtree(reachedSet, parent);
            return null;
          }
          for(ARTElement child : innerTree.getChildren()) {
            child.addParent(elementsMap.get(parent));
          }
          innerTree.removeFromART();
        }
        else {
          //normal edge
          //create an edge from parent to current
          elementsMap.get(currentElement).addParent(elementsMap.get(parent));
        }
      }
      if(currentElement.getParents().isEmpty()) {
        root = elementsMap.get(currentElement);
      }
    }
    assert root != null;
    return root;
  }

  /**
   * This method looks for the reached set that belongs to (root, rootPrecision),
   * then looks for target in this reached set and constructs a tree from root to target
   * (recursively, if needed).
   * @throws RecursiveAnalysisFailedException
   */
  private ARTElement computeCounterexampleSubgraph(ARTElement root, Precision rootPrecision, ARTElement newTreeTarget, Map<ARTElement, ARTElement> pPathElementToReachedElement) throws InterruptedException, RecursiveAnalysisFailedException {
    CFANode rootNode = root.retrieveLocationElement().getLocationNode();
    Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

    AbstractElement reducedRootElement = wrappedReducer.getVariableReducedElement(root, rootSubtree, rootNode);
    ReachedSet reachSet = abstractElementToReachedSet.get(root);

    //we found the to the root and precision corresponding reach set
    //now try to find the target in the reach set
    ARTElement targetARTElement = (ARTElement) expandedToReducedCache.get(pPathElementToReachedElement.get(newTreeTarget));
    if(targetARTElement.isDestroyed()) {
      logger.log(Level.FINE, "Target element refers to a destroyed ARTElement, i.e., the cached subtree is outdated. Updating it.");
      return null;
    }
    assert reachSet.contains(targetARTElement);
    //we found the target; now construct a subtree in the ART starting with targetARTElement
    ARTElement result = computeCounterexampleSubgraph(targetARTElement, new ARTReachedSet(reachSet), newTreeTarget, pPathElementToReachedElement);
    if(result == null) {
      //enforce recomputation to update cached subtree
      artCache.removeReturnEntry(reducedRootElement, reachSet.getPrecision(reachSet.getFirstElement()), rootSubtree);
    }
    return result;
  }

  void clearCaches() {
    artCache.clear();
    abstractElementToReachedSet.clear();
  }

  Pair<Block, ReachedSet> getCachedReachedSet(ARTElement root, Precision rootPrecision) {
    CFANode rootNode = root.retrieveLocationElement().getLocationNode();
    Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

    ReachedSet reachSet = abstractElementToReachedSet.get(root);
    assert reachSet != null;
    return Pair.of(rootSubtree, reachSet);
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(
      AbstractElement pElement, List<AbstractElement> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException,
      InterruptedException {
    return wrappedTransfer.strengthen(pElement, pOtherElements, pCfaEdge, pPrecision);
  }

}
