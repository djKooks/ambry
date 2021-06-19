/*
 * Copyright 2021 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.quota;

import com.github.ambry.account.Container;
import java.util.Objects;


/**
 * Resource for which quota is specified for enforced.
 */
public class QuotaResource {
  public final static String DELIM = "_";
  private final String resourceId; // unique identifier for Ambry account, container or host.
  private final QuotaResourceType quotaResourceType;

  /**
   * Constructor for {@link QuotaResource}.
   * @param resourceId Id if the resource.
   * @param quotaResourceType {@link QuotaResourceType} object specifying the type of resource.
   */
  public QuotaResource(String resourceId, QuotaResourceType quotaResourceType) {
    this.resourceId = Objects.requireNonNull(resourceId);
    this.quotaResourceType = Objects.requireNonNull(quotaResourceType);
  }

  /**
   * Create {@link QuotaResource} from {@link Container}.
   * @param container {@link Container} object.
   * @return QuotaResource object.
   */
  public static QuotaResource fromContainer(Container container) {
    return new QuotaResource(
        String.join(DELIM, String.valueOf(container.getParentAccountId()), String.valueOf(container.getId())),
        QuotaResourceType.CONTAINER);
  }

  /**
   * @return the resourceId.
   */
  public String getResourceId() {
    return resourceId;
  }

  /**
   * @return the {@link QuotaResourceType}.
   */
  public QuotaResourceType getQuotaResourceType() {
    return quotaResourceType;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    QuotaResource other = (QuotaResource) obj;
    return other.getResourceId().equals(resourceId) && other.getQuotaResourceType().equals(quotaResourceType);
  }

  @Override
  public int hashCode() {
    return 89 * quotaResourceType.hashCode() + resourceId.hashCode();
  }

  /**
   * Type of Ambry resource for which quota can be applied.
   */
  public enum QuotaResourceType {
    ACCOUNT, CONTAINER, SERVICE
  }
}
