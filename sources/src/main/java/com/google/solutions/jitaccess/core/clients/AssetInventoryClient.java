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
import com.google.api.services.cloudasset.v1.CloudAsset;
import com.google.api.services.cloudasset.v1.model.PolicyInfo;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;

import io.opentelemetry.instrumentation.annotations.WithSpan;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Adapter for the Asset Inventory API.
 */
public class AssetInventoryClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  private final GoogleCredentials credentials;
  protected final HttpTransport.Options httpOptions;

  public AssetInventoryClient(
    GoogleCredentials credentials,
    HttpTransport.Options httpOptions
  ) {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.credentials = credentials;
    this.httpOptions = httpOptions;
  }

  protected CloudAsset createClient() throws IOException {
    try {
      return new CloudAsset.Builder(
        HttpTransport.newTransport(),
        new GsonFactory(),
        HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a CloudAsset client failed", e);
    }
  }

  /**
   * Get effective set of IAM policies for a project.
   */
  @WithSpan
  public List<PolicyInfo> getEffectiveIamPolicies(
    String scope,
    ProjectId projectId
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(scope, "scope");
    Preconditions.checkNotNull(projectId, "projectId");

    try
    {
      var results = createClient()
        .effectiveIamPolicies()
        .batchGet(scope)
        .setNames(List.of(projectId.getFullResourceName()))
        .execute()
        .getPolicyResults();

      return results.isEmpty()
        ? List.of()
        : results.get(0).getPolicies();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Denied access to scope '%s'", scope), e);
        case 404:
          throw new ResourceNotFoundException(
            String.format("The project '%s' does not exist", projectId), e);
        case 429:
          throw new QuotaExceededException(
            "Exceeded quota for BatchGetEffectiveIamPolicies API requests. Consider increasing the request " +
              "quota in the application project.",
            e);
        default:
          throw (GoogleJsonResponseException) e.fillInStackTrace();
      }
    }
  }
}
