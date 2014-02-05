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

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.appengine.dao.JobDAO;
import org.sosy_lab.cpachecker.appengine.entity.Job;

import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;

@Options
public class GAETaskQueueJobRunner implements JobRunner {

  public enum InstanceType {
    /**
     * A frontend instance will be used.
     * A time limit applies.
     */
    FRONTEND,
    /**
     * A backend instance will be used.
     * This instance type imposes no time limit. Therefore limits.time.wall may
     * be set to anything.
     */
    BACKEND
  }

  public static final String QUEUE_NAME = "cpachecker";
  public static final String WORKER_PATH = "/workers/run-job";
  public static final String BACKEND_NAME = "job-worker-b1";

  @Option(name = "gae.instanceType",
      description = "The instance type to use when running CPAchecker on Google App Engine."
          + "Frontend instances have a wall time limit of 9 minutes. Backends may run for up to 24 hours."
          + "However, instance hours on backends are limited",
      values = { "FRONTEND", "BACKEND" })
  private InstanceType instanceType = InstanceType.FRONTEND;

  /**
   * Constructs a new instance.
   * The job submitted via {@link #run(Job)} will be enqueued immediately.
   */
  public GAETaskQueueJobRunner() {
    instanceType = InstanceType.FRONTEND;
  }

  /**
   * Constructs a new instance that enqueues the job sumbitted via {@link #run(Job)}
   * immediately.
   *
   * @param instancyType The instance type to use for processing the job.
   */
  public GAETaskQueueJobRunner(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  @Override
  public Job run(Job job) {
    String jobKey = job.getKey();

    Queue queue = QueueFactory.getQueue(QUEUE_NAME);
    TaskOptions builder = TaskOptions.Builder
      .withUrl(WORKER_PATH)
      .taskName("job-" + jobKey)
      .param("jobKey", jobKey);

    if (instanceType == InstanceType.BACKEND) {
      builder.header("Host", BackendServiceFactory.getBackendService().getBackendAddress(BACKEND_NAME));
    }

    TaskHandle task = queue.add(builder);

    job.setInstanceType(instanceType);
    job.setQueueName(task.getQueueName());
    job.setTaskName(task.getName());
    JobDAO.save(job);

    return job;
  }

}
