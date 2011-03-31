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
package org.sosy_lab.cpachecker.cfa.parser.eclipse;

import java.io.IOException;
import java.util.Map;

import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.dom.parser.c.ANSICParserExtensionConfiguration;
import org.eclipse.cdt.core.dom.parser.c.GCCParserExtensionConfiguration;
import org.eclipse.cdt.core.dom.parser.c.ICParserExtensionConfiguration;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.CodeReader;
import org.eclipse.cdt.core.parser.ICodeReaderCache;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.ParserFactory;
import org.eclipse.core.runtime.CoreException;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Timer;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CParser;
import org.sosy_lab.cpachecker.exceptions.ParserException;

public class EclipseCParser implements CParser {
  
  private final LogManager logger;
  
  private final ILanguage language;
  
  private final IParserLogService parserLog = ParserFactory.createDefaultLogService();

  private final Timer parseTimer = new Timer();
  private final Timer cfaTimer = new Timer();
  
  public EclipseCParser(LogManager pLogger, Dialect dialect) {
    logger = pLogger;
    
    switch (dialect) {
    case C99:
      language = new CLanguage(new ANSICParserExtensionConfiguration());
      break;
    case GNUC:
      language = new CLanguage(new GCCParserExtensionConfiguration());
      break;
    default:
      throw new IllegalArgumentException("Unknown C dialect");
    }
  }
  
  @Override
  public CFA parseFile(String pFilename) throws ParserException, IOException {
    
    return buildCFA(parse(new CodeReader(pFilename)));
  }

  @Override
  public CFA parseString(String pCode) throws ParserException {

    return buildCFA(parse(new CodeReader(pCode.toCharArray())));
  }

  @Override
  public org.sosy_lab.cpachecker.cfa.ast.IASTNode parseSingleStatement(String pCode) throws ParserException {
    
    // parse
    IASTTranslationUnit ast = parse(new CodeReader(pCode.toCharArray()));
    
    // strip wrapping function header
    IASTDeclaration[] declarations = ast.getDeclarations();
    if (   declarations == null
        || declarations.length != 1
        || !(declarations[0] instanceof IASTFunctionDefinition)) {
      throw new ParserException("Not a single function: " + ast.getRawSignature());
    }

    IASTFunctionDefinition func = (IASTFunctionDefinition)declarations[0];
    org.eclipse.cdt.core.dom.ast.IASTStatement body = func.getBody();
    if (!(body instanceof IASTCompoundStatement)) {
      throw new ParserException("Function has an unexpected " + body.getClass().getSimpleName() + " as body: " + func.getRawSignature());
    }

    org.eclipse.cdt.core.dom.ast.IASTStatement[] statements = ((IASTCompoundStatement)body).getStatements();
    if (!(statements.length == 2 && statements[1] == null || statements.length == 1)) {
      throw new ParserException("Not exactly one statement in function body: " + body);
    }

    return new ASTConverter(new Scope()).convert(statements[0]);
  }

  private IASTTranslationUnit parse(CodeReader codeReader) throws ParserException {
    parseTimer.start();
    try {
      return language.getASTTranslationUnit(codeReader,
                                            StubScannerInfo.instance,
                                            StubCodeReaderFactory.instance,
                                            null,
                                            parserLog);
    } catch (CFAGenerationRuntimeException e) {
      // thrown by StubCodeReaderFactory
      throw new ParserException(e);
    } catch (CoreException e) {
      throw new ParserException(e);
    } finally {
      parseTimer.stop();
    }
  }
  
  private CFA buildCFA(IASTTranslationUnit ast) throws ParserException {
    cfaTimer.start();
    try {
      CFABuilder builder = new CFABuilder(logger);
      try {
        ast.accept(builder);
      } catch (CFAGenerationRuntimeException e) {
        throw new ParserException(e);
      }
    
      return new CFA(builder.getCFAs(), builder.getCFANodes(), builder.getGlobalDeclarations());
    } finally {
      cfaTimer.stop();
    }
  }

  
  @Override
  public Timer getParseTime() {
    return parseTimer;
  }

  @Override
  public Timer getCFAConstructionTime() {
    return cfaTimer;
  }

  
  /**
   * Private class extending the Eclipse CDT class that is the starting point
   * for using the parser.
   * Supports choise of parser dialect.
   */
  private static class CLanguage extends GCCLanguage {

    private final ICParserExtensionConfiguration parserConfig;

    public CLanguage(ICParserExtensionConfiguration parserConfig) {
      this.parserConfig = parserConfig;
    }

    @Override
    protected ICParserExtensionConfiguration getParserExtensionConfiguration() {
      return parserConfig;
    }
  }

  /**
   * Private class that creates CodeReaders for files. Caching is not supported.
   * TODO: Errors are ignored currently.
   */
  private static class StubCodeReaderFactory implements ICodeReaderFactory {

    private static ICodeReaderFactory instance = new StubCodeReaderFactory();

    @Override
    public int getUniqueIdentifier() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CodeReader createCodeReaderForTranslationUnit(String path) {
      try {
        return new CodeReader(path);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override
    public ICodeReaderCache getCodeReaderCache() {
      throw new UnsupportedOperationException();
    }
    @Override
    public CodeReader createCodeReaderForInclusion(String arg0) {
      throw new CFAGenerationRuntimeException("#include statements are not allowed in the source code.");
    }
  }

  /**
   * Private class that tells the Eclipse CDT scanner that no macros and include
   * paths have been defined externally.
   */
  private static class StubScannerInfo implements IScannerInfo {

    private static IScannerInfo instance = new StubScannerInfo();

    @Override
    public Map<String, String> getDefinedSymbols() {
      // the externally defined pre-processor macros
      return null;
    }

    @Override
    public String[] getIncludePaths() {
      return null;
    }
  }
}
