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
package org.sosy_lab.cpachecker.appengine.dao;

import static com.googlecode.objectify.ObjectifyService.ofy;

import java.util.List;

import org.sosy_lab.cpachecker.appengine.entity.Job;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.VoidWork;


public class JobDAO {

  public static Job load(String key) {
    Key<Job> jobKey = Key.create(key);
    return load(jobKey);
  }

  public static Job load(Key<Job> key) {
    return ofy().load().key(key).now();
  }

  public static List<Job> jobs() {
    return ofy().load().type(Job.class).list();
  }

  public static Job save(Job job) {
    ofy().save().entity(job).now();
    return job;
  }

  /**
   * Tries to delete a job and indicates if it was possible.
   * Also deletes all associated files.
   *
   * @param job The job to delete.
   */
  public static void delete(final Job job) {
    if (job != null) {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          try {
            Queue queue = QueueFactory.getQueue(job.getQueueName());
            queue.deleteTask(job.getTaskName());
          } catch (Exception _) {
            /*
             * it does not matter if the task could be deleted or not
             * since it will disappear anyway after it's been run.
             */
          }

          if (job.getFiles() != null && job.getFiles().size() > 0) {
            ofy().delete().entities(job.getFiles()).now();
          }
          ofy().delete().entities(job).now();
        }
      });
    }
  }

  public static void delete(String key) {
    Key<Job> jobKey = Key.create(key);
    delete(jobKey);
  }

  public static void delete(Key<Job> key) {
    delete(load(key));
  }

  public static Key<Job> allocateKey() {
    return ObjectifyService.factory().allocateId(Job.class);
  }

}
