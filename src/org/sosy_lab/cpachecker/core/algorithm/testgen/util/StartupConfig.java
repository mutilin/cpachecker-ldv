/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.testgen.util;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.testgen.TestGenAlgorithm;

/**
 * Encapsulates {@link Configuration}, {@link ShutdownNotifier} and {@link LogManager} instances
 * intended for easier passing them from {@link TestGenAlgorithm} to its various strategies.
 */
public class StartupConfig {

  /**
   * Utility Method which handles creating a new {@link StartupConfig} instance while using
   * {@link ShutdownNotifier#createWithParent(ShutdownNotifier)}
   */
  public static StartupConfig createWithParent(StartupConfig pStartupConfig) {
    return new StartupConfig(pStartupConfig.getConfig(), pStartupConfig.getLog(),
        ShutdownNotifier.createWithParent(pStartupConfig.getShutdownNotifier()));
  }

  private final Configuration config;
  private final ShutdownNotifier notifier;
  private final LogManager log;

  public StartupConfig(Configuration pLConfig, LogManager pLogManager, ShutdownNotifier pLShutdownNotifier) {
    this.config = pLConfig;
    this.notifier = pLShutdownNotifier;
    this.log = pLogManager;
  }

  public Configuration getConfig() {
    return config;
  }

  public ShutdownNotifier getShutdownNotifier() {
    return notifier;
  }

  public LogManager getLog() {
    return log;
  }
}