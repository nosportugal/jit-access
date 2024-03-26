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

package com.google.solutions.jitaccess.core.clients;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.secretmanager.v1.SecretManager;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;

import io.opentelemetry.instrumentation.annotations.WithSpan;

/**
 * Client for the Secrets Manager API.
 */
@Singleton
public class SecretManagerClient {
  private static final String SECRET_CHARSET = "UTF-8";
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  public SecretManagerClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  private @NotNull SecretManager createClient() throws IOException {
    try {
      return new SecretManager.Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a SecretManager client failed", e);
    }
  }

  /**
   * Access a secret
   * @param secretPath resource path, in the format projects/x/secrets/y/versions/z
   */
  @WithSpan
  public @Nullable String accessSecret(
    String secretPath
  ) throws AccessException, IOException {
    try {
      var payload = createClient()
        .projects()
        .secrets()
        .versions()
        .access(secretPath)
        .execute()
        .getPayload();

      if (payload == null) {
        return null;
      }

      var payloadData = payload.decodeData();
      if (payloadData == null) {
        return null;
      }
      else {
        return new String(payloadData, SECRET_CHARSET);
      }
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Access to secret '%s' was denied", secretPath), e);
        case 404:
          throw new ResourceNotFoundException(
            String.format("The secret '%s' does not exist", secretPath), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }
}
