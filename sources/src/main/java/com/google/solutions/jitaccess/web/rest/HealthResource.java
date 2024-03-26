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

import com.google.solutions.jitaccess.core.ThrowingCompletableFuture;
import com.google.solutions.jitaccess.core.clients.Diagnosable;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.LogEvents;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * REST API controller for health checks.
 *
 * This controller allows anonymous requests.
 */
@Dependent
@Path("/health")
public class HealthResource {
  @Inject
  Executor executor;

  @Inject
  Instance<Diagnosable> diagnosables;

  @Inject
  LogAdapter logAdapter;

  /**
   * Check if the application is alive.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("alive")
  public @NotNull ResponseEntity checkLiveness() {
    //
    // The fact that this class received a request is sufficient
    // indication that the application initialized successfully
    // and that Quarkus is working.
    //
    // Restarting the application would serve no purpose at this
    // point.
    //
    return new ResponseEntity(true, Map.of());
  }

  /**
   * Check if the application is ready to receive requests.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("ready")
  public @NotNull Response checkReadiness() throws ExecutionException, InterruptedException {
    //
    // Diagnose all services that support self-diagnosis.
    //
    var diagnosticsFutures = this.diagnosables
      .stream()
      .map(d -> ThrowingCompletableFuture.submit(() -> d.diagnose(), this.executor))
      .toList();

    //
    // Consolidate results. The response only contains a summary,
    // any errors only go to the log.
    //
    var results = diagnosticsFutures
      .stream()
      .flatMap(f -> {
        try {
          return f.get().stream();
        }
        catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      })
      .toList();

    for (var result : results) {
      if (!result.successful()) {
        this.logAdapter
          .newWarningEntry(
            LogEvents.API_HEALTH,
            result.toString())
          .write();
      }
    }

    var response =  new ResponseEntity(
      results
        .stream()
        .allMatch(r -> r.successful()), // AND-combine results.
      results
        .stream()
        .collect(Collectors.toMap(r -> r.name(), r -> r.successful())));

    if (response.healthy) {
      //
      // Return a 200/OK.
      //
      return Response.ok(response).build();
    }
    else {
      //
      // Return a 503/Service unavailable.
      //
      return Response
        .status(Response.Status.SERVICE_UNAVAILABLE)
        .entity(response)
        .build();
    }
  }

  /**
   * @param healthy overall status
   * @param details status of individual services
   */
  public record ResponseEntity(
    boolean healthy,
    Map<String, Boolean> details
  ) {}
}
