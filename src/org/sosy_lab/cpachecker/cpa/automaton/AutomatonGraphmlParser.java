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
package org.sosy_lab.cpachecker.cpa.automaton;

import static org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.SINK_NODE_ID;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CParser;
import org.sosy_lab.cpachecker.cfa.CProgramScope;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.parser.Scope;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonBoolExpr.And;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonBoolExpr.Negation;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonBoolExpr.Or;
import org.sosy_lab.cpachecker.exceptions.CParserException;
import org.sosy_lab.cpachecker.util.SourceLocationMapper.OriginDescriptor;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlTag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

@Options(prefix="spec")
public class AutomatonGraphmlParser {

  @Option(secure=true, description="Consider the negative semantics of tokens provided with path automatons.")
  private boolean considerNegativeSemanticsAttribute = false; // legacy: token matching needs this

  @Option(secure=true, description="Consider assumptions that are provided with the path automaton?")
  private boolean considerAssumptions = true;

  @Option(secure=true, description="Legacy option for token-based matching with path automatons.")
  private boolean transitionToStopForNegatedTokensetMatch = false; // legacy: tokenmatching

  @Option(secure=true, description="Match the source code provided with the witness.")
  private boolean matchSourcecodeData = false;

  @Option(secure=true, description="Match the line numbers within the origin (mapping done by preprocessor line markers).")
  private boolean matchOriginLine = true;

