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
package org.sosy_lab.cpachecker.util.predicates.pathformula.withUF;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.FormulaEncodingOptions;

@Options(prefix="cpa.predicate")
public class FormulaEncodingWithUFOptions extends FormulaEncodingOptions {

  @Option(description = "The function used to model successful heap object allocation. " +
                        "This is only used, when pointer analysis with UFs is enabled.")
  private String successfulAllocFunctionName = "__VERIFIER_successful_alloc";

  @Option(description = "The function used to model successful heap object allocation with zeroing. " +
                        "This is only used, when pointer analysis with UFs is enabled.")
  private String successfulZallocFunctionName = "__VERIFIER_successful_zalloc";

  @Option(description = "Setting this to true makes memoryAllocationFunctions always return a valid pointer.")
  private boolean memoryAllocationsAlwaysSucceed = false;

  @Option(description = "Enable the option to allow detecting the allocation type by type " +
                        "of the LHS of the assignment, e.g. char *arr = malloc(size) is detected as char[size]")
  private boolean revealAllocationTypeFromLhs = false;

  @Option(description = "Use deferred allocation heuristic that tracks void * variables until the actual type " +
                        "of the allocation is figured out.")
  private boolean deferUntypedAllocations = false;

  @Option(description = "Maximum size of allocations for which all structure fields are regarded always essential, " +
                        "regardless of whether they were ever really used in code.")
  private int maxPreFilledAllocationSize = 0;

  @Option(description = "The default size in bytes for memory allocations when the value cannot be determined.")
  private int defaultAllocationSize = 4;

  @Option(description = "The default length for arrays when the real length cannot be determined.")
  private int defaultArrayLength = 20;

  @Option(description = "The maximum length for arrays (elements beyond this will be ignored).")
  private int maxArrayLength = 20;

  @Option(description = "Function that is used to free allocated memory.")
  private String memoryFreeFunctionName = "free";

  @Option(description = "Ignore variables that are not relevant for reachability properties.")
  private boolean ignoreIrrelevantVariables = true;

  public FormulaEncodingWithUFOptions(Configuration config) throws InvalidConfigurationException {
    super(config);
    config.inject(this, FormulaEncodingWithUFOptions.class);
  }

  public boolean isSuccessfulAllocFunctionName(final String name) {
    return successfulAllocFunctionName.equals(name);
  }

  public boolean isSuccessfulZallocFunctionName(final String name) {
    return successfulZallocFunctionName.equals(name);
  }

  public String getSuccessfulAllocFunctionName() {
    return successfulAllocFunctionName;
  }

  public String getSuccessfulZallocFunctionName() {
    return successfulZallocFunctionName;
  }

  public boolean makeMemoryAllocationsAlwaysSucceed() {
    return memoryAllocationsAlwaysSucceed;
  }

  public boolean revealAllocationTypeFromLHS() {
    return revealAllocationTypeFromLhs;
  }

  public boolean deferUntypedAllocations() {
    return deferUntypedAllocations;
  }

  public int maxPreFilledAllocationSize() {
    return maxPreFilledAllocationSize;
  }

  public int defaultAllocationSize() {
    return defaultAllocationSize;
  }

  public int defaultArrayLength() {
    return defaultArrayLength;
  }

  public int maxArrayLength() {
    return maxArrayLength;
  }

  public boolean isMemoryFreeFunction(final String name) {
    return memoryFreeFunctionName.equals(name);
  }

  public boolean ignoreIrrelevantVariables() {
    return ignoreIrrelevantVariables;
  }
}
