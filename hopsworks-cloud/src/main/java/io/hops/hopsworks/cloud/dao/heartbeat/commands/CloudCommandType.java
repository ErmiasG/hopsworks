/*
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
 */
package io.hops.hopsworks.cloud.dao.heartbeat.commands;

import com.google.common.base.Strings;

import java.util.Objects;

public class CloudCommandType {
  public static final CloudCommandType REMOVE_NODES = CloudCommandType.of("REMOVE_NODES");

  private final String type;

  public static CloudCommandType of(String type) {
    if (Strings.isNullOrEmpty(type)) {
      throw new IllegalArgumentException("Cloud command type cannot be null or empty");
    }
    return new CloudCommandType(type);
  }

  private CloudCommandType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof CloudCommandType) {
      return type.equalsIgnoreCase(((CloudCommandType) o).type);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return type;
  }
}
