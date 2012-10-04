/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usageStatistics;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;


public class CodeCoveringSimple implements CodeCovering{

  private Set<String> UsedFunctions;
  private Set<Integer> ExceptionLines;
  CFA cfa;
  String filename;

  CodeCoveringSimple(CFA pCfa, String fname) {
    UsedFunctions = new HashSet<String>();
    ExceptionLines = new HashSet<Integer>();
    cfa = pCfa;
    filename = fname;
  }

  @Override
  public void addFunctionUsage(String pFunctionName) {
    UsedFunctions.add(pFunctionName);
  }

  @Override
  public void generate() {
    boolean usedFunction = false, globalDeclaration = true;
    int lineCounter = 0;
    //final String folder = "/home/alpha/git/cpachecker/test/";
    //final String filename = "cil.out";
    //final String inputFile = filename + ".i";
    final String outputFile = filename + ".info";
    int FNH = 0;
    Set<Integer> lineUsage = new HashSet<Integer>();
    Map<Integer, String> functionHeads = new HashMap<Integer, String>();

    UsedFunctions.add(cfa.getMainFunction().getFunctionName());

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));

      try {
        FileOutputStream file = new FileOutputStream (outputFile);
        PrintWriter writer = new PrintWriter(file);

        writer.println("TN:");
        writer.println("SF:" + filename);

		    for (FunctionEntryNode entry : cfa.getAllFunctionHeads()) {
		      functionHeads.put(entry.getLineNumber(), entry.getFunctionName());
		      writer.println("FN:" + entry.getLineNumber() + "," + entry.getFunctionName());
		    }

		    for (String name : UsedFunctions) {
		      if (cfa.getAllFunctionNames().contains(name)) {
		        writer.println("FNDA:" + "1," + name);
		        FNH++;
		      }
		    }

		    writer.println("FNF:" + cfa.getNumberOfFunctions());
		    writer.println("FNH:" + FNH);

		    for (CFANode node : cfa.getAllNodes()) {
		      for (int i = 0; i < node.getNumEnteringEdges(); i++)
		        lineUsage.add(node.getEnteringEdge(i).getLineNumber());
		      for (int i = 0; i < node.getNumLeavingEdges(); i++)
		        lineUsage.add(node.getLeavingEdge(i).getLineNumber());
		    }

		    String str;
		    while ((str = in.readLine()) != null) {
		      lineCounter++;

		      if (str.matches("#.*") || (str.matches(" *\\{ *")) || str.matches(" *\\} *") ||
		          str.matches(" *//.*") || str.matches(" */\\*.*\\*/ *") || str.matches(" *") || ExceptionLines.contains(lineCounter))
		        continue;

		      if (functionHeads.containsKey(lineCounter)) {
		        String functionName = functionHeads.get(lineCounter);
		        globalDeclaration = false;
		        if ((usedFunction = UsedFunctions.contains(functionName)) == true) {
		          writer.println("DA:" + lineCounter +",1");
		        } else {
		          writer.println("DA:" + lineCounter +",0");
  	        }

		      } else if (lineUsage.contains(lineCounter)) {
		        if (usedFunction)
		          writer.println("DA:" + lineCounter +",1");
		        else if (!globalDeclaration)
		          writer.println("DA:" + lineCounter +",0");

		      } else if (!str.contains("else") && !str.contains("goto") && !str.contains(":") && !str.equals("") && !globalDeclaration) {
		          writer.println("DA:" + lineCounter + ",0");
		      }
		    }

  	    //writer.println("LF:" + lineCounter);
  	    //writer.println("LH:" + lineUsage.size());
  	    writer.println("end_of_record");
  	    writer.close();
      } catch(FileNotFoundException e) {
      	System.err.println("Cannot open output file " + outputFile);
      } catch (IOException e) {
      	e.printStackTrace();
      } finally {
      	in.close();
      }
    } catch(FileNotFoundException e) {
    	System.out.println("Cannot open input file " + filename);
    } catch(IOException e) {
    	e.printStackTrace();
    }
  }

  @Override
  public void addException(int pLine) {
    ExceptionLines.add(pLine);
  }

}
