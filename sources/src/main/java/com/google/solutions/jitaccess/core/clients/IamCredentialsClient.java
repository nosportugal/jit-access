//
// Copyright 2022 Google LLC
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
import com.google.api.client.json.webtoken.JsonWebToken;
import com.google.api.services.iamcredentials.v1.IAMCredentials;
import com.google.api.services.iamcredentials.v1.model.SignJwtRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.auth.UserId;
import jakarta.inject.Singleton;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Adapter for IAM Credentials API
 */
@Singleton
public class IamCredentialsClient {
  public static final String OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

  private final @NotNull GoogleCredentials credentials;
  private final @NotNull HttpTransport.Options httpOptions;

  private @NotNull IAMCredentials createClient() throws IOException
  {
    try {
      return new IAMCredentials
        .Builder(
          HttpTransport.newTransport(),
          new GsonFactory(),
          HttpTransport.newAuthenticatingRequestInitializer(this.credentials, this.httpOptions))
        .setApplicationName(ApplicationVersion.USER_AGENT)
        .build();
    }
    catch (GeneralSecurityException e) {
      throw new IOException("Creating a IAMCredentials client failed", e);
    }
  }

  public IamCredentialsClient(
    @NotNull GoogleCredentials credentials,
    @NotNull HttpTransport.Options httpOptions
  )  {
    Preconditions.checkNotNull(credentials, "credentials");
    Preconditions.checkNotNull(httpOptions, "httpOptions");

    this.httpOptions = httpOptions;
    this.credentials = credentials;
  }

  /**
   * Sign a JWT using the Google-managed service account key.
   */
  @WithSpan
  public String signJwt(
    @NotNull UserId serviceAccount,
    @NotNull JsonWebToken.Payload payload
  ) throws AccessException, IOException {
    Preconditions.checkNotNull(serviceAccount, "serviceAccount");
    Preconditions.checkNotNull(payload, "payload");

    try
    {
      if (payload.getFactory() == null) {
        payload.setFactory(new GsonFactory());
      }

      var payloadJson = payload.toString();
      assert (payloadJson.startsWith("{"));

      var request = new SignJwtRequest()
        .setPayload(payloadJson);

      return createClient()
        .projects()
        .serviceAccounts()
        .signJwt(
          String.format("projects/-/serviceAccounts/%s", serviceAccount.email),
          request)
        .execute()
        .getSignedJwt();
    }
    catch (GoogleJsonResponseException e) {
      switch (e.getStatusCode()) {
        case 401:
          throw new NotAuthenticatedException("Not authenticated", e);
        case 403:
          throw new AccessDeniedException(
            String.format("Denied access to service account '%s': %s", serviceAccount.email, e.getMessage()), e);
        default:
          throw (GoogleJsonResponseException)e.fillInStackTrace();
      }
    }
  }

  /**
   * Get JWKS location for service account key set.
   */
  public static String getJwksUrl(@NotNull UserId serviceAccount) {
    return String.format(
      "https://www.googleapis.com/service_accounts/v1/metadata/jwk/%s", 
      serviceAccount.email);
  }
}
