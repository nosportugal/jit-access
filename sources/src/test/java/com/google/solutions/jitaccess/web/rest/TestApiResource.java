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

package com.google.solutions.jitaccess.web.rest;

import com.google.auth.oauth2.TokenVerifier;
import com.google.solutions.jitaccess.core.AccessDeniedException;
import com.google.solutions.jitaccess.core.ProjectId;
import com.google.solutions.jitaccess.core.RoleBinding;
import com.google.solutions.jitaccess.core.UserId;
import com.google.solutions.jitaccess.core.catalog.*;
import com.google.solutions.jitaccess.core.catalog.project.MpaProjectRoleCatalog;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleActivator;
import com.google.solutions.jitaccess.core.catalog.project.ProjectRoleBinding;
import com.google.solutions.jitaccess.core.clients.ResourceManagerClient;
import com.google.solutions.jitaccess.core.notifications.NotificationService;
import com.google.solutions.jitaccess.web.LogAdapter;
import com.google.solutions.jitaccess.web.RuntimeEnvironment;
import com.google.solutions.jitaccess.web.TokenObfuscator;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TestApiResource {
  private static final UserId SAMPLE_USER = new UserId("user-1@example.com");
  private static final UserId SAMPLE_USER_2 = new UserId("user-2@example.com");

  private static final String SAMPLE_TOKEN = "eySAMPLE";
  private static final Pattern DEFAULT_JUSTIFICATION_PATTERN = Pattern.compile("pattern");
  private static final int DEFAULT_MIN_NUMBER_OF_REVIEWERS = 1;
  private static final int DEFAULT_MAX_NUMBER_OF_REVIEWERS = 10;
  private static final int DEFAULT_MAX_NUMBER_OF_ROLES = 3;
  private static final String DEFAULT_HINT = "hint";
  private static final Duration DEFAULT_ACTIVATION_DURATION = Duration.ofMinutes(5);
  private static final TokenSigner.TokenWithExpiry SAMPLE_TOKEN_WITH_EXPIRY =
    new TokenSigner.TokenWithExpiry(
      SAMPLE_TOKEN,
      Instant.now(),
      Instant.now().plusSeconds(10));

  private ApiResource resource;
  private NotificationService notificationService;

  @BeforeEach
  public void before() {
    this.resource = new ApiResource();
    this.resource.options = new ApiResource.Options(DEFAULT_MAX_NUMBER_OF_ROLES);
    this.resource.logAdapter = new LogAdapter();
    this.resource.runtimeEnvironment = Mockito.mock(RuntimeEnvironment.class);
    this.resource.mpaCatalog = Mockito.mock(MpaProjectRoleCatalog.class);
    this.resource.projectRoleActivator = Mockito.mock(ProjectRoleActivator.class);
    this.resource.justificationPolicy = Mockito.mock(JustificationPolicy.class);
    this.resource.tokenSigner = Mockito.mock(TokenSigner.class);

    this.notificationService = Mockito.mock(NotificationService.class);
    when(this.notificationService.canSendNotifications()).thenReturn(true);

    this.resource.notificationServices = Mockito.mock(Instance.class);
    when(this.resource.notificationServices.stream()).thenReturn(List.of(notificationService).stream());
    when(this.resource.notificationServices.iterator()).thenReturn(List.of(notificationService).iterator());

    when(this.resource.runtimeEnvironment.createAbsoluteUriBuilder(any(UriInfo.class)))
      .thenReturn(UriBuilder.fromUri("https://localhost/"));
  }

  // -------------------------------------------------------------------------
  // getPolicy.
  // -------------------------------------------------------------------------

  @Test
  public void whenPathNotMapped_ThenGetReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/unknown", ExceptionMappers.ErrorEntity.class);

    assertEquals(404, response.getStatus());
  }

  // -------------------------------------------------------------------------
  // getPolicy.
  // -------------------------------------------------------------------------

  @Test
  public void getPolicyReturnsJustificationHint() throws Exception {
    when(this.resource.justificationPolicy.hint())
      .thenReturn(DEFAULT_HINT);
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/api/policy", ApiResource.PolicyResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body);
    assertEquals(DEFAULT_HINT, body.justificationHint);
  }

  @Test
  public void getPolicyReturnsSignedInUser() throws Exception {
    when(this.resource.justificationPolicy.hint())
      .thenReturn(DEFAULT_HINT);
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(resource, SAMPLE_USER)
      .get("/api/policy", ApiResource.PolicyResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body);
    assertEquals(SAMPLE_USER, body.signedInUser);
  }

  // -------------------------------------------------------------------------
  // listProjects.
  // -------------------------------------------------------------------------

  @Test
  public void postProjectsReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectDiscoveryThrowsAccessDeniedException_ThenListProjectsReturnsError() throws Exception {
    when(this.resource.mpaCatalog.listProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryThrowsIOException_ThenListProjectsReturnsError() throws Exception {
    when(this.resource.mpaCatalog.listProjects(eq(SAMPLE_USER)))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenProjectDiscoveryReturnsNoProjects_ThenListProjectsReturnsEmptyList() throws Exception {
    when(this.resource.mpaCatalog.listProjects(eq(SAMPLE_USER)))
      .thenReturn(new TreeSet<>());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertEquals(0, body.projects.size());
  }

  @Test
  public void whenProjectDiscoveryReturnsProjects_ThenListProjectsReturnsList() throws Exception {
    when(this.resource.mpaCatalog.listProjects(eq(SAMPLE_USER)))
      .thenReturn(new TreeSet<>(Set.of(
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3"))));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects", ApiResource.ProjectsResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.projects);
    assertIterableEquals(
      List.of(
        "project-1",
        "project-2",
        "project-3"),
      body.projects);
  }

  // -------------------------------------------------------------------------
  // listPeers.
  // -------------------------------------------------------------------------

  @Test
  public void postPeersReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/peers", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void getPeersWithoutRoleReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());
  }

  @Test
  public void whenPeerDiscoveryThrowsAccessDeniedException_ThenListPeersReturnsError() throws Exception {
    when(this.resource.mpaCatalog.listReviewers(eq(SAMPLE_USER), any()))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenPeerDiscoveryThrowsIOException_ThenListPeersReturnsError() throws Exception {
    when(this.resource.mpaCatalog.listReviewers(eq(SAMPLE_USER), any()))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenPeerDiscoveryReturnsNoPeers_ThenListPeersReturnsEmptyList() throws Exception {
    when(this.resource.mpaCatalog
      .listReviewers(
        eq(SAMPLE_USER),
        argThat(r -> r.roleBinding().role().equals("roles/browser"))))
      .thenReturn(new TreeSet());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ApiResource.ProjectRolePeersResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.peers);
    assertEquals(0, body.peers.size());
  }

  @Test
  public void whenPeerDiscoveryReturnsProjects_ThenListPeersReturnsList() throws Exception {
    when(this.resource.mpaCatalog
      .listReviewers(
        eq(SAMPLE_USER),
        argThat(r -> r.roleBinding().role().equals("roles/browser"))))
      .thenReturn(new TreeSet(Set.of(new UserId("peer-1@example.com"), new UserId("peer-2@example.com"))));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/peers?role=roles/browser", ApiResource.ProjectRolePeersResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.peers);
    assertEquals(2, body.peers.size());
  }

  // -------------------------------------------------------------------------
  // listRoles.
  // -------------------------------------------------------------------------

  @Test
  public void postRolesReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenProjectIsEmpty_ThenListRolesReturnsError() throws Exception {
    when(this.resource.mpaCatalog.listProjects(eq(SAMPLE_USER)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/%20/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenCatalogThrowsAccessDeniedException_ThenListRolesReturnsError() throws Exception {
    when(this.resource.mpaCatalog
      .listEntitlements(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenCatalogThrowsIOException_ThenListRolesReturnsError() throws Exception {
    when(this.resource.mpaCatalog
      .listEntitlements(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenThrow(new IOException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenCatalogReturnsNoRoles_ThenListRolesReturnsEmptyList() throws Exception {
    when(this.resource.mpaCatalog
      .listEntitlements(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of()),
        Set.of(),
        Set.of("warning")));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(0, body.roles.size());
    assertNotNull(body.warnings);
    assertEquals(1, body.warnings.size());
    assertEquals("warning", body.warnings.stream().findFirst().get());
  }

  @Test
  public void whenCatalogReturnsRoles_ThenListRolesReturnsList() throws Exception {
    var role1 = new Entitlement<ProjectRoleBinding>(
      new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/browser")),
      "ent-1",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var role2 = new Entitlement<ProjectRoleBinding>(
      new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1").getFullResourceName(), "roles/janitor")),
      "ent-2",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    when(this.resource.mpaCatalog
      .listEntitlements(
        eq(SAMPLE_USER),
        eq(new ProjectId("project-1"))))
      .thenReturn(new EntitlementSet<>(
        new TreeSet<>(Set.of(role1, role2)),
        Set.of(),
        Set.of()));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles", ApiResource.ProjectRolesResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.roles);
    assertEquals(2, body.roles.size());
    assertEquals(role1.id().roleBinding(), body.roles.get(0).roleBinding);
    assertEquals(role2.id().roleBinding(), body.roles.get(1).roleBinding);
    assertTrue(body.warnings.isEmpty());
  }

  // -------------------------------------------------------------------------
  // selfApproveActivation.
  // -------------------------------------------------------------------------

  @Test
  public void getSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenBodyIsEmpty_ThenSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles/self-activate", ExceptionMappers.ErrorEntity.class);

    assertEquals(415, response.getStatus());
  }

  @Test
  public void whenProjectIsNull_ThenSelfApproveActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/%20/roles/self-activate",
      new ApiResource.SelfActivationRequest(),
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRolesEmpty_ThenSelfApproveActivationReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of();

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("role"));
  }

  @Test
  public void whenRolesExceedsLimit_ThenSelfApproveActivationReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequest();

    request.roles = Stream
      .generate(() -> "roles/role-x")
      .limit(DEFAULT_MAX_NUMBER_OF_ROLES + 1)
      .collect(Collectors.toList());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("exceeds"));
  }

  @Test
  public void whenJustificationMissing_ThenSelfApproveActivationReturnsError() throws Exception {
    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser");

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("justification"));
  }

  @Test
  public void whenActivatorThrowsException_ThenSelfApproveActivationReturnsError() throws Exception {
    when(this.resource.projectRoleActivator
      .createJitRequest(any(), any(), any(), any(), any()))
      .thenCallRealMethod();
    when(this.resource.projectRoleActivator
      .activate(any()))
      .thenThrow(new AccessDeniedException("mock"));

    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser", "roles/browser");
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ExceptionMappers.ErrorEntity.class);

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenRolesContainDuplicates_ThenSelfApproveActivationSucceedsAndIgnoresDuplicates() throws Exception {
    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");

    when(this.resource.projectRoleActivator
      .createJitRequest(any(), any(), any(), any(), any()))
      .thenCallRealMethod();
    when(this.resource.projectRoleActivator
      .activate(argThat(r -> r.entitlements().size() == 1)))
      .then(r -> new Activation<>((ActivationRequest<ProjectRoleBinding>) r.getArguments()[0]));

    var request = new ApiResource.SelfActivationRequest();
    request.roles = List.of("roles/browser", "roles/browser");
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/self-activate",
      request,
      ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(SAMPLE_USER, body.beneficiary);
    assertEquals(0, body.reviewers.size());
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals("justification", body.justification);
    assertNotNull(body.items);
    assertEquals(1, body.items.size());
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals(roleBinding, body.items.get(0).roleBinding);
    assertEquals(Entitlement.Status.ACTIVE, body.items.get(0).status);
    assertNotNull(body.items.get(0).activationId);
  }

  // -------------------------------------------------------------------------
  // requestActivation.
  // -------------------------------------------------------------------------

  @Test
  public void getRequestActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/projects/project-1/roles/request", ExceptionMappers.ErrorEntity.class);

    assertEquals(405, response.getStatus());
  }

  @Test
  public void whenBodyIsEmpty_ThenRequestActivationReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/projects/project-1/roles/request", ExceptionMappers.ErrorEntity.class);

    assertEquals(415, response.getStatus());
  }

  @Test
  public void whenProjectIsNull_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/%20/roles/request",
      new ApiResource.SelfActivationRequest(),
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("projectId"));
  }

  @Test
  public void whenRoleEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = null;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("role"));
  }

  @Test
  public void whenPeersEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of();

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("at least"));
  }

  @Test
  public void whenTooFewPeersSelected_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        2,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of("peer@example.com");

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
        "/api/projects/project-1/roles/request",
        request,
        ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("at least"));
  }

  @Test
  public void whenTooManyPeersSelected_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = Stream.generate(() -> "peer@example.com")
        .limit(DEFAULT_MAX_NUMBER_OF_REVIEWERS + 1)
        .collect(Collectors.toList());

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
        "/api/projects/project-1/roles/request",
        request,
        ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("exceed"));
  }

  @Test
  public void whenJustificationEmpty_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    var request = new ApiResource.ActivationRequest();
    request.peers = List.of(SAMPLE_USER.email);
    request.role = "roles/mock";

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("justification"));
  }

  @Test
  public void whenNotificationsNotConfigured_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    when(this.notificationService.canSendNotifications()).thenReturn(false);

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    assertEquals(500, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("feature"));
  }

  @Test
  public void whenActivatorThrowsException_ThenRequestActivationReturnsError() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    when(this.resource.projectRoleActivator
      .createMpaRequest(
        eq(SAMPLE_USER),
        any(),
        any(),
        any(),
        any(),
        any()))
      .thenThrow(new AccessDeniedException("mock"));

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ExceptionMappers.ErrorEntity.class);

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenRequestValid_ThenRequestActivationSendsNotification() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    this.resource.projectRoleActivator = new ProjectRoleActivator(
      this.resource.mpaCatalog,
      Mockito.mock(ResourceManagerClient.class),
      this.resource.justificationPolicy);

    when(this.resource.tokenSigner
      .sign(any(), any()))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var request = new ApiResource.ActivationRequest();
    request.role = "roles/mock";
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ApiResource.ActivationStatusResponse.class);
    assertEquals(200, response.getStatus());

    verify(this.notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof ApiResource.RequestActivationNotification));
  }

  @Test
  public void whenRequestValid_ThenRequestActivationReturnsSuccessResponse() throws Exception {
    when(this.resource.mpaCatalog.options())
      .thenReturn(new MpaProjectRoleCatalog.Options(
        null,
        DEFAULT_ACTIVATION_DURATION,
        DEFAULT_MIN_NUMBER_OF_REVIEWERS,
        DEFAULT_MAX_NUMBER_OF_REVIEWERS));

    this.resource.projectRoleActivator = new ProjectRoleActivator(
      this.resource.mpaCatalog,
      Mockito.mock(ResourceManagerClient.class),
      this.resource.justificationPolicy);

    when(this.resource.tokenSigner
      .sign(any(), any()))
      .thenReturn(SAMPLE_TOKEN_WITH_EXPIRY);

    var roleBinding = new RoleBinding(new ProjectId("project-1"), "roles/browser");
    var request = new ApiResource.ActivationRequest();
    request.role = roleBinding.role();
    request.peers = List.of(SAMPLE_USER_2.email, SAMPLE_USER_2.email);
    request.justification = "justification";
    request.activationTimeout = 5;

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER).post(
      "/api/projects/project-1/roles/request",
      request,
      ApiResource.ActivationStatusResponse.class);

    var body = response.getBody();
    assertEquals(SAMPLE_USER, body.beneficiary);
    assertIterableEquals(Set.of(SAMPLE_USER_2), body.reviewers);
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals("justification", body.justification);
    assertNotNull(body.items);
    assertEquals(1, body.items.size());
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals(roleBinding, body.items.get(0).roleBinding);
    assertEquals(Entitlement.Status.ACTIVATION_PENDING, body.items.get(0).status);
    assertNotNull(body.items.get(0).activationId);
  }
  
  // -------------------------------------------------------------------------
  // getActivationRequest.
  // -------------------------------------------------------------------------

  @Test
  public void whenTokenMissing_ThenGetActivationRequestReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get("/api/activation-request", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenInvalid_ThenGetActivationRequestReturnsError() throws Exception {
    when(this.resource.tokenSigner.verify(any(), eq(SAMPLE_TOKEN)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }


  @Test
  public void whenCallerNotInvolvedInRequest_ThenGetActivationRequestReturnsError() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class))
      .createMpaRequest(
        SAMPLE_USER,
        Set.of(new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var response = new RestDispatcher<>(this.resource, new UserId("other-party@example.com"))
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenValid_ThenGetActivationRequestSucceeds() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class))
      .createMpaRequest(
        SAMPLE_USER,
        Set.of(new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .get(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(request.requestingUser(), body.beneficiary);
    assertIterableEquals(Set.of(SAMPLE_USER_2), request.reviewers());
    assertTrue(body.isBeneficiary);
    assertFalse(body.isReviewer);
    assertEquals(request.justification(), body.justification);
    assertEquals(1, body.items.size());
    assertEquals(request.id().toString(), body.items.get(0).activationId);
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals("ACTIVATION_PENDING", body.items.get(0).status.name());
    assertEquals(request.startTime().getEpochSecond(), body.items.get(0).startTime);
    assertEquals(request.endTime()  .getEpochSecond(), body.items.get(0).endTime);
  }

  // -------------------------------------------------------------------------
  // approveActivationRequest.
  // -------------------------------------------------------------------------

  @Test
  public void whenTokenMissing_ThenApproveActivationRequestReturnsError() throws Exception {
    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post("/api/activation-request", ExceptionMappers.ErrorEntity.class);

    assertEquals(400, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenTokenInvalid_ThenApproveActivationRequestReturnsError() throws Exception {
    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenThrow(new TokenVerifier.VerificationException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
  }

  @Test
  public void whenActivatorThrowsException_ThenApproveActivationRequestReturnsError() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class))
      .createMpaRequest(
        SAMPLE_USER,
        Set.of(new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    when(this.resource.projectRoleActivator
      .approve(
        eq(SAMPLE_USER),
        eq(request)))
      .thenThrow(new AccessDeniedException("mock"));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ExceptionMappers.ErrorEntity.class);

    assertEquals(403, response.getStatus());

    var body = response.getBody();
    assertNotNull(body.getMessage());
    assertTrue(body.getMessage().contains("mock"));
  }

  @Test
  public void whenTokenValid_ThenApproveActivationSendsNotification() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class))
      .createMpaRequest(
        SAMPLE_USER,
        Set.of(new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    when(this.resource.projectRoleActivator
      .approve(
        eq(SAMPLE_USER),
        eq(request)))
      .thenReturn(new Activation<>(request));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    verify(this.notificationService, times(1))
      .sendNotification(argThat(n -> n instanceof ApiResource.ActivationApprovedNotification));
  }

  @Test
  public void whenTokenValid_ThenApproveActivationRequestSucceeds() throws Exception {
    var request = new ProjectRoleActivator(
      Mockito.mock(EntitlementCatalog.class),
      Mockito.mock(ResourceManagerClient.class),
      Mockito.mock(JustificationPolicy.class))
      .createMpaRequest(
        SAMPLE_USER,
        Set.of(new ProjectRoleBinding(new RoleBinding(new ProjectId("project-1"), "roles/mock"))),
        Set.of(SAMPLE_USER_2),
        "a justification",
        Instant.now(),
        Duration.ofSeconds(60));

    when(this.resource.tokenSigner
      .verify(
        any(),
        eq(SAMPLE_TOKEN)))
      .thenReturn(request);

    when(this.resource.projectRoleActivator
      .approve(
        eq(SAMPLE_USER_2),
        eq(request)))
      .thenReturn(new Activation<>(request));

    var response = new RestDispatcher<>(this.resource, SAMPLE_USER_2)
      .post(
        "/api/activation-request?activation=" + TokenObfuscator.encode(SAMPLE_TOKEN),
        ApiResource.ActivationStatusResponse.class);

    assertEquals(200, response.getStatus());

    var body = response.getBody();
    assertEquals(request.requestingUser(), body.beneficiary);
    assertIterableEquals(Set.of(SAMPLE_USER_2), request.reviewers());
    assertFalse(body.isBeneficiary);
    assertTrue(body.isReviewer);
    assertEquals(request.justification(), body.justification);
    assertEquals(1, body.items.size());
    assertEquals(request.id().toString(), body.items.get(0).activationId);
    assertEquals("project-1", body.items.get(0).projectId);
    assertEquals("ACTIVE", body.items.get(0).status.name());
    assertEquals(request.startTime().getEpochSecond(), body.items.get(0).startTime);
    assertEquals(request.endTime().getEpochSecond(), body.items.get(0).endTime);
  }
}