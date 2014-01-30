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
package org.sosy_lab.cpachecker.appengine.server.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.restlet.data.MediaType;
import org.restlet.ext.wadl.WadlServerResource;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.sosy_lab.cpachecker.appengine.entity.DefaultOptions;
import org.sosy_lab.cpachecker.appengine.server.common.JobRunnerResource;
import org.sosy_lab.cpachecker.appengine.server.common.SettingsResource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.CharMatcher;


public class SettingsServerResource extends WadlServerResource implements SettingsResource {
  @Override
  public Representation getSettingsAsJson() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    Map<String, String> settings = new HashMap<>();
    String timeLimit = CharMatcher.DIGIT.retainFrom(DefaultOptions.getDefault("limits.time.wall"));
    settings.put("timeLimit", timeLimit);
    settings.put("retries", String.valueOf(JobRunnerResource.MAX_RETRIES));
    settings.put("errorFileName", JobRunnerResource.ERROR_FILE_NAME);
    settings.put("statisticsFileName", DefaultOptions.getImmutableOptions().get("statistics.file"));

    try {
      return new StringRepresentation(mapper.writeValueAsString(settings), MediaType.APPLICATION_JSON);
    } catch (JsonProcessingException e) {
      throw new IOException(e);
    }
  }
}
