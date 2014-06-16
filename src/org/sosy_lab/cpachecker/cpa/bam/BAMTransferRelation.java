/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.blocks.BlockPartitioning;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm.CPAAlgorithmFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackCPA;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackReducer;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackTransferRelation;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsCPA;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsPrecision;
import org.sosy_lab.cpachecker.cpa.lockstatistics.LockStatisticsReducer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.CPAs;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

public class BAMTransferRelation implements TransferRelation, BAMRestoreStack {

  @Options
  static class PCCInformation {

    @Option(name = "pcc.proofgen.doPCC", description = "")
    private boolean doPCC = false;

    private static PCCInformation instance = null;

    private PCCInformation(Configuration pConfig) throws InvalidConfigurationException {
      pConfig.inject(this);
    }

    public static void instantiate(Configuration pConfig) throws InvalidConfigurationException {
      instance = new PCCInformation(pConfig);
    }

    public static boolean isPCCEnabled() {
      return instance.doPCC;
    }

  }

  private final BAMCache argCache;

  final Map<AbstractState, ReachedSet> abstractStateToReachedSet = new HashMap<>();
  final Map<AbstractState, AbstractState> expandedToReducedCache = new HashMap<>();

  private Block currentBlock;
  private LinkedList<Block> BlockStack = new LinkedList<>();

  private BlockPartitioning partitioning;
  private int depth = 0;

  private final LogManager logger;
  private final CPAAlgorithmFactory algorithmFactory;
  private final TransferRelation wrappedTransfer;
  private final ReachedSetFactory reachedSetFactory;
  private final Reducer wrappedReducer;
  private final BAMCPA bamCPA;
  private final ProofChecker wrappedProofChecker;
  private final String entryFunction;
  private final CallstackTransferRelation callstackTransfer;

  private Map<AbstractState, Precision> forwardPrecisionToExpandedPrecision;
  private Map<Pair<ARGState, Block>, Collection<ARGState>> correctARGsForBlocks = null;

  //Stats
  int maxRecursiveDepth = 0;

  final Timer recomputeARTTimer = new Timer();
  final Timer removeCachedSubtreeTimer = new Timer();
  final Timer removeSubtreeTimer = new Timer();

  boolean breakAnalysis = false;
  public BAMTransferRelation(Configuration pConfig, LogManager pLogger, BAMCPA bamCpa,
                             ProofChecker wrappedChecker, BAMCache cache,
      ReachedSetFactory pReachedSetFactory, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
    logger = pLogger;
    algorithmFactory = new CPAAlgorithmFactory(bamCpa, logger, pConfig, pShutdownNotifier);
    reachedSetFactory = pReachedSetFactory;
    wrappedTransfer = bamCpa.getWrappedCpa().getTransferRelation();
    wrappedReducer = bamCpa.getReducer();
    callstackTransfer = (CallstackTransferRelation) (CPAs.retrieveCPA(bamCpa, CallstackCPA.class)).getTransferRelation();
    PCCInformation.instantiate(pConfig);
    bamCPA = bamCpa;
    wrappedProofChecker = wrappedChecker;
    argCache = cache;
    LockStatisticsCPA tmpCpa;
    if ((tmpCpa = CPAs.retrieveCPA(bamCpa, LockStatisticsCPA.class)) != null) {
      ((LockStatisticsReducer)tmpCpa.getReducer()).setRestorator(this);
      ((LockStatisticsReducer)tmpCpa.getReducer()).setCallstackReducer((CallstackReducer)CPAs.retrieveCPA(bamCPA, CallstackCPA.class).getReducer());
    }

    assert wrappedReducer != null;

    entryFunction = pConfig.getProperty("analysis.entryFunction");
  }


