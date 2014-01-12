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
package org.sosy_lab.cpachecker.appengine.entity;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;


public class JobsResourceJSONModule extends SimpleModule {

  private static final long serialVersionUID = 1L;

  public JobsResourceJSONModule() {
    super();
    addSerializer(new JobSerializer());
    addDeserializer(Map.class, new SettingsDeserializer());
  }

  private class JobSerializer extends JsonSerializer<Job> {
    @Override
    public void serialize(Job job, JsonGenerator gen, SerializerProvider pArg2) throws IOException,
        JsonProcessingException {
      DateFormat utcDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
      utcDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

      gen.writeStartObject();
      gen.writeStringField("key", job.getKey());
      gen.writeStringField("status", job.getStatus().name());
      if (job.getResultOutcome() != null) {
        gen.writeStringField("outcome", job.getResultOutcome().name());
      }
      gen.writeStringField("creationDate", utcDateFormat.format(job.getCreationDate()));
      if (job.getExecutionDate() != null) {
        gen.writeStringField("executionDate", utcDateFormat.format(job.getExecutionDate()));
      }
      if (job.getTerminationDate() != null) {
        gen.writeStringField("terminationDate", utcDateFormat.format(job.getTerminationDate()));
      }
      gen.writeEndObject();
    }

    @Override
    public Class<Job> handledType() {return Job.class;}
  }

  private class SettingsDeserializer extends JsonDeserializer<Map<String, Object>> {

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext pArg1) throws IOException, JsonProcessingException {
      parser.enable(Feature.ALLOW_UNQUOTED_CONTROL_CHARS);

      Map<String, Object> settings = new HashMap<>();
      while (parser.nextToken() != null) {
        JsonToken token = parser.getCurrentToken();
        if (token == JsonToken.FIELD_NAME) {
          switch (parser.getCurrentName()) {
          case "specification":
            settings.put("specification", parser.nextTextValue());
            break;
          case "configuration":
            settings.put("configuration", parser.nextTextValue());
            break;
          case "programText":
            settings.put("programText", parser.nextTextValue());
            break;
          case "options":
            if ((token = parser.nextToken()) == JsonToken.START_OBJECT) {
              DefaultOptions options = new DefaultOptions();
              while (parser.nextValue() == JsonToken.VALUE_STRING) {
                options.setOption(parser.getCurrentName(), parser.getValueAsString());
              }
              settings.put("options", options.getUsedOptions());
            }
            break;
          }
        }
      }

      return settings;
    }

    @Override
    public Class<?> handledType() {return Map.class;}

  }

}
