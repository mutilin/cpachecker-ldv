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
import java.util.Set;

import org.sosy_lab.cpachecker.exceptions.CPATransferException;


public class CodeCoveringSimple implements CodeCovering{

  private HashMap<String, Integer> FunctionUsage;
  private HashMap<String, Integer> FunctionDeclaration;
  private HashMap<Integer, Integer> LineUsage;
  private Set<Integer> ExceptionLines;

  CodeCoveringSimple() {
    FunctionUsage = new HashMap<String, Integer>();
    FunctionDeclaration = new HashMap<String, Integer>();
    LineUsage = new HashMap<Integer, Integer>();
    ExceptionLines = new HashSet<Integer>();
  }

  @Override
  public void addFunction(int pLine, String pFunctionName) throws CPATransferException {
    if (!FunctionDeclaration.containsKey(pFunctionName)) {
      FunctionDeclaration.put(pFunctionName, pLine);
    }
    else{
      //it seems to be, that the second declaration is a realization of function
      //TODO think about it
      int l = FunctionDeclaration.get(pFunctionName);
      ExceptionLines.add(l);
      FunctionDeclaration.put(pFunctionName, pLine);
      /*if (l != pLine)
        throw new CPATransferException("Double declaration of function " + pFunctionName + ": in " + l + " and in " + pLine);*/
    }
  }

  @Override
  public void addFunctionUsage(String pFunctionName) {
    if (FunctionUsage.containsKey(pFunctionName)) {
      int counter = FunctionUsage.get(pFunctionName);
      FunctionUsage.put(pFunctionName, counter++);
    }
    else {
      FunctionUsage.put(pFunctionName, 1);
    }
  }

  @Override
  public void addException(int line) {
    ExceptionLines.add(line);
  }

  @Override
  public void addLine(int pLine) {
    if (LineUsage.containsKey(pLine)) {
      int counter = LineUsage.get(pLine);
      LineUsage.put(pLine, counter++);
    }
    else {
      LineUsage.put(pLine, 1);
    }
  }

  @Override
  public void generate() {
    PrintWriter writer = null;
    FileOutputStream file = null;
    boolean usedFunction = true;
    int lineCounter = 0;
    final String folder = "/home/alpha/git/cpachecker/test/";
    final String filename = "cil.out";
    final String inputFile = filename + ".i";
    final String outputFile = filename + ".info";
    final String main = "ldv_main";
    int FNF = 0, FNH = 0;

    if (FunctionDeclaration.containsKey(main)) {
      //it can't be added automatically
      FunctionUsage.put(main, 1);
    }
    try {
      file = new FileOutputStream (folder + outputFile);
      writer = new PrintWriter(file);
    }
    catch(FileNotFoundException e)
    {
      System.out.println("Cannot open file " + folder + outputFile);
      System.exit(0);
    }

    writer.println("TN:");
    writer.println("SF:" + folder + inputFile);

    for (String name : FunctionDeclaration.keySet()) {
      writer.println("FN:" + FunctionDeclaration.get(name) + "," + name);
    }
    FNF = FunctionDeclaration.size();
    for (String name : FunctionUsage.keySet()) {
      writer.println("FNDA:" + FunctionUsage.get(name) + "," + name);
      FNH++;
      if (!FunctionDeclaration.containsKey(name))
        FNF++;
    }
    writer.println("FNF:" + FNF);
    writer.println("FNH:" + FNH);
    try {
      BufferedReader in = new BufferedReader(new FileReader(folder + inputFile));
      String str;
      while ((str = in.readLine()) != null) {
         lineCounter++;
         /*if (lineCounter == 350669) {
           lineCounter++;
           lineCounter--;
         }*/

         if (FunctionDeclaration.containsValue(lineCounter)) {
           String foundName = main;
           for (String name : FunctionDeclaration.keySet()) {
             if (FunctionDeclaration.get(name) == lineCounter){
               foundName = name;
               break;
             }
           }
           if (FunctionUsage.containsKey(foundName)) {
             writer.println("DA:" + lineCounter +"," + FunctionUsage.get(foundName));
             usedFunction = true;
           }
           else {
             writer.println("DA:" + lineCounter +",0");
             usedFunction = false;
           }
         }
         else if (LineUsage.containsKey(lineCounter)) {
           writer.println("DA:" + lineCounter +"," + LineUsage.get(lineCounter));
         }
         else {
           if ((!str.contains("#") &&
               /* #include ...*/
               (!str.equals("{")) &&
               /* { */
               !str.equals("}") &&
               !str.equals("")
               || (FunctionDeclaration.containsValue(lineCounter)))
                && !ExceptionLines.contains(lineCounter))
               /* not used functions */ {
             if (!usedFunction)
               writer.println("DA:" + lineCounter + ",0");
           }
         }
      }
      in.close();
    }
    catch (IOException e) {
      System.out.println("Cannot open file " + folder + inputFile);
      writer.close();
      System.exit(0);
    }
    writer.println("LF:" + lineCounter);
    writer.println("LH:" + LineUsage.size());
    writer.println("end_of_record");
    writer.close();
  }

}
