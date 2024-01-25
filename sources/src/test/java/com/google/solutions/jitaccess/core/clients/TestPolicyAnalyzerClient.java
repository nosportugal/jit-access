//
// Copyright 2021 Google LLC
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

import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.NotAuthenticatedException;
import com.google.solutions.jitaccess.core.UserId;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolicyAnalyzerClient {
  // -------------------------------------------------------------------------
  // findAccessibleResourcesByUser.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenFindAccessibleResourcesByUserThrowsException() {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.findAccessibleResourcesByUser(
        "projects/0",
        new UserId("", "bob@example.com"),
        Optional.empty(),
        Optional.empty(),
        true));
  }

  @Test
  public void whenCallerLacksPermission_ThenFindAccessibleResourcesByUserThrowsException() {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.findAccessibleResourcesByUser(
        "projects/0",
        new UserId("", "bob@example.com"),
        Optional.empty(),
        Optional.empty(),
        true));
  }

  @Test
  public void whenRequestTimesOut_ThenFindAccessibleResourcesByUserThrowsException() {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      new HttpTransport.Options(
        Duration.of(1, ChronoUnit.MILLIS),
        Duration.of(1, ChronoUnit.MILLIS),
        Duration.of(1, ChronoUnit.MILLIS)));

    assertThrows(
      SocketTimeoutException.class,
      () -> adapter.findAccessibleResourcesByUser(
        "projects/0",
        new UserId("", "bob@example.com"),
        Optional.empty(),
        Optional.empty(),
        true));
  }

  @Test
  public void whenPermissionDoesNotExist_ThenFindAccessibleResourcesByUserReturnsEmptyResult() throws Exception {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var result = adapter.findAccessibleResourcesByUser(
      "projects/" + IntegrationTestEnvironment.PROJECT_ID,
      new UserId("", "bob@example.com"),
      Optional.of("invalid.invalid.invalid"),
      Optional.empty(),
      true);

    assertNotNull(result);
    assertNull(result.getAnalysisResults());
  }

  @Test
  public void whenResourceDoesNotExist_ThenFindAccessibleResourcesByUserReturnsEmptyResult() throws Exception {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.APPLICATION_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    var result = adapter.findAccessibleResourcesByUser(
      "projects/" + IntegrationTestEnvironment.PROJECT_ID,
      new UserId("", "bob@example.com"),
      Optional.empty(),
      Optional.of("//cloudresourcemanager.googleapis.com/projects/000-invalid"),
      true);

    assertNotNull(result);
    assertNull(result.getAnalysisResults());
  }

  // -------------------------------------------------------------------------
  // findPermissionedPrincipalsByResource.
  // -------------------------------------------------------------------------

  @Test
  public void whenUnauthenticated_ThenPermissionedPrincipalsByResourceThrowsException() {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.INVALID_CREDENTIAL,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      NotAuthenticatedException.class,
      () -> adapter.findPermissionedPrincipalsByResource(
        "projects/0",
        "//cloudresourcemanager.googleapis.com/projects/132",
        "roles/browser"));
  }

  @Test
  public void whenCallerLacksPermission_ThenFindPermissionedPrincipalsByResourceThrowsException() {
    var adapter = new PolicyAnalyzerClient(
      IntegrationTestEnvironment.NO_ACCESS_CREDENTIALS,
      HttpTransport.Options.DEFAULT);

    assertThrows(
      AccessDeniedException.class,
      () -> adapter.findPermissionedPrincipalsByResource(
        "projects/0",
        "//cloudresourcemanager.googleapis.com/projects/132",
        "roles/browser"));
  }
}
