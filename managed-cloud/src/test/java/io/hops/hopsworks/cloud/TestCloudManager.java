/*
 * Copyright (C) 2020, Logical Clocks AB. All rights reserved
 */
package io.hops.hopsworks.cloud;

import io.hops.hopsworks.cloud.dao.heartbeat.DecommissionStatus;
import io.hops.hopsworks.cloud.dao.heartbeat.commands.DecommissionNodeCommand;
import io.hops.hopsworks.cloud.dao.heartbeat.commands.RemoveNodesCommand;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hosts.HostsController;
import io.hops.hopsworks.common.proxies.CAProxy;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.NodeId;
import org.apache.hadoop.yarn.api.records.NodeReport;
import org.apache.hadoop.yarn.api.records.NodeState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.cli.RMAdminCLI;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;


public class TestCloudManager {
  
  @Test
  public void testSetDecomNoRequest() throws IOException, TransformerException, TransformerConfigurationException,
      ParserConfigurationException, Exception {
    CloudManager cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    Map<String, CloudNode> workers = new HashMap<>();
    workers.put("host1", new CloudNode("id", "host1", "ip1", 0, "type1", "running"));
    
    Configuration conf = new Configuration();

    //if a worker is already decomssioned it shoud be returned as decomissioned
    List<NodeReport> report = new ArrayList<>();
    NodeReport nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.DECOMMISSIONED,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    YarnClient yarnClient = Mockito.mock(YarnClient.class);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    HostsController hostsController = Mockito.mock(HostsController.class);
    CAProxy caProxy = Mockito.mock(CAProxy.class);
    DistributedFileSystemOps dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    DecommissionStatus status = cloudManager.setAndGetDecommission(new ArrayList<>(), workers,
        yarnClient, dfsOps, conf, caProxy, hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioning().isEmpty());
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    
    //if a worker is already decomssioning it shoud be returned as decomissioning
    report = new ArrayList<>();
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.DECOMMISSIONING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    
    status = cloudManager.setAndGetDecommission(new ArrayList<>(), workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().size() == 1);
    Assert.assertTrue(status.getDecommissioned().isEmpty());
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host1")));
    
    //otherwise no worker should be return as either decomissioned or decomissioning
    report = new ArrayList<>();
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    
    status = cloudManager.setAndGetDecommission(new ArrayList<>(), workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().isEmpty());
    Assert.assertTrue(status.getDecommissioned().isEmpty());
    
    //if yarn contains node that are not in the worker list they should be ignored
    report = new ArrayList<>();
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host2", 0), NodeState.DECOMMISSIONING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host3", 0), NodeState.DECOMMISSIONED,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    
    status = cloudManager.setAndGetDecommission(new ArrayList<>(), workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    Assert.assertTrue(status.getDecommissioning().isEmpty());
    Assert.assertTrue(status.getDecommissioned().isEmpty());

    //if a node has already been decommisioned it should stay decommisined what ever yarn is thingking.
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    cloudManager.decommissionedNodes.addAll(workers.values());
    report = new ArrayList<>();
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    
    status = cloudManager.setAndGetDecommission(new ArrayList<>(), workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    //we only return newly decomissioned nodes.
    Assert.assertTrue(status.getDecommissioned().isEmpty());
    Assert.assertTrue(status.getDecommissioning().isEmpty());
    
  }
  
  @Test
  public void testSetDecomStatusPriority() throws IOException, TransformerException, TransformerConfigurationException,
      ParserConfigurationException, Exception {
    CloudManager cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    Map<String, CloudNode> workers = new HashMap<>();
    workers.put("host1", new CloudNode("id", "host1", "ip1", 0, "type1", "running"));
    workers.put("host2", new CloudNode("id", "host2", "ip2", 0, "type1", "running"));
    workers.put("host3", new CloudNode("id", "host3", "ip3", 0, "type1", "running"));
    workers.put("host4", new CloudNode("id", "host4", "ip4", 0, "type1", "running"));
    
    Configuration conf = new Configuration();

    
    List<NodeReport> report = new ArrayList<>();
    NodeReport nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.SHUTDOWN,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host2", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host3", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host4", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 0);
    nodeReport.setNumApplicationMasters(1);
    report.add(nodeReport);
    YarnClient yarnClient = Mockito.mock(YarnClient.class);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    HostsController hostsController = Mockito.mock(HostsController.class);
    CAProxy caProxy = Mockito.mock(CAProxy.class);

    //we should start by decommissioning nodes that are unusable from yarn point of view
    DistributedFileSystemOps dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    Map<String, Integer> nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 1);
    RemoveNodesCommand request = new RemoveNodesCommand(1L, nodesToRemove);
    List<RemoveNodesCommand> requests = new ArrayList<>();
    requests.add(request);

    DecommissionStatus status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().isEmpty());
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    
    ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    List<String[]> expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
          "standalone=\"no\"?><hosts><host><name>host1</name><timeout>36000</timeout></host></hosts>"});
    expected.add(new String[]{"-refreshNodes","-g","-server"});
    List<String[]> called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));
    
    ArgumentCaptor<String> nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
    
    //if we decommision more nodes than there are unusable nodes we should first select unusable nodes and then
    //empty nodes
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 2);
    request = new RemoveNodesCommand(1L, nodesToRemove);
    requests = new ArrayList<>();
    requests.add(request);

    status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().size() == 1);
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host2")));
    
    commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<hosts>" +
          "<host><name>host1</name><timeout>36000</timeout></host>" +
          "<host><name>host2</name><timeout>36000</timeout></host>" +
          "</hosts>"});
    expected.add(new String[]{"-refreshNodes","-g","-server"});
    called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));
    
    nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1\nhost2", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
    
    //if we decommision more nodes than there are unusable and empty nodes we should first select unusable nodes, then
    //empty nodes and then nodes running no application masters.
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 3);
    request = new RemoveNodesCommand(1L, nodesToRemove);
    requests = new ArrayList<>();
    requests.add(request);


    status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().size() == 2);
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host2")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host3")));
    
    commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<hosts>" +
          "<host><name>host1</name><timeout>36000</timeout></host>" +
          "<host><name>host3</name><timeout>36000</timeout></host>" +
          "<host><name>host2</name><timeout>36000</timeout></host>" +
          "</hosts>"});
    expected.add(new String[]{"-refreshNodes","-g","-server"});
    called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));
    
    nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1\nhost3\nhost2", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
    
    //if we decommision more nodes than there are unusable, empty and noMaster nodes we should 
    // first select unusable nodes, then empty nodes, then nodes running no application master and finally
    // nodes running application masters
    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 4);
    request = new RemoveNodesCommand(1L, nodesToRemove);
    requests = new ArrayList<>();
    requests.add(request);

    status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());
    
    Assert.assertTrue(status.getDecommissioning().size() == 3);
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host2")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host3")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host4")));
    
    commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<hosts>" +
          "<host><name>host4</name><timeout>36000</timeout></host>" +
          "<host><name>host1</name><timeout>36000</timeout></host>" +
          "<host><name>host3</name><timeout>36000</timeout></host>" +
          "<host><name>host2</name><timeout>36000</timeout></host>" +
          "</hosts>"});
    expected.add(new String[]{"-refreshNodes","-g","-server"});
    called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));
    
    nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host4\nhost1\nhost3\nhost2", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
  }
  
  @Test
  public void testSetDecomContainersPriority() throws IOException, TransformerException,
      TransformerConfigurationException, ParserConfigurationException, Exception {
    CloudManager cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    Map<String, CloudNode> workers = new HashMap<>();
    workers.put("host1", new CloudNode("id", "host1", "ip1", 0, "type1", "running"));
    workers.put("host2", new CloudNode("id", "host2", "ip2", 0, "type1", "running"));

    Configuration conf = new Configuration();

    List<NodeReport> report = new ArrayList<>();
    NodeReport nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host2", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 2, null, 0);
    report.add(nodeReport);
    YarnClient yarnClient = Mockito.mock(YarnClient.class);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    HostsController hostsController = Mockito.mock(HostsController.class);
    CAProxy caProxy = Mockito.mock(CAProxy.class);

    //If all the nodes are running containers we should start by decommissioning the node that has the least containers
    DistributedFileSystemOps dfsOps = Mockito.mock(DistributedFileSystemOps.class);

    Map<String, Integer> nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 1);
    RemoveNodesCommand request = new RemoveNodesCommand(1L, nodesToRemove);
    List<RemoveNodesCommand> requests = new ArrayList<>();
    requests.add(request);

    DecommissionStatus status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());

    Assert.assertTrue(status.getDecommissioning().size() == 1);
    Assert.assertTrue(status.getDecommissioned().isEmpty());
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host1")));

    ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    List<String[]> expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" "
      + "standalone=\"no\"?><hosts><host><name>host1</name><timeout>36000</timeout></host></hosts>"});
    expected.add(new String[]{"-refreshNodes", "-g", "-server"});
    List<String[]> called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));

    ArgumentCaptor<String> nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
  }
  
  @Test
  public void testSetDecomAppMasterPriority() throws IOException, TransformerException,
      TransformerConfigurationException, ParserConfigurationException, Exception {
    CloudManager cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    Map<String, CloudNode> workers = new HashMap<>();
    workers.put("host1", new CloudNode("id", "host1", "ip1", 0, "type1", "running"));
    workers.put("host2", new CloudNode("id", "host2", "ip2", 0, "type1", "running"));

    Configuration conf = new Configuration();

    List<NodeReport> report = new ArrayList<>();
    NodeReport nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 1);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host2", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 2, null, 2);
    report.add(nodeReport);
    YarnClient yarnClient = Mockito.mock(YarnClient.class);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    HostsController hostsController = Mockito.mock(HostsController.class);
    CAProxy caProxy = Mockito.mock(CAProxy.class);

    //If all the nodes are running app masters we should start by decommissioning the node that has the least
    DistributedFileSystemOps dfsOps = Mockito.mock(DistributedFileSystemOps.class);

    Map<String, Integer> nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 1);
    RemoveNodesCommand request = new RemoveNodesCommand(1L, nodesToRemove);
    List<RemoveNodesCommand> requests = new ArrayList<>();
    requests.add(request);

    DecommissionStatus status = cloudManager.setAndGetDecommission(requests, workers, yarnClient, dfsOps, conf, caProxy,
        hostsController, new ArrayList<>());

    Assert.assertTrue(status.getDecommissioning().size() == 1);
    Assert.assertTrue(status.getDecommissioned().isEmpty());
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host1")));

    ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    List<String[]> expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" "
      + "standalone=\"no\"?><hosts><host><name>host1</name><timeout>36000</timeout></host></hosts>"});
    expected.add(new String[]{"-refreshNodes", "-g", "-server"});
    List<String[]> called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));

    ArgumentCaptor<String> nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
  }
  
  
  
  @Test
  public void testDecomissionRequest() throws IOException, TransformerException, TransformerConfigurationException,
      ParserConfigurationException, Exception {
    CloudManager cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    Map<String, CloudNode> workers = new HashMap<>();
    workers.put("host1", new CloudNode("id", "host1", "ip1", 0, "type1", "running"));
    workers.put("host2", new CloudNode("id", "host2", "ip2", 0, "type1", "running"));
    workers.put("host3", new CloudNode("id", "host3", "ip3", 0, "type1", "running"));
    workers.put("host4", new CloudNode("id", "host4", "ip4", 0, "type1", "running"));
    
    Configuration conf = new Configuration();

    
    List<NodeReport> report = new ArrayList<>();
    NodeReport nodeReport = NodeReport.newInstance(NodeId.newInstance("host1", 0), NodeState.SHUTDOWN,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host2", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 0, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host3", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 0);
    report.add(nodeReport);
    nodeReport = NodeReport.newInstance(NodeId.newInstance("host4", 0), NodeState.RUNNING,
        "httpAddress", "rackName", null, null, 1, null, 0);
    nodeReport.setNumApplicationMasters(1);
    report.add(nodeReport);
    YarnClient yarnClient = Mockito.mock(YarnClient.class);
    Mockito.stub(yarnClient.getNodeReports()).toReturn(report);
    HostsController hostsController = Mockito.mock(HostsController.class);
    CAProxy caProxy = Mockito.mock(CAProxy.class);

    cloudManager = Mockito.spy(CloudManager.class);
    Mockito.doNothing().when(cloudManager).execute(Mockito.any(RMAdminCLI.class), Mockito.any(String[].class));
    Mockito.doReturn(Instant.now()).when(cloudManager).getBeginningOfHeartbeat();
    
    DistributedFileSystemOps dfsOps = Mockito.mock(DistributedFileSystemOps.class);
    
    Map<String, Integer> nodesToRemove = new HashMap<>();
    nodesToRemove.put("type1", 2);
    RemoveNodesCommand request = new RemoveNodesCommand(1L, nodesToRemove);
    List<RemoveNodesCommand> removeCommands = new ArrayList<>();
    removeCommands.add(request);
    
    DecommissionNodeCommand decomCmd = new DecommissionNodeCommand(2L, "host2", "nodeId2");
    List<DecommissionNodeCommand> decomCommands = new ArrayList<>();
    decomCommands.add(decomCmd);

    //it should decommission the node from the decommission command and then proceede with the decommission selection
    //for the remaining nodes.
    DecommissionStatus status = cloudManager.setAndGetDecommission(removeCommands, workers, yarnClient, dfsOps, conf,
        caProxy, hostsController, decomCommands);
       
    Assert.assertTrue(status.getDecommissioning().size() == 2);
    Assert.assertTrue(status.getDecommissioned().size() == 1);
    Assert.assertTrue(status.getDecommissioned().contains(workers.get("host1")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host2")));
    Assert.assertTrue(status.getDecommissioning().contains(workers.get("host3")));
    
    ArgumentCaptor<String[]> commandCaptor = ArgumentCaptor.forClass(String[].class);
    Mockito.verify(cloudManager, Mockito.times(2)).execute(Mockito.any(RMAdminCLI.class), commandCaptor.capture());
    List<String[]> expected = new ArrayList<>(2);
    expected.add(new String[]{"-updateExcludeList",
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<hosts>" +
          "<host><name>host1</name><timeout>36000</timeout></host>" +
          "<host><name>host3</name><timeout>36000</timeout></host>" +
          "<host><name>host2</name><timeout>36000</timeout></host>" +
          "</hosts>"});
    expected.add(new String[]{"-refreshNodes","-g","-server"});
    List<String[]> called = commandCaptor.getAllValues();
    Assert.assertArrayEquals(expected.get(0), called.get(0));
    Assert.assertArrayEquals(expected.get(1), called.get(1));
    
    ArgumentCaptor<String> nodes = ArgumentCaptor.forClass(String.class);
    Mockito.verify(dfsOps).updateExcludeList(nodes.capture());
    Assert.assertEquals("host1\nhost3\nhost2", nodes.getValue());
    Mockito.verify(dfsOps).refreshNodes();
  }
}
