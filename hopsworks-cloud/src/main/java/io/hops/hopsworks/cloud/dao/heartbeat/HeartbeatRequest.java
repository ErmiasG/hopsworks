/*
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
 */
package io.hops.hopsworks.cloud.dao.heartbeat;

import io.hops.hopsworks.cloud.CloudNode;
import io.hops.hopsworks.cloud.dao.heartbeat.commands.CommandStatus;

import java.util.List;
import java.util.Map;

public class HeartbeatRequest extends BaseMessage {
  private final List<CloudNode> decommissioningNodes;
  private final List<CloudNode> decommissionedNodes;
  private final Map<Long, CommandStatus> commandsStatus;

  public HeartbeatRequest(List<CloudNode> decommissionedNodes, List<CloudNode> decommissioningNodes,
          Map<Long, CommandStatus> commandsStatus){
    this.decommissionedNodes = decommissionedNodes;
    this.decommissioningNodes = decommissioningNodes;
    this.commandsStatus = commandsStatus;
  }

  public List<CloudNode> getDecommissioningNodes() {
    return decommissioningNodes;
  }

  public List<CloudNode> getDecommissionedNodes() {
    return decommissionedNodes;
  }

  public Map<Long, CommandStatus> getCommandsStatus() {
    return commandsStatus;
  }
}
