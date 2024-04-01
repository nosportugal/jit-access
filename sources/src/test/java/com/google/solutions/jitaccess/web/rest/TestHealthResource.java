//
// Copyright 2024 Google LLC
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

package com.google.solutions.jitaccess.web.rest;

import com.google.solutions.jitaccess.core.auth.UserId;
import com.google.solutions.jitaccess.core.clients.DiagnosticsResult;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RestDispatcher;
import com.google.solutions.jitaccess.web.MockitoUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

public class TestHealthResource {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");

  //---------------------------------------------------------------------------
  // /health/alive
  //---------------------------------------------------------------------------

  @Test
  public void checkLivenessReturns200() throws Exception {
    var response = new RestDispatcher<>(new HealthResource(), SAMPLE_USER)
      .get("/health/alive", HealthResource.ResponseEntity.class);

    assertEquals(200, response.getStatus());
    assertTrue(response.getBody().healthy());
  }

  //---------------------------------------------------------------------------
  // /health/ready
  //---------------------------------------------------------------------------

  @Test
  public void whenChecksSucceed_thenCheckReadinessReturns200() throws Exception {
    var resource = new HealthResource();
    resource.logAdapter = new LogAdapter();
    resource.executor = new Executor() {
      @Override
      public void execute(@NotNull Runnable command) {
        command.run();
      }
    };

    resource.diagnosables = MockitoUtils.toCdiInstance(
      () -> List.of(new DiagnosticsResult("Sample-1")));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/health/ready", HealthResource.ResponseEntity.class);

    assertEquals(200, response.getStatus());
    assertTrue(response.getBody().healthy());
    assertEquals(Map.of("Sample-1", true), response.getBody().details());
  }

  @Test
  public void whenAnyCheckFails_thenCheckReadinessReturns503() throws Exception {
    var resource = new HealthResource();
    resource.logAdapter = new LogAdapter();
    resource.executor = new Executor() {
      @Override
      public void execute(@NotNull Runnable command) {
        command.run();
      }
    };

    resource.diagnosables = MockitoUtils.toCdiInstance(
      () -> List.of(
        new DiagnosticsResult("Sample-1"),
        new DiagnosticsResult("Sample-2", false, "error")));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/health/ready", HealthResource.ResponseEntity.class);

    assertEquals(503, response.getStatus());
    assertFalse(response.getBody().healthy());
    assertEquals(
      Map.of("Sample-1", true, "Sample-2", false),
      response.getBody().details());
  }
}
