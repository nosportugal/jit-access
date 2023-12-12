//
// Copyright 2023 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.core.adapters;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.AccessException;
import com.google.solutions.jitaccess.core.ApplicationVersion;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.data.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@ApplicationScoped
public class PubSubAdapter {
  private final GoogleCredentials credentials;
  private final HttpTransport.Options httpOptions;

  public PubSubAdapter(
    GoogleCredentials credentials,
    HttpTransport.Options httpOptions)
  {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  private Pubsub createClient() throws IOException {
    try {
      return new Pubsub.Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a PubSub client failed", e);
    }
  }

  public String publish(
    Topic topic,
    PubsubMessage message
  ) throws AccessException, IOException {
    var client = createClient();

    try {
      var request = new PublishRequest();
      request.setMessages(Arrays.asList(message));

      var result = client
        .projects()
        .topics()
        .publish(topic.getFullResourceName(), request)
        .execute();
      if (result.getMessageIds().size() < 1){
        throw new IOException(
          String.format("Publishing message to topic %s returned empty response", topic));
      }

      return result.getMessageIds().get(0);
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
        case 404:
          throw new AccessDeniedException(
            String.format(
              "Pub/Sub topic '%s' cannot be accessed or does not exist: %s",
              topic,
              e.getMessage()),
            e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }
}
