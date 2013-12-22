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
package org.sosy_lab.cpachecker.appengine.entity;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.Serialize;

@Entity
public class Job {

  public enum Status {
    PENDING, RUNNING, ABORTED, TIMEOUT
  }

  @Id private Long id;
  private Date creationDate;
  private Date executionDate;
  private Date terminationDate;
  private Status status;
  @Serialize private Map<String, String> options; // serialize to avoid problems with '.' in the keys
  private String specification;
  private String configuration;
  private Ref<JobFile> program;

  @Ignore
  private Map<String, String> defaultOptions;

  public Job() {
    init();
  }

  public Job(Long id) {
    init();
    this.id = id;
  }

  private void init() {
    defaultOptions = new HashMap<>();
    defaultOptions.put("output.disable", "true");
    defaultOptions.put("cpa.predicate.solver", "smtinterpol");
    defaultOptions.put("statistics.export", "false");
    defaultOptions.put("statistics.memory", "false");
    defaultOptions.put("limits.time.cpu", "-1");
    defaultOptions.put("limits.time.wall", "-1");

    status = Status.PENDING;
    creationDate = new Date();
  }

  public String getKeyString() {
    return Key.create(Job.class, id).getString();
  }

  public Map<String, String> getDefaultOptions() {
    return defaultOptions;
  }


  public Date getExecutionDate() {
    return executionDate;
  }


  public void setExecutionDate(Date pExecutionDate) {
    executionDate = pExecutionDate;
  }


  public Date getTerminationDate() {
    return terminationDate;
  }


  public void setTerminationDate(Date pTerminationDate) {
    terminationDate = pTerminationDate;
  }


  public Status getStatus() {
    return status;
  }


  public void setStatus(Status pStatus) {
    status = pStatus;
  }


  public Map<String, String> getOptions() {
    return options;
  }


  public void setOptions(Map<String, String> pOptions) {
    options = pOptions;
  }


  public String getSpecification() {
    return specification;
  }


  public void setSpecification(String pSpecification) {
    specification = pSpecification;
  }



  public String getConfiguration() {
    return configuration;
  }


  public void setConfiguration(String pConfiguration) {
    configuration = pConfiguration;
  }

  public Long getId() {
    return id;
  }


  public Date getCreationDate() {
    return creationDate;
  }


  public JobFile getProgram() {
    return program.get();
  }


  public void setProgram(JobFile pProgram) {
    program = Ref.create(pProgram);
  }
}
