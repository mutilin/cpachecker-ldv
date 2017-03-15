/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usage;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Optional;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import javax.xml.parsers.ParserConfigurationException;
import org.sosy_lab.common.Appender;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.AssumeCase;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.GraphMlBuilder;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeFlag;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.NodeType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

public class KleverErrorTracePrinter extends ErrorTracePrinter {

  public KleverErrorTracePrinter(Configuration c, BAMTransferRelation pT, LogManager pL) {
    super(c, pT, pL);
  }

  int idCounter = 0;
  private String getId() {
    return "A" + idCounter++;
  }

  @Override
  protected void printUnsafe(SingleIdentifier pId, Pair<UsageInfo, UsageInfo> pTmpPair) {
    UsageInfo firstUsage = pTmpPair.getFirst();
    UsageInfo secondUsage = pTmpPair.getSecond();
    List<CFAEdge> firstPath, secondPath;

    if (firstUsage.getPath() == null) {
      createPath(firstUsage);
    }
    firstPath = firstUsage.getPath();
    if (secondUsage.getPath() == null) {
      createPath(secondUsage);
    }
    secondPath = secondUsage.getPath();

    if (firstPath == null || secondPath == null) {
      return;
    }
    try {
      File name = new File("output/witness." + createUniqueName(pId) + ".graphml");
      String defaultSourcefileName = from(firstPath)
          .filter(FILTER_EMPTY_FILE_LOCATIONS).get(0).getFileLocation().getFileName();

      GraphMlBuilder builder = new GraphMlBuilder(WitnessType.VIOLATION_WITNESS, defaultSourcefileName, Language.C,
          MachineModel.LINUX64, new VerificationTaskMetaData(config, java.util.Optional.empty()));

      idCounter = 0;
      Element result = builder.createNodeElement("A0", NodeType.ONPATH);
      builder.addDataElementChild(result, NodeFlag.ISENTRY.key , "true");
      printPath(firstUsage, 0, builder);
      result = printPath(secondUsage, 1, builder);

      builder.addDataElementChild(result, NodeFlag.ISVIOLATION.key, "true");

      //builder.appendTo(w);
      MoreFiles.writeFile(Paths.get(name.getAbsolutePath()), Charset.defaultCharset(), (Appender) a -> builder.appendTo(a));
      //w.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    } catch (DOMException e1) {
      e1.printStackTrace();
    } catch (InvalidConfigurationException e1) {
      e1.printStackTrace();
    }

  }


  private Element printPath(UsageInfo usage, int threadId, GraphMlBuilder builder) {
    String currentId = getId(), nextId = currentId;
    SingleIdentifier pId = usage.getId();
    List<CFAEdge> path = usage.getPath();

    Iterator<CFAEdge> iterator = from(path)
               .filter(FILTER_EMPTY_FILE_LOCATIONS)
               .iterator();

    Optional<CFAEdge> warningEdge = from(path)
      .filter(e -> e.getLineNumber() == usage.getLine().getLine() && e.toString().contains(pId.getName()))
      .last();

    CFAEdge warning;

    if (warningEdge.isPresent()) {
      warning = warningEdge.get();
    } else {
      logger.log(Level.WARNING, "Can not determine an unsafe edge");
      warning = null;
    }
    Element result = null;
    Element lastWarningElement = null;

    while (iterator.hasNext()) {
      currentId = nextId;
      nextId = getId();
      CFAEdge pEdge = iterator.next();

      result = builder.createEdgeElement(currentId, nextId);
      dumpCommonInfoForEdge(builder, result, pEdge);
      builder.addDataElementChild(result, KeyDef.THREADID, Integer.toString(threadId));

      if (pEdge == warning) {
        lastWarningElement = result;
      }
      result = builder.createNodeElement(nextId, NodeType.ONPATH);
    }
    if (lastWarningElement != null) {
      builder.addDataElementChild(lastWarningElement, KeyDef.WARNING, usage.getWarningMessage());
    }

    //Special hack to connect two traces
    idCounter--;
    return result;
  }

  private void dumpCommonInfoForEdge(GraphMlBuilder builder, Element result, CFAEdge pEdge) {

    if (pEdge.getSuccessor() instanceof FunctionEntryNode) {
      FunctionEntryNode in = (FunctionEntryNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONENTRY, in.getFunctionName());

    }
    if (pEdge.getSuccessor() instanceof FunctionExitNode) {
      FunctionExitNode out = (FunctionExitNode) pEdge.getSuccessor();
      builder.addDataElementChild(result, KeyDef.FUNCTIONEXIT, out.getFunctionName());
    }

    if (pEdge instanceof AssumeEdge) {
      AssumeEdge a = (AssumeEdge) pEdge;
      AssumeCase assumeCase = a.getTruthAssumption() ? AssumeCase.THEN : AssumeCase.ELSE;
      builder.addDataElementChild(result, KeyDef.CONTROLCASE, assumeCase.toString());
    }

    FileLocation location = pEdge.getFileLocation();
    assert (location != null) : "should be filtered";
    builder.addDataElementChild(result, KeyDef.ORIGINFILE, location.getFileName());
    builder.addDataElementChild(result, KeyDef.STARTLINE, Integer.toString(location.getStartingLineInOrigin()));
    builder.addDataElementChild(result, KeyDef.OFFSET, Integer.toString(location.getNodeOffset()));

    if (!pEdge.getRawStatement().trim().isEmpty()) {
      builder.addDataElementChild(result, KeyDef.SOURCECODE, pEdge.getRawStatement());
    }
  }

}
