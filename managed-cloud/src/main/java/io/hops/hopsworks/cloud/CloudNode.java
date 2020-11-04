/*
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
 */
package io.hops.hopsworks.cloud;

public class CloudNode {

  private String nodeId;
  private String host;
  private String ip;
  private Integer numGPUs;
  private String instanceType;
  private String instanceState;

  public CloudNode(String nodeId, String host, String ip, Integer numGPUs, String instanceType, String instanceState) {
    this.nodeId = nodeId;
    this.host = host;
    this.ip = ip;
    this.numGPUs = numGPUs;
    this.instanceType = instanceType;
    this.instanceState = instanceState;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public Integer getNumGPUs() {
    return numGPUs;
  }

  public void setNumGPUs(Integer numGPUs) {
    this.numGPUs = numGPUs;
  }

  public String getInstanceType() {
    return instanceType;
  }

  public void setInstanceType(String instanceType) {
    this.instanceType = instanceType;
  }

  public String getInstanceState() {
    return instanceState;
  }

  public void setInstanceState(String instanceState) {
    this.instanceState = instanceState;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other instanceof CloudNode) {
      CloudNode cother = (CloudNode) other;
      return host.equals(cother.getHost());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return host.hashCode();
  }

  @Override
  public String toString() {
    return "CloudNode{" +
            "nodeId='" + nodeId + '\'' +
            ", host='" + host + '\'' +
            ", ip='" + ip + '\'' +
            ", numGPUs=" + numGPUs +
            ", instanceType='" + instanceType + '\'' +
            ", instanceState='" + instanceState + '\'' +
            '}';
  }
}
