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

package com.google.solutions.jitaccess.core.catalog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEntitlement {

  // -------------------------------------------------------------------------
  // toString.
  // -------------------------------------------------------------------------

  @Test
  public void toStringReturnsName() {
    var ent = new Entitlement<SampleEntitlementId>(
      new SampleEntitlementId("1"),
      "Sample entitlement",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    assertEquals("Sample entitlement", ent.toString());
  }

  // -------------------------------------------------------------------------
  // compareTo.
  // -------------------------------------------------------------------------

  @Test
  public void compareToOrdersByStatusThenName() {
    var availableA = new Entitlement<SampleEntitlementId>(
      new SampleEntitlementId("A"),
      "Entitlement A",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);
    var activeA = new Entitlement<SampleEntitlementId>(
      new SampleEntitlementId("A"),
      "Entitlement A",
      ActivationType.JIT,
      Entitlement.Status.ACTIVE);
    var pendingA = new Entitlement<SampleEntitlementId>(
      new SampleEntitlementId("A"),
      "Entitlement A",
      ActivationType.JIT,
      Entitlement.Status.ACTIVATION_PENDING);

    var availableB = new Entitlement<SampleEntitlementId>(
      new SampleEntitlementId("B"),
      "Entitlement B",
      ActivationType.JIT,
      Entitlement.Status.AVAILABLE);

    var entitlements = List.of(
      availableB,
      pendingA,
      availableA,
      activeA);

    var sorted = new TreeSet<Entitlement<SampleEntitlementId>>();
    sorted.addAll(entitlements);

    Assertions.assertIterableEquals(
      List.of(
        availableA,
        availableB,
        activeA,
        pendingA),
      sorted);
  }
}
