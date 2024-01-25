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

package com.google.solutions.jitaccess.core.catalog.project;

import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Member;
import com.google.api.services.cloudasset.v1.model.Binding;
import com.google.common.base.Preconditions;
import com.google.solutions.jitaccess.core.*;
import com.google.solutions.jitaccess.core.catalog.ActivationType;
import com.google.solutions.jitaccess.core.catalog.Entitlement;
import com.google.solutions.jitaccess.core.catalog.EntitlementSet;
import com.google.solutions.jitaccess.core.clients.AssetInventoryClient;
import com.google.solutions.jitaccess.core.clients.DirectoryGroupsClient;
import com.google.solutions.jitaccess.core.clients.IamTemporaryAccessConditions;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import io.opentelemetry.instrumentation.annotations.WithSpan;

/**
 * Repository that uses the Asset Inventory API (without its
 * Policy Analyzer subset) to find entitlements.
 *
 * Entitlements as used by this class are role bindings that
 * are annotated with a special IAM condition (making the binding
 * "eligible").
 */
public class AssetInventoryRepository implements ProjectRoleRepository {
  public static final String GROUP_PREFIX = "group:";
  public static final String USER_PREFIX = "user:";

  private final Options options;
  private final Executor executor;
  private final DirectoryGroupsClient groupsClient;
  private final AssetInventoryClient assetInventoryClient;

  public AssetInventoryRepository(
    Executor executor,
    DirectoryGroupsClient groupsClient,
    AssetInventoryClient assetInventoryClient,
    Options options
  ) {
    Preconditions.checkNotNull(executor, "executor");
    Preconditions.checkNotNull(groupsClient, "groupsClient");
    Preconditions.checkNotNull(assetInventoryClient, "assetInventoryClient");
    Preconditions.checkNotNull(options, "options");

    this.executor = executor;
    this.groupsClient = groupsClient;
    this.assetInventoryClient = assetInventoryClient;
    this.options = options;
  }

  static <T> T awaitAndRethrow(CompletableFuture<T> future) throws AccessException, IOException {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof AccessException) {
        throw (AccessException)e.getCause().fillInStackTrace();
      }

      if (e.getCause() instanceof IOException) {
        throw (IOException)e.getCause().fillInStackTrace();
      }

