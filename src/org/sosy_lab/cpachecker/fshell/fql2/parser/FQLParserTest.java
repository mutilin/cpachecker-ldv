/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.fshell.fql2.parser;

import java.io.StringReader;

import junit.framework.Assert;

import org.junit.Test;


public class FQLParserTest {

  // TODO Use Michael's test suite, too.

  @Test
  public void testFQLParserScanner001() throws Exception {
    String lInput = "COVER EDGES(@BASICBLOCKENTRY)";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(@BASICBLOCKENTRY)");
  }

  @Test
  public void testFQLParserScanner002() throws Exception {
    String lInput = "COVER EDGES(@CONDITIONEDGE)";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(@CONDITIONEDGE)");
  }

  @Test
  public void testFQLParserScanner003() throws Exception {
    String lInput = "COVER EDGES(@DECISIONEDGE)";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(@DECISIONEDGE)");
  }

  @Test
  public void testFQLParserScanner004() throws Exception {
    String lInput = "COVER EDGES(@CONDITIONGRAPH)";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(@CONDITIONGRAPH)");
  }

  @Test
  public void testFQLParserScanner005() throws Exception {
    String lInput = "COVER EDGES(ID)";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(ID)");
  }

  @Test
  public void testFQLParserScanner006() throws Exception {
    String lInput = "COVER EDGES(COMPLEMENT(@BASICBLOCKENTRY))";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(COMPLEMENT(@BASICBLOCKENTRY))");
  }

  @Test
  public void testFQLParserScanner007() throws Exception {
    String lInput = "COVER EDGES(INTERSECT(@BASICBLOCKENTRY, @CONDITIONEDGE))";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(INTERSECT(@BASICBLOCKENTRY, @CONDITIONEDGE))");
  }

  @Test
  public void testFQLParserScanner008() throws Exception {
    String lInput = "COVER EDGES(UNION(@BASICBLOCKENTRY, @CONDITIONEDGE))";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER EDGES(UNION(@BASICBLOCKENTRY, @CONDITIONEDGE))");
  }

  @Test
  public void testFQLParserScanner009() throws Exception {
    String lInput = "COVER \"EDGES(ID)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER \"EDGES(ID)*\"");
  }
  
  @Test
  public void testFQLParserScanner010() throws Exception {
    String lInput = "COVER \"EDGES(ID)*\".EDGES(@CALL(f)).\"EDGES(ID)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER (\"EDGES(ID)*\".EDGES(@CALL(f))).\"EDGES(ID)*\"");
  }

  @Test
  public void testFQLParserScanner011() throws Exception {
    String lInput = "COVER \"EDGES(ID)*\".NODES(@CALL(f)).\"EDGES(ID)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER (\"EDGES(ID)*\".NODES(@CALL(f))).\"EDGES(ID)*\"");
  }

  @Test
  public void testFQLParserScanner012() throws Exception {
    String lInput = "COVER \"EDGES(ID)*\".PATHS(@CALL(f), 2).\"EDGES(ID)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));
    
    Object lResult = lParser.parse().value;

    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER (\"EDGES(ID)*\".PATHS(@CALL(f), 2)).\"EDGES(ID)*\"");
  }

  @Test
  public void testFQLParserScanner013() throws Exception {
    String lInput = "COVER \"NODES(ID)*\".{ x > 10 }.EDGES(@CALL(f)).\"PATHS(ID, 5)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER ((\"NODES(ID)*\".{ x > 10 }).EDGES(@CALL(f))).\"PATHS(ID, 5)*\"");
  }
  
  @Test
  public void testFQLParserScanner014() throws Exception {
    String lInput = "IN @FILE('source.c') COVER \"NODES(ID)*\".{ x > 10 }.EDGES(@CALL(f)).\"PATHS(ID, 5)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER ((\"NODES(COMPOSE(ID, @FILE('source.c')))*\".{ x > 10 }).EDGES(COMPOSE(@CALL(f), @FILE('source.c')))).\"PATHS(COMPOSE(ID, @FILE('source.c')), 5)*\"");
  }
  
  @Test
  public void testFQLParserScanner015() throws Exception {
    String lInput = "IN PRED(@FILE('source.c'), {y > 100}) COVER \"NODES(ID)*\".{ x > 10 }.EDGES(@CALL(f)).\"PATHS(ID, 5)*\"";

    System.out.println(lInput);

    FQLParser lParser = new FQLParser(new StringReader(lInput));

    Object lResult = lParser.parse().value;
    
    System.out.println("RESULT: " + lResult.toString());
    
    Assert.assertEquals(lResult.toString(), "COVER ((\"NODES(COMPOSE(ID, PRED(@FILE('source.c'), { y > 100 })))*\".{ x > 10 }).EDGES(COMPOSE(@CALL(f), PRED(@FILE('source.c'), { y > 100 })))).\"PATHS(COMPOSE(ID, PRED(@FILE('source.c'), { y > 100 })), 5)*\"");
  }

}
