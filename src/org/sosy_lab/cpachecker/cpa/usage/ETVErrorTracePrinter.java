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
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.lock.LockState;
import org.sosy_lab.cpachecker.cpa.lock.LockTransferRelation;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureFieldIdentifier;

@Options(prefix="cpa.usage")
public class ETVErrorTracePrinter extends ErrorTracePrinter {

  @Option(name="output", description="path to write results")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path outputStatFileName = Paths.get("unsafe_rawdata");

  private final LockTransferRelation lTransfer;
  private Writer writer;

  public ETVErrorTracePrinter(Configuration pC, BAMTransferRelation pT, LogManager pL, LockTransferRelation t) {
    super(pC, pT, pL);
    lTransfer = t;
  }

  @Override
  protected void init() {
    try {
      writer = Files.newBufferedWriter(Paths.get(outputStatFileName.toString()), Charset.defaultCharset());
      logger.log(Level.FINE, "Print statistics about unsafe cases");
      printCountStatistics(writer, container.getUnsafeIterator());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void finish() {
    try {
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void printUnsafe(SingleIdentifier id, Pair<UsageInfo, UsageInfo> pPair) {
    try {
      if (id instanceof StructureFieldIdentifier) {
        writer.append("###\n");
      } else if (id instanceof GlobalVariableIdentifier) {
        writer.append("#\n");
      } else if (id instanceof LocalVariableIdentifier) {
        writer.append("##" + ((LocalVariableIdentifier)id).getFunction() + "\n");
      } else {
        logger.log(Level.WARNING, "What is it? " + id.toString());
      }
      writer.append(id.getDereference() + "\n");
      writer.append(id.getType().toASTString(id.getName()) + "\n");
      //if (isTrueUnsafe) {
      //  writer.append("Line 0:     N0 -{/*Is true unsafe:*/}-> N0" + "\n");
      //}
      //writer.append("Line 0:     N0 -{/*Number of usage points:" + uinfo.getNumberOfTopUsagePoints() + "*/}-> N0" + "\n");
      //writer.append("Line 0:     N0 -{/*Number of usages      :" + uinfo.size() + "*/}-> N0" + "\n");
      writer.append("Line 0:     N0 -{/*Two examples:*/}-> N0" + "\n");

      createVisualization(id, pPair.getFirst(), writer);
      createVisualization(id, pPair.getSecond(), writer);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void createVisualization(final SingleIdentifier id, final UsageInfo usage, final Writer writer) throws IOException {
    LockState Locks = (LockState) usage.getState(LockState.class);

    writer.append("Line 0:     N0 -{/*_____________________*/}-> N0\n");
    if (Locks != null) {
      writer.append("Line 0:     N0 -{/*" + Locks.toString() + "*/}-> N0\n");
    }
    if (usage.isLooped()) {
      writer.append("Line 0:     N0 -{/*Failure in refinement*/}-> N0\n");
    }
    if (usage.getPath() == null && usage.getKeyState() != null) {
      createPath(usage);
    }
    int callstackDepth = 1;
    /*
     * We must use iterator to be sure, when is the end of the list.
     * I tried to check the edge, it is the last, but it can be repeated during the sequence
     */
    Iterator<CFAEdge> iterator = usage.getPath().iterator();
    while (iterator.hasNext()) {
      CFAEdge edge = iterator.next();
      if (edge instanceof CFunctionCallEdge && iterator.hasNext()) {
        callstackDepth++;
      } else if (edge instanceof CFunctionReturnEdge) {
        callstackDepth--;
      } else if (edge instanceof CReturnStatementEdge && !iterator.hasNext()) {
        assert callstackDepth > 0;
        callstackDepth--;
      } else if (edge instanceof BlankEdge && edge.getDescription().contains("return") && !iterator.hasNext()) {
        //Evil hack, but this is how etv works
        assert callstackDepth > 0;
        callstackDepth--;
      }
      String caption = shouldBeHighlighted(edge);
      if (caption != null && !(edge instanceof CFunctionReturnEdge)) {
        writer.write("Line 0:     N0 -{/*" + caption + "*/}-> N0\n");
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      } else if (edge.getLineNumber() == usage.getLine().getLine() && edge.toString().contains(id.getName())) {
        writer.write("Line 0:     N0 -{highlight}-> N0\n");
      }
      writer.write(edge.toString() + "\n");
    }
    for (int i = 0; i < callstackDepth; i++) {
      writer.append("Line 0:     N0 -{return;}-> N0\n");
    }
    writer.write("\n");
  }

  private void printCountStatistics(final Writer writer, final Iterator<SingleIdentifier> idIterator) throws IOException {
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
      }
      else if (id instanceof LocalVariableIdentifier) {
        if (id.getDereference() == 0) {
          local++;
        } else {
          localPointer++;
        }
      }
      else if (id instanceof StructureFieldIdentifier) {
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
    //writer.println("--Structures:           " + structures);
    writer.append(fields + "\n");
    writer.append(fieldPointer + "\n");
    writer.append(global + globalPointer + local + localPointer + fields + fieldPointer + "\n");
  }

  private String shouldBeHighlighted(CFAEdge pEdge) {
    if (lTransfer != null) {
      return lTransfer.doesChangeTheState(pEdge);
    } else {
      return null;
    }
  }
}