  void setForwardPrecisionToExpandedPrecision(
      Map<AbstractState, Precision> pForwardPrecisionToExpandedPrecision) {
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
  public Collection<? extends AbstractState> getAbstractSuccessors(
          final AbstractState pState, final Precision pPrecision, final CFAEdge edge)
          throws CPATransferException, InterruptedException {
    final Collection<? extends AbstractState> successors = getAbstractSuccessorsWithoutWrapping(pState, pPrecision, edge);
    return attachAdditionalInfoToCallNodes(successors);
  }

  private Collection<? extends AbstractState> getAbstractSuccessorsWithoutWrapping(
            final AbstractState pState, final Precision pPrecision, final CFAEdge edge)
    throws CPATransferException, InterruptedException {

    forwardPrecisionToExpandedPrecision.clear();
    if (edge != null) {
      // TODO when does this happen?
      return getAbstractSuccessors0(pState, pPrecision, edge);
    }

    CFANode node = extractLocation(pState);

    if (!partitioning.isCallNode(node)) {
      // the easy case: we are in the middle of a block, so just forward to wrapped CPAs
      List<AbstractState> result = new ArrayList<>();
      for (CFAEdge e : CFAUtils.leavingEdges(node)) {

        if (e instanceof CFunctionCallEdge && e.getSuccessor().getFunctionName().equals("kvprintf")) {
          callstackTransfer.setFlag();
          return attachAdditionalInfoToCallNodes(wrappedTransfer.getAbstractSuccessors(pState, pPrecision, getSummaryEdge(node)));
        }
        if (isRecursionEdge(e)) {
          callstackTransfer.setFlag();
          result.addAll(getAbstractSuccessors0(pState, pPrecision, getSummaryEdge(node)));
          break;
        }

        Collection<? extends AbstractState> tmpResult = getAbstractSuccessors0(pState, pPrecision, e);
        result.addAll(tmpResult);
        if (e instanceof CFunctionReturnEdge && tmpResult.size() > 0) {
          setLockStatisticsPrecision(AbstractStates.extractStateByType(tmpResult.iterator().next(), CallstackState.class),
              pPrecision);
        }
      }
      return result;
    }

    if (partitioning.getBlockForCallNode(node).equals(currentBlock)) {
      // we are already in same context
      // thus we already did the recursive call or we have a recursion in the cachedSubtrees
      // the latter is not supported yet, but in the the former case we can classically do the post operation
      if (BlockStack.size() == 0) {
        BlockStack.add(currentBlock);
        setLockStatisticsPrecision(AbstractStates.extractStateByType(pState, CallstackState.class), pPrecision);
      }
      return wrappedTransfer.getAbstractSuccessors(pState, pPrecision, null); // edge is null
    }
    Block nextBlock;
    nextBlock = partitioning.getBlockForCallNode(node);
    if (BlockStack.contains(nextBlock)) {
      logger.log(Level.INFO, "BlockStack contains nextBlock");
      //go by summaryEdge
      callstackTransfer.setFlag();
      return attachAdditionalInfoToCallNodes(wrappedTransfer.getAbstractSuccessors(pState, pPrecision, getSummaryEdge(node)));
    }

    if (isHeadOfMainFunction(node)) {
      // skip main function, TODO Why?
      return wrappedTransfer.getAbstractSuccessors(pState, pPrecision, null); // edge is null
    }

    // we are a the entryNode of a new block, so we have to start a recursive analysis
    return doRecursiveAnalysis(pState, pPrecision, node);
  }

  /** Enters a new block and performs a new analysis or returns result from cache. */
  private Collection<? extends AbstractState> doRecursiveAnalysis(
          final AbstractState pState, final Precision pPrecision, final CFANode node)
          throws CPATransferException, InterruptedException  {

    //Create ReachSet with node as initial element (+ add corresponding Location+CallStackElement)
    //do an CPA analysis to get the complete reachset
    //if lastElement is error State
    // -> return lastElement and break at precision adjustment
    //else
    // -> compute which states refer to return nodes
    // -> return these states as successor
    // -> cache the result

    logger.log(Level.FINER, "Starting recursive analysis of depth", ++depth);
    logger.log(Level.ALL, "Starting state:", pState);
    maxRecursiveDepth = Math.max(depth, maxRecursiveDepth);

    final Block outerSubtree = currentBlock;
    currentBlock = partitioning.getBlockForCallNode(node);

    BlockStack.add(currentBlock);
    Collection<Pair<AbstractState, Precision>> reducedResult = performCompositeAnalysis(pState, pPrecision, node);

    logger.log(Level.FINER, "Recursive analysis of depth", depth--, "finished");
    logger.log(Level.ALL, "Resulting states:", reducedResult);

    addBlockAnalysisInfo(pState);

    if (breakAnalysis) {
      // analysis aborted, so lets abort here too
      // TODO why return element?
      assert reducedResult.size() == 1;
      return Collections.singleton(Iterables.getOnlyElement(reducedResult).getFirst());
    }

    logger.log(Level.FINEST, "Expanding states with initial state", pState);
    logger.log(Level.FINEST, "Expanding states", reducedResult);

    final List<AbstractState> expandedResult = expandResultStates(reducedResult, outerSubtree, pState, pPrecision);

    logger.log(Level.ALL, "Expanded results:", expandedResult);

    currentBlock = outerSubtree;

    return expandedResult;
  }

  private Collection<? extends AbstractState> getAbstractSuccessors0(AbstractState pElement, Precision pPrecision,
      CFAEdge edge) throws CPATransferException, InterruptedException {
    assert edge != null;

    CFANode currentNode = edge.getPredecessor();

    Block currentNodeBlock = partitioning.getBlockForReturnNode(currentNode);
    /*if (currentNodeBlock != null && !currentBlock.equals(currentNodeBlock)
        && currentNodeBlock.getNodes().contains(edge.getSuccessor())) {
      // we are not analyzing the block corresponding to currentNode (currentNodeBlock) but the currentNodeBlock is inside of this block
      // avoid a reanalysis
      return Collections.emptySet();
    }*/

    if (currentBlock.isReturnNode(currentNode) && !currentBlock.getNodes().contains(edge.getSuccessor())) {
      // do not perform analysis beyond the current block
      return Collections.emptySet();
    }
    return wrappedTransfer.getAbstractSuccessors(pElement, pPrecision, edge);
  }


  static boolean isHeadOfMainFunction(CFANode currentNode) {
    return currentNode instanceof FunctionEntryNode && currentNode.getNumEnteringEdges() == 0;
  }

  private List<AbstractState> expandResultStates(
          final Collection<Pair<AbstractState, Precision>> reducedResult,
          final Block outerSubtree, final AbstractState state, final Precision precision) {
    final List<AbstractState> expandedResult = new ArrayList<>(reducedResult.size());
    for (Pair<AbstractState, Precision> reducedPair : reducedResult) {
      AbstractState reducedState = reducedPair.getFirst();
      Precision reducedPrecision = reducedPair.getSecond();

      AbstractState expandedState =
              wrappedReducer.getVariableExpandedState(state, currentBlock, reducedState);
      expandedToReducedCache.put(expandedState, reducedState);

      Precision expandedPrecision =
              wrappedReducer.getVariableExpandedPrecision(precision, outerSubtree, reducedPrecision);

      ((ARGState)expandedState).addParent((ARGState) state);
      expandedResult.add(expandedState);

      forwardPrecisionToExpandedPrecision.put(expandedState, expandedPrecision);
    }
    BlockStack.removeLast();
    setLockStatisticsPrecision(AbstractStates.extractStateByType(state, CallstackState.class).getPreviousState(), precision);
    return expandedResult;
  }

  private void setLockStatisticsPrecision(final CallstackState state, Precision pPrecision) {
    if (pPrecision instanceof WrapperPrecision) {
      LockStatisticsPrecision lockPrecision = ((WrapperPrecision) pPrecision).retrieveWrappedPrecision(LockStatisticsPrecision.class);

      if (lockPrecision != null) {
        try {
          CallstackState fullState = restoreCallstack(state);
          lockPrecision.setPreciseState(fullState);
        } catch (HandleCodeException e) {
          logger.log(Level.WARNING, "Can't restore callstack");
        }
      }
    }
  }

  private boolean isRecursionEdge(CFAEdge e) {
    if (e instanceof CFunctionCallEdge) {
      for (Block block : BlockStack) {
        if (block.getCallNode().equals(e.getSuccessor())) {
          return true;
        }
      }
    }
    return false;
  }

  private CFunctionSummaryStatementEdge getSummaryEdge(CFANode node) {
    CFAEdge e;
    for (int i = 0; i < node.getNumLeavingEdges(); i++) {
      e = node.getLeavingEdge(i);
      if (e instanceof CFunctionSummaryStatementEdge) {
        return (CFunctionSummaryStatementEdge)e;
      }
    }
    return null;
  }

  /** Analyse the block starting at node with initialState.
   * If there is a result in the cache, it is used,
   * otherwise a recursive CPAAlgorithm is started. */
  private Collection<Pair<AbstractState, Precision>> performCompositeAnalysis(
          final AbstractState initialState, final Precision initialPrecision, final CFANode node)
          throws InterruptedException, RecursiveAnalysisFailedException {

    logger.log(Level.FINEST, "Reducing state", initialState);
    final AbstractState reducedInitialState = wrappedReducer.getVariableReducedState(initialState, currentBlock, node);
    final Precision reducedInitialPrecision = wrappedReducer.getVariableReducedPrecision(initialPrecision, currentBlock);
    // try to get previously computed element from cache
    //cleanLockStatisticsPrecision(reducedInitialPrecision);
    final Pair<ReachedSet, Collection<AbstractState>> pair =
            argCache.get(reducedInitialState, reducedInitialPrecision, currentBlock);
    ReachedSet reached = pair.getFirst();
    final Collection<AbstractState> returnStates = pair.getSecond();

    final Collection<Pair<AbstractState, Precision>> result;

    setLockStatisticsPrecision(AbstractStates.extractStateByType(initialState, CallstackState.class), reducedInitialPrecision);
    if (returnStates != null) {
      assert reached != null;
      // cache hit, return element from cache
      logger.log(Level.FINEST, "Cache hit");
      result = imbueAbstractStatesWithPrecision(reached, returnStates);

    } else {
      if (reached == null) {
        // we have not even cached a partly computed reach-set,
        // so we must compute the subgraph specification from scratch
        reached = createInitialReachedSet(reducedInitialState, reducedInitialPrecision);
        //cleanLockStatisticsPrecision(reducedInitialPrecision);
        argCache.put(reducedInitialState, reducedInitialPrecision, currentBlock, reached);
        //setLockStatisticsPrecision(initialState, reducedInitialPrecision);
        logger.log(Level.FINEST, "Cache miss: starting recursive CPAAlgorithm with new initial reached-set.");
      } else {
        logger.log(Level.FINEST, "Partial cache hit: starting recursive CPAAlgorithm with partial reached-set.");
      }

      try {
        result = performCompositeAnalysisWithCPAAlgorithm(reached, reducedInitialState, reducedInitialPrecision);
      } catch (CPAException e) {
        throw new RecursiveAnalysisFailedException(e);
      }
    }

    abstractStateToReachedSet.put(initialState, reached);
    return result;
  }


  /** Analyse the block with a 'recursive' call to the CPAAlgorithm.
   * Then analyse the result and get the returnStates. */
  private Collection<Pair<AbstractState, Precision>> performCompositeAnalysisWithCPAAlgorithm(
          final ReachedSet reached, final AbstractState reducedInitialState, final Precision reducedInitialPrecision)
          throws InterruptedException, CPAException {

    // CPAAlgorithm is not re-entrant due to statistics
    CPAAlgorithm algorithm =  algorithmFactory.newInstance();

    algorithm.run(reached);
    // if the element is an error element
    final Collection<AbstractState> returnStates;
    final AbstractState lastState = reached.getLastState();
    if (isTargetState(lastState)) {
      //found a target state inside a recursive subgraph call
      //this needs to be propagated to outer subgraph (till main is reached)
      returnStates = Collections.singletonList(lastState);

    } else if (reached.hasWaitingState()) {
      //no target state, but waiting elements
      //analysis failed -> also break this analysis
      breakAnalysis = true;
      return Collections.singletonList(Pair.of(reducedInitialState, reducedInitialPrecision));
    } else {
      returnStates = AbstractStates.filterLocations(reached, currentBlock.getReturnNodes()).toList();
    }

    ARGState rootOfBlock = null;
    if (PCCInformation.isPCCEnabled()) {
      if (!(reached.getFirstState() instanceof ARGState)) {
        throw new CPATransferException("Cannot build proof, ARG, for BAM analysis.");
      }
      rootOfBlock = BAMARGUtils.copyARG((ARGState) reached.getFirstState());
    }
    argCache.put(reducedInitialState, reached.getPrecision(reached.getFirstState()),
                 currentBlock, returnStates, rootOfBlock);

    return imbueAbstractStatesWithPrecision(reached, returnStates);
  }

  private List<Pair<AbstractState, Precision>> imbueAbstractStatesWithPrecision(
      ReachedSet pReached, Collection<AbstractState> pElements) {
    List<Pair<AbstractState, Precision>> result = new ArrayList<>();
    for (AbstractState ele : pElements) {
      result.add(Pair.of(ele, pReached.getPrecision(ele)));
    }
    return result;
  }

  private Collection<? extends AbstractState> attachAdditionalInfoToCallNodes(
      Collection<? extends AbstractState> pSuccessors) {
    if (PCCInformation.isPCCEnabled()) {
      List<AbstractState> successorsWithExtendedInfo = new ArrayList<>(pSuccessors.size());
      for (AbstractState elem : pSuccessors) {
        if (!(elem instanceof ARGState)) { return pSuccessors; }
        if (!(elem instanceof BAMARGBlockStartState)) {
          successorsWithExtendedInfo.add(createAdditionalInfo((ARGState) elem));
        } else {
          successorsWithExtendedInfo.add(elem);
        }
      }
      return successorsWithExtendedInfo;
    }
    return pSuccessors;
  }

  protected AbstractState attachAdditionalInfoToCallNode(AbstractState pElem) {
    if (!(pElem instanceof BAMARGBlockStartState) && PCCInformation.isPCCEnabled() && pElem instanceof ARGState) { return createAdditionalInfo((ARGState) pElem); }
    return pElem;
  }

  private ARGState createAdditionalInfo(ARGState pElem) {
    CFANode node = AbstractStates.extractLocation(pElem);
    if (partitioning.isCallNode(node) && !partitioning.getBlockForCallNode(node).equals(currentBlock)) {
      BAMARGBlockStartState replaceWith = new BAMARGBlockStartState(pElem.getWrappedState(), null);
      replaceInARG(pElem, replaceWith);
      return replaceWith;
    }
    return pElem;
  }

  private void replaceInARG(ARGState toReplace, ARGState replaceWith) {
    if (toReplace.isCovered()) {
      replaceWith.setCovered(toReplace.getCoveringState());
    }
    toReplace.uncover();

    toReplace.replaceInARGWith(replaceWith);
  }

  private void addBlockAnalysisInfo(AbstractState pElement) throws CPATransferException {
    if (PCCInformation.isPCCEnabled()) {
      if (argCache.getLastAnalyzedBlock() == null || !(pElement instanceof BAMARGBlockStartState)) { throw new CPATransferException(
          "Cannot build proof, ARG, for BAM analysis."); }
      ((BAMARGBlockStartState) pElement).setAnalyzedBlock(argCache.getLastAnalyzedBlock());
    }
  }

  private ReachedSet createInitialReachedSet(AbstractState initialState, Precision initialPredicatePrecision) {
    ReachedSet reached = reachedSetFactory.create();
    reached.add(initialState, initialPredicatePrecision);
    return reached;
  }

  void removeSubtree(ARGReachedSet mainReachedSet, ARGPath pPath,
      ARGState element, List<Precision> pNewPrecisions,
      List<Class<? extends Precision>> pNewPrecisionTypes,
      Map<ARGState, ARGState> pPathElementToReachedState) {
    removeSubtreeTimer.start();

    final ARGSubtreeRemover argSubtreeRemover = new ARGSubtreeRemover(
            partitioning, wrappedReducer, argCache, reachedSetFactory, abstractStateToReachedSet,
            removeCachedSubtreeTimer, logger);
    argSubtreeRemover.removeSubtree(mainReachedSet, pPath, element,
            pNewPrecisions, pNewPrecisionTypes, pPathElementToReachedState);

    removeSubtreeTimer.stop();
  }

  public ARGState findPath(ARGState target,
      Map<ARGState, ARGState> pPathElementToReachedState, final CallstackState stack) throws InterruptedException, RecursiveAnalysisFailedException {

    final BAMCEXSubgraphComputer cexSubgraphComputer = new BAMCEXSubgraphComputer(
        partitioning, wrappedReducer, argCache, pPathElementToReachedState,
        abstractStateToReachedSet, expandedToReducedCache, logger);

        return cexSubgraphComputer.findPath(target, pPathElementToReachedState, stack);
  }

     ARGState computeCounterexampleSubgraph(ARGState target, ARGReachedSet reachedSet,
        Map<ARGState, ARGState> pPathElementToReachedState)
throws InterruptedException, RecursiveAnalysisFailedException {
    final BAMCEXSubgraphComputer cexSubgraphComputer = new BAMCEXSubgraphComputer(
            partitioning, wrappedReducer, argCache, pPathElementToReachedState,
            abstractStateToReachedSet, expandedToReducedCache, logger);
    return cexSubgraphComputer.computeCounterexampleSubgraph(target, reachedSet, new BAMCEXSubgraphComputer.BackwardARGState(target));
  }

  public void clearCaches() {
    argCache.clear();
    abstractStateToReachedSet.clear();
    expandedToReducedCache.clear();
  }

  Pair<Block, ReachedSet> getCachedReachedSet(ARGState root, Precision rootPrecision) {
    CFANode rootNode = extractLocation(root);
    Block rootSubtree = partitioning.getBlockForCallNode(rootNode);

    ReachedSet reachSet = abstractStateToReachedSet.get(root);
    assert reachSet != null;
    return Pair.of(rootSubtree, reachSet);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(
      AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException,
      InterruptedException {
    return attachAdditionalInfoToCallNodes(wrappedTransfer.strengthen(pElement, pOtherElements, pCfaEdge, pPrecision));
  }

  public boolean areAbstractSuccessors(AbstractState pState, CFAEdge pCfaEdge,
      Collection<? extends AbstractState> pSuccessors) throws CPATransferException,
      InterruptedException {
    if (pCfaEdge != null) { return wrappedProofChecker.areAbstractSuccessors(pState, pCfaEdge, pSuccessors); }
    return areAbstractSuccessors0(pState, pSuccessors, partitioning.getMainBlock());
  }

  private boolean areAbstractSuccessors0(AbstractState pState,
      Collection<? extends AbstractState> pSuccessors, final Block currentBlock)
      throws CPATransferException,
      InterruptedException {
    // currently cannot deal with blocks for which the set of call nodes and return nodes of that block is not disjunct
    boolean successorExists;

    CFANode node = extractLocation(pState);

    if (partitioning.isCallNode(node) && !isHeadOfMainFunction(node)
        && !partitioning.getBlockForCallNode(node).equals(currentBlock)) {
      // do not support nodes which are call nodes of multiple blocks
      Block analyzedBlock = partitioning.getBlockForCallNode(node);
      try {
        if (!(pState instanceof BAMARGBlockStartState)
            || ((BAMARGBlockStartState) pState).getAnalyzedBlock() == null
            || !bamCPA.isCoveredBy(wrappedReducer.getVariableReducedStateForProofChecking(pState, analyzedBlock, node),
                ((BAMARGBlockStartState) pState).getAnalyzedBlock())) { return false; }
      } catch (CPAException e) {
        throw new CPATransferException("Missing information about block whose analysis is expected to be started at "
            + pState);
      }
      try {
        Collection<ARGState> endOfBlock;
        Pair<ARGState, Block> key = Pair.of(((BAMARGBlockStartState) pState).getAnalyzedBlock(), analyzedBlock);
        if (correctARGsForBlocks != null && correctARGsForBlocks.containsKey(key)) {
          endOfBlock = correctARGsForBlocks.get(key);
        } else {
          Pair<Boolean, Collection<ARGState>> result =
              checkARGBlock(((BAMARGBlockStartState) pState).getAnalyzedBlock(), analyzedBlock);
          if (!result.getFirst()) { return false; }
          endOfBlock = result.getSecond();
          setCorrectARG(key, endOfBlock);
        }

        HashSet<AbstractState> notFoundSuccessors = new HashSet<>(pSuccessors);
        AbstractState expandedState;

        Multimap<CFANode, AbstractState> blockSuccessors = HashMultimap.create();
        for (AbstractState absElement : pSuccessors) {
          ARGState successorElem = (ARGState) absElement;
          blockSuccessors.put(extractLocation(absElement), successorElem);
        }


        for (ARGState leaveB : endOfBlock) {
          successorExists = false;
          expandedState = wrappedReducer.getVariableExpandedStateForProofChecking(pState, analyzedBlock, leaveB);
          for (AbstractState next : blockSuccessors.get(extractLocation(leaveB))) {
            if (bamCPA.isCoveredBy(expandedState, next)) {
              successorExists = true;
              notFoundSuccessors.remove(next);
            }
          }
          if (!successorExists) { return false; }
        }

        if (!notFoundSuccessors.isEmpty()) { return false; }

      } catch (CPAException e) {
        throw new CPATransferException("Checking ARG with root " + ((BAMARGBlockStartState) pState).getAnalyzedBlock()
            + " for block " + currentBlock + "failed.");
      }
    } else {
      HashSet<CFAEdge> usedEdges = new HashSet<>();
      for (AbstractState absElement : pSuccessors) {
        ARGState successorElem = (ARGState) absElement;
        usedEdges.add(((ARGState) pState).getEdgeToChild(successorElem));
      }

      //no call node, check if successors can be constructed with help of CFA edges
      for (int i = 0; i < node.getNumLeavingEdges(); i++) {
        // edge leads to node in inner block
        Block currentNodeBlock = partitioning.getBlockForReturnNode(node);
        if (currentNodeBlock != null && !currentBlock.equals(currentNodeBlock)
            && currentNodeBlock.getNodes().contains(node.getLeavingEdge(i).getSuccessor())) {
          if (usedEdges.contains(node.getLeavingEdge(i))) { return false; }
          continue;
        }
        // edge leaves block, do not analyze, check for call node since if call node is also return node analysis will go beyond current block
        if (!currentBlock.isCallNode(node) && currentBlock.isReturnNode(node)
            && !currentBlock.getNodes().contains(node.getLeavingEdge(i).getSuccessor())) {
          if (usedEdges.contains(node.getLeavingEdge(i))) { return false; }
          continue;
        }
        if (!wrappedProofChecker.areAbstractSuccessors(pState, node.getLeavingEdge(i), pSuccessors)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public CallstackState restoreCallstack(CallstackState state) throws HandleCodeException {
    CallstackState fullState = null, tmpState;
    CFANode currentNode, previousNode, predecessor;

    previousNode = null;
    for (int i = 0; i < BlockStack.size(); i++) {
      currentNode = BlockStack.get(i).getCallNode();
      predecessor = currentNode;
      for (int j = 0; j < currentNode.getNumEnteringEdges(); j++) {
        predecessor = currentNode.getEnteringEdge(j).getPredecessor();
        if (previousNode == null && predecessor.getFunctionName().equals(entryFunction)) {
          break;
        } else if (previousNode != null && predecessor.getFunctionName().equals(previousNode.getFunctionName())) {
          break;
        }
      }
      fullState = new CallstackState(fullState, currentNode.getFunctionName(), predecessor);
      previousNode = currentNode;
    }
    tmpState = state;
    if (fullState != null) {
      if (tmpState.getCurrentFunction().equals(fullState.getCurrentFunction())) {
        return fullState;
      } else if (!tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() != null) {
        Stack<CallstackState> callstack = new Stack<>();
        while (!tmpState.getCurrentFunction().equals(fullState.getCurrentFunction())) {
          callstack.push(tmpState);
          tmpState = tmpState.getPreviousState();
          if (tmpState == null) {
            throw new HandleCodeException("Can't restore callstack");
          }
        }
        tmpState = fullState;
        CallstackState savedState;
        while (!callstack.empty()) {
          savedState = callstack.pop();
          tmpState = new CallstackState(tmpState, savedState.getCurrentFunction(), savedState.getCallNode());
        }
        return tmpState;
      } else/* if (!tmpState.getCurrentFunction().equals(fullState.getCurrentFunction()) && tmpState.getPreviousState() == null) */{
        currentNode = currentBlock.getCallNode();
        for (int i = 0; i < currentNode.getNumEnteringEdges(); i++) {
          previousNode = currentNode.getEnteringEdge(i).getPredecessor();
          if (previousNode.getFunctionName().equals(fullState.getCurrentFunction())) {
            tmpState = new CallstackState(fullState, tmpState.getCurrentFunction(), previousNode);
            return tmpState;
          }
        }
        throw new HandleCodeException("Can't find called function");
      }
    } else {
      return tmpState;
    }
  }

  private Pair<Boolean, Collection<ARGState>> checkARGBlock(ARGState rootNode,
      final Block currentBlock)
      throws CPAException, InterruptedException {
    Collection<ARGState> returnNodes = new ArrayList<>();
    Set<ARGState> waitingForUnexploredParents = new HashSet<>();
    boolean unexploredParent;
    Stack<ARGState> waitlist = new Stack<>();
    HashSet<ARGState> visited = new HashSet<>();
    HashSet<ARGState> coveredNodes = new HashSet<>();
    ARGState current;

    waitlist.add(rootNode);
    visited.add(rootNode);

    while (!waitlist.isEmpty()) {
      current = waitlist.pop();

      if (current.isTarget()) {
        returnNodes.add(current);
      }

      if (current.isCovered()) {
        coveredNodes.clear();
        coveredNodes.add(current);
        do {
          if (!bamCPA.isCoveredBy(current, current.getCoveringState())) {
            returnNodes = Collections.emptyList();
            return Pair.of(false, returnNodes);
          }
          coveredNodes.add(current);
          if (coveredNodes.contains(current.getCoveringState())) {
            returnNodes = Collections.emptyList();
            return Pair.of(false, returnNodes);
          }
          current = current.getCoveringState();
        } while (current.isCovered());

        if (!visited.contains(current)) {
          unexploredParent = false;
          for (ARGState p : current.getParents()) {
            if (!visited.contains(p) || waitlist.contains(p)) {
              waitingForUnexploredParents.add(current);
              unexploredParent = true;
              break;
            }
          }
          if (!unexploredParent) {
            visited.add(current);
            waitlist.add(current);
          }
        }
        continue;
      }

      CFANode node = extractLocation(current);
      if (currentBlock.isReturnNode(node)) {
        returnNodes.add(current);
      }

      if (!areAbstractSuccessors0(current, current.getChildren(), currentBlock)) {
        returnNodes = Collections.emptyList();
        return Pair.of(false, returnNodes);
      }

      for (ARGState child : current.getChildren()) {
        unexploredParent = false;
        for (ARGState p : child.getParents()) {
          if (!visited.contains(p) || waitlist.contains(p)) {
            waitingForUnexploredParents.add(child);
            unexploredParent = true;
            break;
          }
        }
        if (unexploredParent) {
          continue;
        }
        if (visited.contains(child)) {
          returnNodes = Collections.emptyList();
          return Pair.of(false, returnNodes);
        } else {
          waitingForUnexploredParents.remove(child);
          visited.add(child);
          waitlist.add(child);
        }
      }

    }
    if (!waitingForUnexploredParents.isEmpty()) {
      returnNodes = Collections.emptyList();
      return Pair.of(false, returnNodes);
    }
    return Pair.of(true, returnNodes);
  }

  public Collection<ReachedSet> getCachedReachedSet() {
    return argCache.getAllCachedReachedStates();
  }

  public void setCorrectARG(Pair<ARGState, Block> pKey, Collection<ARGState> pEndOfBlock){
    if (correctARGsForBlocks == null) {
      correctARGsForBlocks = new HashMap<>();
    }
    correctARGsForBlocks.put(pKey, pEndOfBlock);
  }
}
