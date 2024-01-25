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

package com.google.solutions.jitaccess.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

public class TestProjectId {
  private final String SAMPLE_PROJECT_FULLRESOURCENAME =
    "//cloudresourcemanager.googleapis.com/projects/project-1";

  @Test
  public void toStringReturnsId() {
    assertEquals("project-1", new ProjectId("project-1").toString());
  }

  // -------------------------------------------------------------------------
  // Full resource name conversion.
  // -------------------------------------------------------------------------

  @Test
  public void getFullResourceNameReturnsFullyQualifiedName() {
    assertEquals(
      "//cloudresourcemanager.googleapis.com/projects/project-1",
      new ProjectId("project-1").getFullResourceName());
  }

  @Test
  public void fromFullResourceNameReturnsProjectId() {
    assertEquals(
      new ProjectId("project-1"),
      ProjectId.fromFullResourceName("//cloudresourcemanager.googleapis.com/projects/project-1"));
  }

  @Test
  public void whenResourceIsProject_TheIsSupportedResourceReturnsTrue() {
    assertTrue(ProjectId.isProjectFullResourceName(SAMPLE_PROJECT_FULLRESOURCENAME));
  }

  @Test
  public void whenResourceIsNotAProject_TheIsSupportedResourceReturnsTrue() {
    assertFalse(ProjectId.isProjectFullResourceName(SAMPLE_PROJECT_FULLRESOURCENAME + "/foo/bar"));
  }

  // -------------------------------------------------------------------------
  // Type.
  // -------------------------------------------------------------------------

  @Test
  public void type() {
    assertEquals("project", new ProjectId("project-1").type());
  }

  // -------------------------------------------------------------------------
  // ID.
  // -------------------------------------------------------------------------

  @Test
  public void id() {
    assertEquals("project-1", new ProjectId("project-1").id());
  }

  // -------------------------------------------------------------------------
  // Path.
  // -------------------------------------------------------------------------

  @Test
  public void path() {
    assertEquals("projects/project-1", new ProjectId("project-1").path());
  }

  // -------------------------------------------------------------------------
  // Equality.
  // -------------------------------------------------------------------------

  @Test
  public void whenObjectAreEquivalent_ThenEqualsReturnsTrue() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-1");

    assertTrue(id1.equals(id2));
    assertEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectAreSame_ThenEqualsReturnsTrue() {
    ProjectId id1 = new ProjectId("project-1");

    assertTrue(id1.equals(id1));
  }

  @Test
  public void whenObjectAreMotEquivalent_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");
    ProjectId id2 = new ProjectId("project-2");

    assertFalse(id1.equals(id2));
    assertNotEquals(id1.hashCode(), id2.hashCode());
  }

  @Test
  public void whenObjectIsNull_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(null));
  }

  @Test
  public void whenObjectIsDifferentType_ThenEqualsReturnsFalse() {
    ProjectId id1 = new ProjectId("project-1");

    assertFalse(id1.equals(""));
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Test
  public void whenInTreeSet_ThenReturnsInExpectedOrder() {
    var projects = List.of(
      new ProjectId("project-3"),
      new ProjectId("project-1"),
      new ProjectId("project-2"));

    assertIterableEquals(
      List.of(
        new ProjectId("project-1"),
        new ProjectId("project-2"),
        new ProjectId("project-3")),
      new TreeSet<>(projects));
  }
}
