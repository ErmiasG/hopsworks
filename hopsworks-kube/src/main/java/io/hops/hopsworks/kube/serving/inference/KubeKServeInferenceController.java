/*
 * Copyright (C) 2022, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.kube.serving.inference;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.hops.common.Pair;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.serving.inference.InferenceEndpoint;
import io.hops.hopsworks.common.serving.inference.InferenceHttpClient;
import io.hops.hopsworks.common.serving.inference.InferencePort;
import io.hops.hopsworks.common.serving.inference.InferenceVerb;
import io.hops.hopsworks.common.serving.inference.ServingInferenceUtils;
import io.hops.hopsworks.exceptions.ApiKeyException;
import io.hops.hopsworks.exceptions.InferenceException;
import io.hops.hopsworks.kube.common.KubeInferenceEndpoints;
import io.hops.hopsworks.kube.common.KubeKServeClientService;
import io.hops.hopsworks.kube.security.KubeApiKeyUtils;
import io.hops.hopsworks.kube.serving.utils.KubeServingUtils;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.serving.Serving;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.persistence.entity.user.security.apiKey.ApiKey;
import io.hops.hopsworks.restutils.RESTCodes;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONObject;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.logging.Level;

@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class KubeKServeInferenceController {
  
  @EJB
  private KubeKServeClientService kubeKServeClientService;
  @EJB
  private KubeInferenceEndpoints kubeInferenceEndpoints;
  @EJB
  private KubeServingUtils kubeServingUtils;
  @EJB
  private InferenceHttpClient inferenceHttpClient;
  @EJB
  private ServingInferenceUtils servingInferenceUtils;
  @EJB
  private KubeApiKeyUtils kubeApiKeyUtils;
  @EJB
  private UserFacade userFacade;
  
  /**
   * KServe inference. Sends a JSON request to the REST API of a kserve server
   *
   * @param serving the serving instance to send the request to
   * @param verb the type of inference request (predict, regress, classify)
   * @param inferenceRequestJson the JSON payload of the inference request
   * @param authHeader the Authorization header of the request
   * @return the inference result returned by the serving server
   * @throws InferenceException
   */
  public Pair<Integer, String> infer(String username, Serving serving, InferenceVerb verb, String inferenceRequestJson,
    String authHeader) throws InferenceException, ApiKeyException {
    
    if (verb == InferenceVerb.TEST) {
      // if header contains JWT token, replace with API Key
      if (authHeader.startsWith(KubeApiKeyUtils.AUTH_HEADER_BEARER_PREFIX)) {
        // get user from db
        Users user = userFacade.findByUsername(username);
        // get API key from kube secret
        Optional<ApiKey> apiKey = kubeApiKeyUtils.getServingApiKey(user);
        if (!apiKey.isPresent()) {
          // serving api key not found
          throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_AUTH_TYPE_NOT_SUPPORTED, Level.FINE,
            "Inference requests to KServe require authentication with API Keys",
            String.format("Serving API Key not found for user ", user.getUsername()));
        }
        String secretName = kubeApiKeyUtils.getServingApiKeySecretName(apiKey.get().getPrefix());
        String rawSecret = kubeApiKeyUtils.getServingApiKeyValueFromKubeSecret(secretName);
        // replace auth header and verb
        authHeader = KubeApiKeyUtils.AUTH_HEADER_API_KEY_PREFIX + rawSecret;
        verb = InferenceVerb.PREDICT;
      }
    } else if (verb != InferenceVerb.PREDICT) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.BAD_REQUEST, Level.FINE, String.format("Verb %s not" +
        " supported in KServe deployments", verb.toString()));
    }
    if (!authHeader.startsWith(KubeApiKeyUtils.AUTH_HEADER_API_KEY_PREFIX)) {
      // JWT not supported for KServe (Istio auth)
      throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_AUTH_TYPE_NOT_SUPPORTED, Level.FINE,
        "Inference requests to KServe require authentication with API Keys");
    }
    
    Project project = serving.getProject();
    JSONObject inferenceServiceStatus;
    try {
      inferenceServiceStatus = kubeKServeClientService.getInferenceServiceStatus(project, serving);
    } catch (KubernetesClientException e) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.SERVING_INSTANCE_INTERNAL, Level.SEVERE, null,
        e.getMessage(), e);
    }
    
    if (inferenceServiceStatus == null) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.SERVING_NOT_RUNNING, Level.FINE);
    }

    // Get host header, istio host and port
    String hostHeader;
    try {
      hostHeader = (new URI(inferenceServiceStatus.getString("url"))).getHost();
    } catch (URISyntaxException e) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.SERVING_INSTANCE_INTERNAL, Level.SEVERE, null,
        e.getMessage(), e);
    }
    InferenceEndpoint endpoint = kubeInferenceEndpoints.getEndpoint(InferenceEndpoint.InferenceEndpointType.NODE);
    String host = endpoint.getAnyHost();
    if (host == null) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.ENDPOINT_NOT_FOUND, Level.SEVERE);
    }
    Integer port = endpoint.getPort(InferencePort.InferencePortName.HTTP).getNumber();
    
    // Build request
    HttpPost request;
    try {
      request = servingInferenceUtils.buildInferenceRequest(host, port,
        kubeServingUtils.getModelServerInferencePath(serving, verb), inferenceRequestJson);
      request.addHeader("host", hostHeader); // needed by Istio to route the request
      request.addHeader("authorization", authHeader); // istio auth
    } catch (URISyntaxException e) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_ERROR, Level.SEVERE, null, e.getMessage(), e);
    }
    
    int nRetry = 3;
    while (nRetry > 0) {
      try {
        HttpContext context = HttpClientContext.create();
        CloseableHttpResponse response = inferenceHttpClient.execute(request, context);
        return inferenceHttpClient.handleInferenceResponse(response);
      } catch (InferenceException e) {
        // Maybe the node we are trying to send requests to died.
      } finally {
        nRetry--;
      }
    }
    
    throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_ERROR, Level.INFO);
  }
}
