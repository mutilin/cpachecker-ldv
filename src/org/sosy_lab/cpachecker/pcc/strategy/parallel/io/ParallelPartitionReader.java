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
package org.sosy_lab.cpachecker.pcc.strategy.parallel.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipInputStream;

import org.sosy_lab.common.Triple;
import org.sosy_lab.cpachecker.pcc.strategy.AbstractStrategy;
import org.sosy_lab.cpachecker.pcc.strategy.partitioning.PartitioningIOHelper;


public class ParallelPartitionReader implements Runnable {

  private final int partitionIndex;
  private final AtomicBoolean success;
  private final Semaphore waitRead;
  private Lock checkingReadingLock;
  private Condition partitionReady;
  private final int numPartitions;

  private final AbstractStrategy strategy;
  private final PartitioningIOHelper ioHelper;

  private static final Lock lock = new ReentrantLock();

  public ParallelPartitionReader(final int index, final AtomicBoolean pSuccess, final Semaphore pWaitRead,
      final AbstractStrategy pStrategy, final PartitioningIOHelper pIOHelper) {
    partitionIndex = index;
    success = pSuccess;
    waitRead = pWaitRead;
    strategy = pStrategy;
    ioHelper = pIOHelper;
    numPartitions = ioHelper.getNumPartitions();
    checkingReadingLock = null;
    partitionReady = null;
  }

  public ParallelPartitionReader(final int index, final AtomicBoolean pSuccess, final Semaphore pWaitRead,
      final AbstractStrategy pStrategy, final PartitioningIOHelper pIOHelper, final Condition pPartitionReady,
      final Lock pCoordination) {
    this(index, pSuccess, pWaitRead, pStrategy, pIOHelper);
    checkingReadingLock = pCoordination;
    partitionReady = pPartitionReady;
  }

  private void prepareAbortion() {
    success.set(false);
    waitRead.release(numPartitions);
    giveSignal();
  }

  @Override
  public void run() {
    Triple<InputStream, ZipInputStream, ObjectInputStream> streams = null;
    try {
      streams = strategy.openAdditionalProofStream(partitionIndex);
      ioHelper.readPartition(streams.getThird(), lock);
      giveSignal();
    } catch (IOException | ClassNotFoundException e) {
      prepareAbortion();
    } finally {
      if (streams != null) {
        try {
          streams.getThird().close();
          streams.getSecond().close();
          streams.getFirst().close();
        } catch (IOException e) {
        }
      }
    }
  }

  private void giveSignal() {
    if(partitionReady!=null){
    checkingReadingLock.lock();
    try {
      partitionReady.signalAll();
    } finally {
      checkingReadingLock.unlock();
    }
    }
  }

}
