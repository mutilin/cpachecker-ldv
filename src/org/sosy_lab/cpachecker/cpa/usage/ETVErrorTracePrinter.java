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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.lock.AbstractLockState;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.refinement.PathRestorator;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;

@Options(prefix = "cpa.usage")
public class ETVErrorTracePrinter extends ErrorTracePrinter {

  @Option(name = "output", description = "path to write results", secure = true)
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  @Option(
    description = "use single file for output or dump every error trace to its own file",
    secure = true
  )
  private boolean singleFileOutput = false;

  private Writer globalWriter;

  public ETVErrorTracePrinter(
      Configuration pC,
      PathRestorator pT,
      CFA pCfa,
      LogManager pL,
      LockTransferRelation t)
      throws InvalidConfigurationException {
    super(pC, pT, pCfa, pL, t);
  }

  @Override
  protected void init() {
    try {
      globalWriter =
          Files.newBufferedWriter(
              Paths.get(outputStatFileName.toString()), Charset.defaultCharset());
      logger.log(Level.FINE, "Print statistics about unsafe cases");
      printCountStatistics(globalWriter, container.getUnsafeIterator());
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Exception during init actions: " + e.getMessage());
    }
  }

  @Override
  protected void finish() {
    try {
      globalWriter.close();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Exception during finish actions: " + e.getMessage());
    }
  }

  @Override
  protected void printUnsafe(
      SingleIdentifier id, Pair<UsageInfo, UsageInfo> pPair, boolean refined) {
    File name = new File("output/ErrorPath." + createUniqueName(id) + ".txt");
    Writer writer;
    try {
      if (singleFileOutput) {
        writer = globalWriter;
      } else {
        writer = Files.newBufferedWriter(name.toPath(), Charset.defaultCharset());
      }

      if (id instanceof StructureFieldIdentifier) {
        writer.append("###\n");
      } else if (id instanceof GlobalVariableIdentifier) {
        writer.append("#\n");
      } else if (id instanceof LocalVariableIdentifier) {
        writer.append("##" + ((LocalVariableIdentifier) id).getFunction() + "\n");
      } else {
        logger.log(Level.WARNING, "What is it? " + id.toString());
      }
      writer.append(id.getDereference() + "\n");
      writer.append(id.getType().toASTString(id.getName()) + "\n");
      if (refined) {
        writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
      }
      // writer.append("Line 0:     N0 -{/*Number of usage points:" +
      // uinfo.getNumberOfTopUsagePoints() + "*/}-> N0" + "\n");
      // writer.append("Line 0:     N0 -{/*Number of usages      :" + uinfo.size() + "*/}-> N0" +
      // "\n");
      writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");

      createVisualization(id, pPair.getFirst(), writer);
      createVisualization(id, pPair.getSecond(), writer);
      if (!singleFileOutput) {
        writer.close();
      }
      printedUnsafes.inc();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Exception while printing unsafe " + id + ": " + e.getMessage());
    }
  }

  private void createVisualization(
      final SingleIdentifier id, final UsageInfo usage, final Writer writer) throws IOException {
    AbstractLockState locks = usage.getLockState();

    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    if (locks != null) {
      writer.append("Line 0:     N0 -{/*" + locks + "*/}-> N0\n");
    }
    if (usage.isLooped()) {
      writer.append("Line 0:     N0 -{/*Failure in refinement*/}-> N0\n");
    }
    int callstackDepth = 1;
    /*
     * We must use iterator to be sure, when is the end of the list.
     * I tried to check the edge, it is the last, but it can be repeated during the sequence
     */
    Iterator<CFAEdge> iterator = getPathIterator(usage);
    while (iterator.hasNext()) {
      CFAEdge edge = iterator.next();
      if (edge == null || edge instanceof CDeclarationEdge) {
        continue;
      }
      if (edge instanceof CFunctionCallEdge && iterator.hasNext()) {
        callstackDepth++;
      } else if (edge instanceof CFunctionReturnEdge) {
        callstackDepth--;
      } else if (edge instanceof CReturnStatementEdge && !iterator.hasNext()) {
        assert callstackDepth > 0;
        callstackDepth--;
      } else if (edge instanceof BlankEdge
          && edge.getDescription().contains("return")
          && !iterator.hasNext()) {
        // Evil hack, but this is how etv works
        assert callstackDepth > 0;
        callstackDepth--;
      }
      String caption = getNoteFor(edge);
      if (!caption.isEmpty() && !(edge instanceof CFunctionReturnEdge)) {
        writer.write("Line 0:     N0 -{/*" + caption + "*/}-> N0\n");
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      } else if (edge.getSuccessor() == usage.getCFANode()
          && edge.toString().contains(id.getName())) {
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      }
      writer.write(edge + "\n");
    }
    for (int i = 0; i < callstackDepth; i++) {
      writer.append("Line 0:     N0 -{return;}-> N0\n");
    }
    writer.write("\n");
  }

  private void printCountStatistics(
      final Writer writer, final Iterator<SingleIdentifier> idIterator) throws IOException {
    int global = 0, local = 0, fields = 0;
    int globalPointer = 0, localPointer = 0, fieldPointer = 0;
    SingleIdentifier id;

    while (idIterator.hasNext()) {
      id = idIterator.next();
      if (id instanceof GlobalVariableIdentifier) {
        if (id.getDereference() == 0) {
          global++;
        } else {
          globalPointer++;
        }
      } else if (id instanceof LocalVariableIdentifier) {
        if (id.getDereference() == 0) {
          local++;
        } else {
          localPointer++;
        }
      } else if (id instanceof StructureFieldIdentifier) {
        if (id.getDereference() == 0) {
          fields++;
        } else {
          fieldPointer++;
        }
      }
    }
    writer.append(global + "\n");
    writer.append(globalPointer + "\n");
    writer.append(local + "\n");
    writer.append(localPointer + "\n");
    // writer.println("--Structures:           " + structures);
    writer.append(fields + "\n");
    writer.append(fieldPointer + "\n");
    writer.append(global + globalPointer + local + localPointer + fields + fieldPointer + "\n");
  }
}
