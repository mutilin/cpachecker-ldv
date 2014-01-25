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
package org.sosy_lab.cpachecker.appengine.server.resource;

import java.util.Collections;
import java.util.List;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.wadl.WadlServerResource;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.sosy_lab.cpachecker.appengine.common.FreemarkerUtil;
import org.sosy_lab.cpachecker.appengine.dao.JobDAO;
import org.sosy_lab.cpachecker.appengine.dao.JobStatisticDAO;
import org.sosy_lab.cpachecker.appengine.entity.Job;
import org.sosy_lab.cpachecker.appengine.entity.JobFile;
import org.sosy_lab.cpachecker.appengine.entity.JobStatistic;
import org.sosy_lab.cpachecker.appengine.json.JobFileMixinAnnotations;
import org.sosy_lab.cpachecker.appengine.json.JobMixinAnnotations;
import org.sosy_lab.cpachecker.appengine.json.JobStatisticMixinAnnotations;
import org.sosy_lab.cpachecker.appengine.server.common.JobResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.appengine.api.log.LogQuery;
import com.google.appengine.api.log.LogServiceFactory;
import com.google.appengine.api.log.RequestLogs;


public class JobServerResource extends WadlServerResource implements JobResource {

  private Job job;

  @Override
  protected void doInit() throws ResourceException {
    super.doInit();
    job = JobDAO.load(getAttribute("jobKey"));

    if (job == null) {
      getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
      getResponse().commit();
    } else {
      if (job.getRequestID() != null && job.getStatistic() == null) {
        List<String> reqIDs = Collections.singletonList(job.getRequestID());
        LogQuery query = LogQuery.Builder.withRequestIds(reqIDs);
        for (RequestLogs record : LogServiceFactory.getLogService().fetch(query)) {
          if (record.isFinished()) {

            // Update status if job is done but the status does not reflect this
            if (job.getStatus() == org.sosy_lab.cpachecker.appengine.entity.Job.Status.PENDING
                || job.getStatus() == org.sosy_lab.cpachecker.appengine.entity.Job.Status.RUNNING) {
              job.setStatus(org.sosy_lab.cpachecker.appengine.entity.Job.Status.ERROR);
              job.setStatusMessage(String.format("Running the job is done but the status did not reflect this."
                  + "Therefore the status was set to %s.", org.sosy_lab.cpachecker.appengine.entity.Job.Status.ERROR));
            }

            JobStatistic stats = new JobStatistic(job);
            stats.setCost(record.getCost());
            stats.setHost(record.getHost());
            stats.setLatency(record.getLatencyUsec());
            stats.setEndTime(record.getEndTimeUsec());
            stats.setStartTime(record.getStartTimeUsec());
            stats.setPendingTime(record.getPendingTimeUsec());
            stats.setMcycles(record.getMcycles());

            JobStatisticDAO.save(stats);
            job.setStatistic(stats);
            JobDAO.save(job);
          }
        }
      }
    }
  }

  @Override
  public Representation jobAsHtml() {
    List<JobFile> files = job.getFilesLoaded();

    return FreemarkerUtil.templateBuilder()
        .context(getContext())
        .addData("job", job)
        .addData("files", files)
        .templateName("job.ftl")
        .build();
  }

  @Override
  public Representation deleteJob(Variant variant) {
    JobDAO.delete(job);
    getResponse().setStatus(Status.SUCCESS_OK);

    // only send redirect if it is a browser call
    if (variant == null || !variant.getMediaType().equals(MediaType.APPLICATION_JSON)) {
      getResponse().redirectSeeOther("/jobs");
    }
    return getResponseEntity();
  }

  @Override
  public Representation jobAsJson() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    mapper.addMixInAnnotations(Job.class, JobMixinAnnotations.Full.class);
    mapper.addMixInAnnotations(JobStatistic.class, JobStatisticMixinAnnotations.Minimal.class);
    mapper.addMixInAnnotations(JobFile.class, JobFileMixinAnnotations.Minimal.class);

    try {
      return new StringRepresentation(mapper.writeValueAsString(job), MediaType.APPLICATION_JSON);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

}
