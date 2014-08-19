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
package org.sosy_lab.cpachecker.cpa.usagestatistics;

import java.util.Collection;
import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

/**
 * Interface for data processing, that was collected in
 * UsageStatistics CPA.
 */

public interface UnsafeDetector {

  /* mode ALL - search in all usages
   * mode TRUE - search in true usages
   * mode FALSE - search in false
   */
  public static enum SearchMode {
    ALL, FALSE, TRUE;
  }
  /**
   * main function to process the data
   * @param pStat - all collected variables
   * @return collection of unsafe variables
   */
  public Collection<SingleIdentifier> getUnsafes(Map<SingleIdentifier, UsageList> pStat);

  /**
   * function to get simple description, its useful to write it in
   * statistics
   * @return description
   */

  public String getDescription();

  public Pair<UsageInfo, UsageInfo> getUnsafePair(UsageList uinfo)
		throws HandleCodeException;

  public boolean isUnsafeCase(UsageList pList, UsageInfo uInfo);
  public boolean containsUnsafe(UsageList pList, SearchMode mode);
}