      throw new IOException("Awaiting executor tasks failed", e);
    }
  }

  List<Binding> findProjectBindings(
    UserId user,
    ProjectId projectId
  ) throws AccessException, IOException {
    //
    // Lookup in parallel:
    // - the effective set of IAM policies applying to this project. This
    //   includes the IAM policy of the project itself, plus any policies
    //   applied to its ancestry (folders, organization).
    // - groups that the user is a member of.
    //
    var listMembershipsFuture = ThrowingCompletableFuture.submit(
      () -> this.groupsClient.listDirectGroupMemberships(user),
      this.executor);

    var effectivePoliciesFuture = ThrowingCompletableFuture.submit(
      () -> this.assetInventoryClient.getEffectiveIamPolicies(
        this.options.scope(),
        projectId),
      this.executor);

    var principalSetForUser = new PrincipalSet(user, awaitAndRethrow(listMembershipsFuture));
    var allBindings = awaitAndRethrow(effectivePoliciesFuture)
      .stream()

      // All bindings, across all resources in the ancestry.
      .flatMap(policy -> policy.getPolicy().getBindings().stream())

      // Only bindings that apply to the user.
      .filter(binding -> principalSetForUser.isMember(binding))

      .collect(Collectors.toList());
    return allBindings;
  }

  //---------------------------------------------------------------------------
  // ProjectRoleRepository.
  //---------------------------------------------------------------------------

  @Override
  @WithSpan
  public SortedSet<ProjectId> findProjectsWithEntitlements(
    UserId user
  ) {
    //
    // Not supported.
    //
    throw new IllegalStateException(
      "Feature is not supported. Use search to determine available projects");
  }

  @Override
  @WithSpan
  public EntitlementSet<ProjectRoleBinding> findEntitlements(
    UserId user,
    ProjectId projectId,
    EnumSet<ActivationType> typesToInclude,
    EnumSet<Entitlement.Status> statusesToInclude
  ) throws AccessException, IOException {

    List<Binding> allBindings = findProjectBindings(user, projectId);

    var allAvailable = new TreeSet<Entitlement<ProjectRoleBinding>>();
    if (statusesToInclude.contains(Entitlement.Status.AVAILABLE)) {

      //
      // Find all JIT-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> jitEligible;
      if (typesToInclude.contains(ActivationType.JIT)) {
        jitEligible = allBindings.stream()
          .filter(binding -> JitConstraints.isJitAccessConstraint(binding.getCondition()))
          .map(binding -> new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())))
          .map(roleBinding -> new Entitlement<>(
            roleBinding,
            roleBinding.roleBinding().role(),
            ActivationType.JIT,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        jitEligible = Set.of();
      }

      //
      // Find all MPA-eligible role bindings. The bindings are
      // conditional and have a special condition that serves
      // as marker.
      //
      Set<Entitlement<ProjectRoleBinding>> mpaEligible;
      if (typesToInclude.contains(ActivationType.MPA)) {
        mpaEligible = allBindings.stream()
          .filter(binding -> JitConstraints.isMultiPartyApprovalConstraint(binding.getCondition()))
          .map(binding -> new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())))
          .map(roleBinding -> new Entitlement<>(
            roleBinding,
            roleBinding.roleBinding().role(),
            ActivationType.MPA,
            Entitlement.Status.AVAILABLE))
          .collect(Collectors.toSet());
      }
      else {
        mpaEligible = Set.of();
      }

      //
      // Determine effective set of eligible roles. If a role is both JIT- and
      // MPA-eligible, only retain the JIT-eligible one.
      //
      allAvailable.addAll(jitEligible);
      allAvailable.addAll(mpaEligible
        .stream()
        .filter(r -> !jitEligible.stream().anyMatch(a -> a.id().equals(r.id())))
        .collect(Collectors.toList()));
    }

    var allActive = new HashSet<ProjectRoleBinding>();
    if (statusesToInclude.contains(Entitlement.Status.ACTIVE)) {
      allActive.addAll(allBindings.stream()
        // Only temporary access bindings.
        .filter(binding -> JitConstraints.isActivated(binding.getCondition()))

        // Only bindings that are still valid.
        .filter(binding -> IamTemporaryAccessConditions.evaluate(
          binding.getCondition().getExpression(),
          Instant.now()))

        .map(binding -> new ProjectRoleBinding(new RoleBinding(projectId, binding.getRole())))
        .collect(Collectors.toList()));
    }

    return new EntitlementSet<>(allAvailable, allActive, Set.of());
  }

  @Override
  @WithSpan
  public Set<UserId> findEntitlementHolders(
    ProjectRoleBinding roleBinding,
    ActivationType activationType
  ) throws AccessException, IOException {

    var policies = this.assetInventoryClient.getEffectiveIamPolicies(
      this.options.scope,
      roleBinding.projectId());

    var principals = policies
      .stream()

      // All bindings, across all resources in the ancestry.
      .flatMap(policy -> policy.getPolicy().getBindings().stream())

      // Only consider requested role.
      .filter(binding -> binding.getRole().equals(roleBinding.roleBinding().role()))

      // Only consider eligible bindings.
      .filter(binding -> JitConstraints.isApprovalConstraint(
        binding.getCondition(),
        activationType))

      .flatMap(binding -> binding.getMembers().stream())
      .collect(Collectors.toSet());

    var allUserMembers = principals.stream()
      .filter(p -> p.startsWith(USER_PREFIX))
      .map(p -> p.substring(USER_PREFIX.length()))
      .distinct()
      .map(email -> new UserId(email))
      .collect(Collectors.toSet());

    //
    // Resolve groups.
    //
    List<CompletableFuture<Collection<Member>>> listMembersFutures = principals.stream()
      .filter(p -> p.startsWith(GROUP_PREFIX))
      .map(p -> p.substring(GROUP_PREFIX.length()))
      .distinct()
      .map(groupEmail -> ThrowingCompletableFuture.submit(
        () -> {
          try {
            return this.groupsClient.listDirectGroupMembers(groupEmail);
          }
          catch (AccessDeniedException e) {
            //
            // Access might be denied if this is an external group,
            // but this is okay.
            //
            return List.<Member>of();
          }
        },
        this.executor))
      .collect(Collectors.toList());

    var allMembers = new HashSet<>(allUserMembers);

    for (var listMembersFuture : listMembersFutures) {
      var members = awaitAndRethrow(listMembersFuture)
        .stream()
        .map(m -> new UserId(m.getEmail()))
        .collect(Collectors.toList());
      allMembers.addAll(members);
    }

    return allMembers;
  }

  // -------------------------------------------------------------------------
  // Inner classes.
  // -------------------------------------------------------------------------

  class PrincipalSet {
    private final Set<String> principalIdentifiers;

    public PrincipalSet(
      UserId user,
      Collection<Group> groups
    ) {
      this.principalIdentifiers = groups
        .stream()
        .map(g -> String.format("group:%s", g.getEmail()))
        .collect(Collectors.toSet());
      this.principalIdentifiers.add(String.format("user:%s", user.email));
    }

    public boolean isMember(Binding binding) {
      return binding.getMembers()
        .stream()
        .anyMatch(member -> this.principalIdentifiers.contains(member));
    }
  }


  /**
   * @param scope Scope to use for queries.
   */
  public record Options(
    String scope) {

    public Options {
      Preconditions.checkNotNull(scope, "scope");
    }
  }
}
