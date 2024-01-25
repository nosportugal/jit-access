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

package com.google.solutions.jitaccess.core;

import com.google.common.base.Preconditions;

/**
 * ID of a Google Cloud folder.
 */
public record FolderId(String id) implements Comparable<FolderId>, ResourceId {
  public FolderId {
    Preconditions.checkNotNull(id, "id");
    assert !id.startsWith("//");
    assert !id.contains("/");
  }

  @Override
  public String toString() {
    return this.id;
  }

  // -------------------------------------------------------------------------
  // Comparable.
  // -------------------------------------------------------------------------

  @Override
  public int compareTo(FolderId o) {
    return this.id.compareTo(o.id);
  }

  // -------------------------------------------------------------------------
  // ResourceId.
  // -------------------------------------------------------------------------

  @Override
  public String type() {
    return "folder";
  }

  @Override
  public String path() {
    return String.format("folders/%s", this.id);
  }
}
