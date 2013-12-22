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
package org.sosy_lab.cpachecker.appengine.common;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

import org.sosy_lab.cpachecker.appengine.entity.Job;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;


public class GAETaskQueueJobRunner implements JobRunner {

  public static final String SERVLET_URL = "/run-job";

  /**
   * Constructs a new instance.
   * The job submitted via {@link #run(Job)} will be enqueued immediately.
   */
  public GAETaskQueueJobRunner() {}

  @Override
  public Job run(Job job) {

    // TODO use named queue
    Queue queue = QueueFactory.getDefaultQueue();
    queue.add(withUrl(SERVLET_URL).param("jobKey", job.getKeyString()));

    return job;
  }

}