  @Option(secure=true, description="File for exporting the path automaton in DOT format.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path automatonDumpFile = null;

  private Scope scope;
  private LogManager logger;
  private Configuration config;
  private MachineModel machine;

  public AutomatonGraphmlParser(Configuration pConfig, LogManager pLogger, MachineModel pMachine, Scope pScope) throws InvalidConfigurationException {
    pConfig.inject(this);

    this.scope = pScope;
    this.machine = pMachine;
    this.logger = pLogger;
    this.config = pConfig;
  }

  private static abstract class CollectorOnExprLeaf<R extends Comparable<?>> {

    protected abstract Set<R> collectFromLeafExpr(AutomatonBoolExpr expr, boolean excludeNegations);

    public Set<R> collectFromExpr(AutomatonBoolExpr expr, boolean excludeNegations) {
      if (expr instanceof And) {
        Set<R> result = Sets.newTreeSet();
        And andExpr = (And) expr;
        result.addAll(collectFromExpr(andExpr.getA(), excludeNegations));
        result.addAll(collectFromExpr(andExpr.getB(), excludeNegations));
        return result;
      } else if (expr instanceof Or) {
        Set<R> result = Sets.newTreeSet();
        Or orExpr = (Or) expr;
        result.addAll(collectFromExpr(orExpr.getA(), excludeNegations));
        result.addAll(collectFromExpr(orExpr.getB(), excludeNegations));
        return result;
      } else if (expr instanceof Negation) {
        if (!excludeNegations) {
          Negation negExpr = (Negation) expr;
          return collectFromExpr(negExpr.getA(), excludeNegations);
        }
     } else {
       return collectFromLeafExpr(expr, excludeNegations);
     }

     return Collections.emptySet();
    }

  }

  private static class TokenCollector extends CollectorOnExprLeaf<Comparable<Integer>> {

    @Override
    protected Set<Comparable<Integer>> collectFromLeafExpr(AutomatonBoolExpr pExpr, boolean pExcludeNegations) {
      if (pExpr instanceof AutomatonBoolExpr.MatchEdgeTokens) {
        return ((AutomatonBoolExpr.MatchEdgeTokens) pExpr).getMatchTokens();
      }

      return Collections.emptySet();
    }

  }

  private static class OriginLineCollector extends CollectorOnExprLeaf<Comparable<OriginDescriptor>> {

    @Override
    protected Set<Comparable<OriginDescriptor>> collectFromLeafExpr(AutomatonBoolExpr pExpr, boolean pExcludeNegations) {
      if (pExpr instanceof AutomatonBoolExpr.MatchStartingLineInOrigin) {
        Comparable<OriginDescriptor> od = ((AutomatonBoolExpr.MatchStartingLineInOrigin) pExpr).getMatchOriginDescriptor();
        return Collections.singleton(od);
      }

      return Collections.emptySet();
    }

  }

  private boolean leafSetsDisjoint(CollectorOnExprLeaf<? extends Comparable<?>> collector, List<AutomatonTransition> transitions) {
    Set<Comparable<?>> allItems = Sets.newTreeSet();
    for (AutomatonTransition t : transitions) {
      Set<? extends Comparable<?>> exprItems = collector.collectFromExpr(t.getTrigger(), true);
      if (exprItems.isEmpty()) {
        continue;
      }

      int differentTokensWithout = allItems.size();
      allItems.addAll(exprItems);
      int differentTokensWith = allItems.size();

      if (differentTokensWith - differentTokensWithout != exprItems.size()) {
        return false;
      }
    }
    return true;
  }

  private boolean tokenSetsDisjoint(List<AutomatonTransition> transitions) {
    CollectorOnExprLeaf<Comparable<Integer>> collector = new TokenCollector();
    return leafSetsDisjoint(collector, transitions);
  }

  private boolean originDescriptorsDisjoint(List<AutomatonTransition> transitions) {
    CollectorOnExprLeaf<Comparable<OriginDescriptor>> collector = new OriginLineCollector();
    return leafSetsDisjoint(collector, transitions);
  }

  /**
  * Parses a Specification File and returns the Automata found in the file.
   * @throws CParserException
  */
  public List<Automaton> parseAutomatonFile(Path pInputFile) throws InvalidConfigurationException {
    CParser cparser = CParser.Factory.getParser(config, logger, CParser.Factory.getOptions(config), machine);
    Set<AutomatonTransition> auxilaryTransitions = Sets.newHashSet();
    try (InputStream input = pInputFile.asByteSource().openStream()) {
      // Parse the XML document ----
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(input);
      doc.getDocumentElement().normalize();

      GraphMlDocumentData docDat = new GraphMlDocumentData(doc);

      // (The one) root node of the graph ----
      NodeList graphs = doc.getElementsByTagName(GraphMlTag.GRAPH.toString());
      Preconditions.checkArgument(graphs.getLength() == 1, "The graph file must describe exactly one automaton.");
      Node graphNode = graphs.item(0);

      // Extract the information on the automaton ----
      Node nameAttribute = graphNode.getAttributes().getNamedItem("name");
      String automatonName = nameAttribute == null ? "" : nameAttribute.getTextContent();
      String initialStateName = null;

      // Create transitions ----
      //AutomatonBoolExpr epsilonTrigger = new SubsetMatchEdgeTokens(Collections.<Comparable<Integer>>emptySet());
      NodeList edges = doc.getElementsByTagName(GraphMlTag.EDGE.toString());
      NodeList nodes = doc.getElementsByTagName(GraphMlTag.NODE.toString());
      Map<String, LinkedList<AutomatonTransition>> stateTransitions = Maps.newHashMap();
      Map<String, Deque<String>> stacks = Maps.newHashMap();

      // Create graph
      Multimap<String, Node> graph = HashMultimap.create();
      String entryNodeId = null;
      for (int i = 0; i < edges.getLength(); i++) {
        Node stateTransitionEdge = edges.item(i);

        String sourceStateId = docDat.getAttributeValue(stateTransitionEdge, "source", "Every transition needs a source!");
        graph.put(sourceStateId, stateTransitionEdge);
      }

      // Find entry
      for (int i = 0; i < nodes.getLength(); ++i) {
        Node node = nodes.item(i);
        if (Boolean.parseBoolean(docDat.getDataValueWithDefault(node, KeyDef.ISENTRYNODE, "false"))) {
          entryNodeId = docDat.getAttributeValue(node, "id", "Every node needs an id!");
          break;
        }
      }

      Preconditions.checkNotNull(entryNodeId, "You must define an entry node.");

      Queue<Node> waitingEdges = new ArrayDeque<>();
      waitingEdges.addAll(graph.get(entryNodeId));
      while (!waitingEdges.isEmpty()) {
        Node stateTransitionEdge = waitingEdges.poll();

        String sourceStateId = docDat.getAttributeValue(stateTransitionEdge, "source", "Every transition needs a source!");
        String targetStateId = docDat.getAttributeValue(stateTransitionEdge, "target", "Every transition needs a target!");

        waitingEdges.addAll(graph.get(targetStateId));

        Element targetStateNode = docDat.getNodeWithId(targetStateId);
        EnumSet<NodeFlag> targetNodeFlags = docDat.getNodeFlags(targetStateNode);

        List<AutomatonBoolExpr> emptyAssertions = Collections.emptyList();
        List<AutomatonAction> actions = Collections.emptyList();
        List<CStatement> assumptions = Lists.newArrayList();

        LinkedList<AutomatonTransition> transitions = stateTransitions.get(sourceStateId);
        if (transitions == null) {
          transitions = Lists.newLinkedList();
          stateTransitions.put(sourceStateId, transitions);
        }

        LinkedList<AutomatonTransition> targetStateTransitions = stateTransitions.get(targetStateId);
        if (targetStateTransitions == null) {
          targetStateTransitions = Lists.newLinkedList();
          stateTransitions.put(targetStateId, targetStateTransitions);
        }

        // Handle stack
        Deque<String> currentStack = stacks.get(sourceStateId);
        if (currentStack == null) {
          currentStack = new ArrayDeque<>();
          stacks.put(sourceStateId, currentStack);
        }
        Deque<String> newStack = currentStack;
        Set<String> functionEntries = docDat.getDataOnNode(stateTransitionEdge, KeyDef.FUNCTIONENTRY);
        if (!functionEntries.isEmpty()) {
          newStack = new ArrayDeque<>(newStack);
          newStack.push(Iterables.getOnlyElement(functionEntries));
        }
        Set<String> functionExits = docDat.getDataOnNode(stateTransitionEdge, KeyDef.FUNCTIONEXIT);
        if (!functionExits.isEmpty()) {
          String function = Iterables.getOnlyElement(functionExits);
          if (newStack.isEmpty()) {
            logger.log(Level.WARNING, "Trying to return from function", function, "although no function is on the stack.");
          } else {
            newStack = new ArrayDeque<>(newStack);
            String oldFunction = newStack.pop();
            assert oldFunction.equals(function);
          }
        }
        stacks.put(targetStateId, newStack);

        AutomatonBoolExpr conjunctedTriggers = AutomatonBoolExpr.TRUE;

        if (considerAssumptions) {
          Set<String> transAssumes = docDat.getDataOnNode(stateTransitionEdge, KeyDef.ASSUMPTION);
          Scope scope = this.scope;
          if (!newStack.isEmpty() && scope instanceof CProgramScope) {
            scope = ((CProgramScope) scope).createFunctionScope(newStack.peek());
          }
          for (String assumeCode : transAssumes) {
            assumptions.addAll(AutomatonASTComparator.generateSourceASTOfBlock(assumeCode, cparser, scope));
          }
        }

        if (matchOriginLine) {
          Set<String> originFileTags = docDat.getDataOnNode(stateTransitionEdge, KeyDef.ORIGINFILE);
          Preconditions.checkArgument(originFileTags.size() < 2, "At most one origin-file data tag must be provided for an edge!");

          Set<String> originLineTags = docDat.getDataOnNode(stateTransitionEdge, KeyDef.ORIGINLINE);
          Preconditions.checkArgument(originLineTags.size() <  2, "At most one origin-line data tag must be provided for each edge!");

          if (originLineTags.size() > 0) {
            Optional<String> matchOriginFileName = originFileTags.isEmpty() ? Optional.<String>absent() : Optional.of(originFileTags.iterator().next());
            int matchOriginLineNumber = Integer.parseInt(originLineTags.iterator().next());
            OriginDescriptor originDescriptor = new OriginDescriptor(matchOriginFileName, matchOriginLineNumber);

            conjunctedTriggers = new AutomatonBoolExpr.And(conjunctedTriggers,
                new AutomatonBoolExpr.MatchStartingLineInOrigin(originDescriptor, true));

            if (targetStateId.equalsIgnoreCase(SINK_NODE_ID) || targetNodeFlags.contains(NodeFlag.ISSINKNODE)) {
              // Transition to the BOTTOM state
              AutomatonBoolExpr trigger = new AutomatonBoolExpr.And(
                  new AutomatonBoolExpr.MatchPathRelevantEdgesBoolExpr(),
                  conjunctedTriggers);
              transitions.add(new AutomatonTransition(
                  trigger,
                  emptyAssertions, assumptions, actions, AutomatonInternalState.BOTTOM, null));
            } else {

              AutomatonBoolExpr lineMatchTrigger = new AutomatonBoolExpr.And(
                  new AutomatonBoolExpr.MatchPathRelevantEdgesBoolExpr(), conjunctedTriggers);

              AutomatonTransition tr = new AutomatonTransition(lineMatchTrigger, emptyAssertions, assumptions, actions, targetStateId);
              transitions.add(0, tr);

              AutomatonTransition trRepetition = new AutomatonTransition(lineMatchTrigger, emptyAssertions, assumptions, actions, targetStateId);
              auxilaryTransitions.add(trRepetition);
              targetStateTransitions.add(0, trRepetition);
            }
          } else {
            AutomatonTransition tr = new AutomatonTransition(
                new AutomatonBoolExpr.Negation(
                    new AutomatonBoolExpr.MatchPathRelevantEdgesBoolExpr()),
                      emptyAssertions, assumptions, actions, targetStateId);
            transitions.add(0, tr);
          }

        }
        if (matchSourcecodeData) {
          Set<String> sourceCodeDataTags = docDat.getDataOnNode(stateTransitionEdge, KeyDef.SOURCECODE);
          Preconditions.checkArgument(sourceCodeDataTags.size() < 2, "At most one source-code data tag must be provided!");
          if (sourceCodeDataTags.isEmpty()) {
            conjunctedTriggers = new AutomatonBoolExpr.And(conjunctedTriggers, new AutomatonBoolExpr.MatchCFAEdgeExact(""));
          } else {
            final String sourceCode = sourceCodeDataTags.iterator().next();
            conjunctedTriggers = new AutomatonBoolExpr.And(conjunctedTriggers, new AutomatonBoolExpr.MatchCFAEdgeExact(sourceCode));
          }

          if (targetStateId.equalsIgnoreCase(SINK_NODE_ID) || targetNodeFlags.contains(NodeFlag.ISSINKNODE)) {
            // Transition to the BOTTOM state
            transitions.add(new AutomatonTransition(conjunctedTriggers, emptyAssertions, actions, AutomatonInternalState.BOTTOM));
          } else {
            // Transition to the next state
            transitions.add(new AutomatonTransition(conjunctedTriggers, emptyAssertions, actions, targetStateId));
            transitions.add(new AutomatonTransition(new AutomatonBoolExpr.Negation(conjunctedTriggers), emptyAssertions, actions, AutomatonInternalState.BOTTOM));
          }
        } else {
          if (considerNegativeSemanticsAttribute) {
            Optional<Boolean> matchPositiveCase = Optional.absent();
            switch (docDat.getDataValueWithDefault(stateTransitionEdge, KeyDef.TOKENSNEGATED, "").toLowerCase()) {
              case "true":
                matchPositiveCase = Optional.of(false);
                break;
              case "false":
                matchPositiveCase = Optional.of(true);
                break;
            }
            conjunctedTriggers = new AutomatonBoolExpr.And(conjunctedTriggers, new AutomatonBoolExpr.MatchAssumeCase(matchPositiveCase));
          }
        }
      }

      // Create states ----
      List<AutomatonInternalState> automatonStates = Lists.newArrayList();
      for (String stateId : docDat.getIdToNodeMap().keySet()) {
        Element stateNode = docDat.getIdToNodeMap().get(stateId);
        EnumSet<NodeFlag> nodeFlags = docDat.getNodeFlags(stateNode);

        List<AutomatonTransition> transitions = stateTransitions.get(stateId);
        if (transitions == null) {
          transitions = Collections.emptyList();
        }

        if (nodeFlags.contains(NodeFlag.ISENTRY)) {
          Preconditions.checkArgument(initialStateName == null, "Only one entrynode is supported!");
          initialStateName = stateId;
        }

        // Determine if "matchAll" should be enabled
        boolean matchAll = !tokenSetsDisjoint(transitions) || !originDescriptorsDisjoint(transitions);

        // ...
        AutomatonInternalState state = new AutomatonInternalState(stateId, transitions, false, matchAll);
        automatonStates.add(state);
      }

      // Build and return the result ----
      Preconditions.checkNotNull(initialStateName, "Every graph needs a specified entry node!");
      Map<String, AutomatonVariable> automatonVariables = Collections.emptyMap();
      List<Automaton> result = Lists.newArrayList();
      Automaton automaton = new Automaton(automatonName, automatonVariables, automatonStates, initialStateName);
      result.add(automaton);

      if (automatonDumpFile != null) {
        try (Writer w = Files.openOutputFile(automatonDumpFile)) {
          automaton.writeDotFile(w);
        } catch (IOException e) {
         // logger.logUserException(Level.WARNING, e, "Could not write the automaton to DOT file");
        }
      }

      return result;

    } catch (FileNotFoundException e) {
      throw new InvalidConfigurationException("Invalid automaton file provided! File not found!: " + pInputFile.getPath());
    } catch (IOException | ParserConfigurationException | SAXException e) {
      throw new InvalidConfigurationException("Error while accessing automaton file!", e);
    } catch (InvalidAutomatonException e) {
      throw new InvalidConfigurationException("The automaton provided is invalid!", e);
    } catch (CParserException e) {
      throw new InvalidConfigurationException("The automaton contains invalid C code!", e);
    }
  }

  Set<Comparable<Integer>> parseTokens(final String tokenString) {
    Set<Comparable<Integer>> result = Sets.newTreeSet();
    String[] ranges = tokenString.trim().split(",");
    for (String range : ranges) {
      if (range.trim().isEmpty()) {
        continue;
      }
      String[] rangeDef = range.trim().split("-");
      int rangeStart = Integer.parseInt(rangeDef[0]);
      int rangeEnd = rangeStart;
      if (rangeDef.length > 1) {
        rangeEnd = Integer.parseInt(rangeDef[1]);
      }
      for (int tokenPos=rangeStart; tokenPos<=rangeEnd; tokenPos++) {
        result.add(tokenPos);
      }
    }
    return result;
  }

  private static class GraphMlDocumentData {

    private final HashMap<String, Optional<String>> defaultDataValues = Maps.newHashMap();
    private final Document doc;

    private Map<String, Element> idToNodeMap = null;

    public GraphMlDocumentData(Document doc) {
      this.doc = doc;
    }

    public EnumSet<NodeFlag> getNodeFlags(Element pStateNode) {
      EnumSet<NodeFlag> result = EnumSet.noneOf(NodeFlag.class);

      NodeList dataChilds = pStateNode.getElementsByTagName(GraphMlTag.DATA.toString());

      for (int i=0; i<dataChilds.getLength(); i++) {
        Node dataChild = dataChilds.item(i);
        Node attribute = dataChild.getAttributes().getNamedItem("key");
        Preconditions.checkNotNull(attribute, "Every data element must have a key attribute!");
        String key = attribute.getTextContent();
        NodeFlag flag = NodeFlag.getNodeFlagByKey(key);
        if (flag != null) {
          result.add(flag);
        }
      }

      return result;
    }

    public Map<String, Element> getIdToNodeMap() {
      if (idToNodeMap != null) {
        return idToNodeMap;
      }

      idToNodeMap = Maps.newHashMap();

      NodeList nodes = doc.getElementsByTagName(GraphMlTag.NODE.toString());
      for (int i=0; i<nodes.getLength(); i++) {
        Element stateNode = (Element) nodes.item(i);
        String stateId = getNodeId(stateNode);

        idToNodeMap.put(stateId, stateNode);
      }

      return idToNodeMap;
    }

    public String getAttributeValue(Node of, String attributeName, String exceptionMessage) {
      Node attribute = of.getAttributes().getNamedItem(attributeName);
      Preconditions.checkNotNull(attribute, exceptionMessage);
      return attribute.getTextContent();
    }

    private Optional<String> getDataDefault(KeyDef dataKey) {
      Optional<String> result = defaultDataValues.get(dataKey.id);
      if (result != null) {
        return result;
      }

      NodeList keyDefs = doc.getElementsByTagName(GraphMlTag.KEY.toString());
      for (int i=0; i<keyDefs.getLength(); i++) {
        Element keyDef = (Element) keyDefs.item(i);
        Node id = keyDef.getAttributes().getNamedItem("id");
        if (dataKey.id.equals(id.getTextContent())) {
          NodeList defaultTags = keyDef.getElementsByTagName(GraphMlTag.DEFAULT.toString());
          result = Optional.absent();
          if (defaultTags.getLength() > 0) {
            Preconditions.checkArgument(defaultTags.getLength() == 1);
            result = Optional.of(defaultTags.item(0).getTextContent());
          }
          defaultDataValues.put(dataKey.id, result);
          return result;
        }
      }
      return Optional.absent();
    }

    public String getNodeId(Node stateNode) {
      return getAttributeValue(stateNode, "id", "Every state needs an ID!");
    }

    public Element getNodeWithId(String nodeId) {
      Element result = getIdToNodeMap().get(nodeId);
      Preconditions.checkNotNull(result, "Node not found. Id: " + nodeId);
      return result;
    }

    public String getDataValueWithDefault(Node dataOnNode, KeyDef dataKey, final String defaultValue) {
      Set<String> values = getDataOnNode(dataOnNode, dataKey);
      if (values.size() == 0) {
        Optional<String> dataDefault = getDataDefault(dataKey);
        if (dataDefault.isPresent()) {
          return dataDefault.get();
        } else {
          return defaultValue;
        }
      } else {
        return values.iterator().next();
      }
    }

    private Set<String> getDataOnNode(Node node, final KeyDef dataKey) {
      Preconditions.checkNotNull(node);
      Preconditions.checkArgument(node.getNodeType() == Node.ELEMENT_NODE);

      Element nodeElement = (Element) node;
      Set<Node> dataNodes = findKeyedDataNode(nodeElement, dataKey);

      Set<String> result = Sets.newHashSet();
      for (Node n: dataNodes) {
        result.add(n.getTextContent());
      }

      return result;
    }

    private Set<Node> findKeyedDataNode(Element of, final KeyDef dataKey) {
      Set<Node> result = Sets.newHashSet();
      NodeList dataChilds = of.getElementsByTagName(GraphMlTag.DATA.toString());
      for (int i=0; i<dataChilds.getLength(); i++) {
        Node dataChild = dataChilds.item(i);
        Node attribute = dataChild.getAttributes().getNamedItem("key");
        Preconditions.checkNotNull(attribute, "Every data element must have a key attribute!");
        if (attribute.getTextContent().equals(dataKey.id)) {
          result.add(dataChild);
        }
      }
      return result;
    }

  }

  public static boolean isGraphmlAutomaton(Path pPath, LogManager pLogger) throws InvalidConfigurationException {
    try (InputStream input = pPath.asByteSource().openStream()) {
      SAXParserFactory.newInstance().newSAXParser().parse(input, new DefaultHandler());
      return true;
    } catch (FileNotFoundException e) {
      throw new InvalidConfigurationException("Invalid automaton file provided! File not found: " + pPath.getPath());
    } catch (IOException e) {
      throw new InvalidConfigurationException("Error while accessing automaton file", e);
    } catch (SAXException e) {
      return false;
    } catch (ParserConfigurationException e) {
      pLogger.logException(Level.WARNING, e, "SAX parser configured incorrectly. Could not determine whether or not the path describes a graphml automaton.");
      return false;
    }
  }

}
