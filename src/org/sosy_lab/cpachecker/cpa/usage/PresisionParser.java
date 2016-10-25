/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.identifiers.GeneralGlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralLocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralStructureFieldIdentifier;


public class PresisionParser {
  private String file;
  private CFA cfa;
  private Map<Integer, CFANode> idToNodeMap = new HashMap<>();

  PresisionParser(String filename, CFA pCfa) {
    file = filename;
    cfa = pCfa;
  }

  public UsagePrecision parse(UsagePrecision precision) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(file));
      String line, local;
      CFANode node = null;
      String[] localSet;
      DataType type;
      Map<GeneralIdentifier, DataType> info = null;
      GeneralIdentifier id;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("N")) {
          //N1 - it's node identifier
          if (node != null && info != null) {
            if (!precision.add(node, info)) {
              throw new CPAException("Node " + node + " is already in presision");
            }
          }
          node = getNode(Integer.parseInt(line.substring(1)));
          info = new HashMap<>();
        } else if (line.length() > 0) {
          // it's information about local statistics
          local = line;
          localSet = local.split(";");
          if (localSet[0].equalsIgnoreCase("g")) {
            //Global variable
            id = new GeneralGlobalVariableIdentifier(localSet[1], Integer.parseInt(localSet[2]));
          } else if (localSet[0].equalsIgnoreCase("l")) {
            //Local identifier
            id = new GeneralLocalVariableIdentifier(localSet[1], Integer.parseInt(localSet[2]));
          } else if (localSet[0].equalsIgnoreCase("s") || localSet[0].equalsIgnoreCase("f")) {
            //Structure (field) identifier
            id = new GeneralStructureFieldIdentifier(localSet[1], Integer.parseInt(localSet[2]));
          } else if (localSet[0].equalsIgnoreCase("r")) {
            //Return identifier, it's not interesting for us
            continue;
          } else {
            System.err.println("Can't resolve such line: " + line);
            continue;
          }
          if (localSet[3].equalsIgnoreCase("global")) {
            type = DataType.GLOBAL;
          } else if (localSet[3].equalsIgnoreCase("local")){
            type = DataType.LOCAL;
          } else {
            System.err.println("Can't resolve such data type: " + localSet[3]);
            continue;
          }
          info.put(id, type);
        }
      }
      if (node != null && info != null) {
        if (!precision.add(node, info)) {
          throw new CPAException("Node " + node + " is already in presision");
        }
      }
    } catch(FileNotFoundException e) {
      System.err.println("Cannot open file " + file);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (CPAException e) {
      System.err.println("Can't parse presision: " + e.getMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return precision;
  }

  private CFANode getNode(int id) {
    if (idToNodeMap.isEmpty()) {
      for (CFANode n : cfa.getAllNodes()) {
        idToNodeMap.put(n.getNodeNumber(), n);
      }
    }
    return idToNodeMap.get(id);
  }
}
