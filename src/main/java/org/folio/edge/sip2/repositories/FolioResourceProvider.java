package org.folio.edge.sip2.repositories;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Resource provider for communicating with FOLIO.
 *
 * @author mreno-EBSCO
 *
 */
public class FolioResourceProvider implements IResourceProvider<IRequestData> {
  private static final String HEADER_X_OKAPI_TOKEN = "x-okapi-token";
  private static final Logger log = LogManager.getLogger();

  private final String okapiUrl;
  private final WebClient client;
  private final String tenant;
  private String okapiToken = null;

  /**
   * Construct a FOLIO resource provider with the specified parameters.
   * @param okapiUrl the URL for okapi
   * @param tenant the tenant for the request
   * @param vertx the vertx instance
   */
  public FolioResourceProvider(String okapiUrl, String tenant, Vertx vertx) {
    this.okapiUrl = okapiUrl;
    this.tenant = tenant;
    this.client = WebClient.create(vertx);
  }

  @Override
  public Future<JsonObject> retrieveResource(IRequestData requestData) {
    final HttpRequest<Buffer> request =
        client.getAbs(okapiUrl + requestData.getPath());

    setHeaders(requestData.getHeaders(), request);

    final Future<JsonObject> future = Future.future();
    request
        .expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON)
        .as(BodyCodec.jsonObject())
        .send(ar -> handleResponse(future, ar));

    return future;
  }

  @Override
  public Future<JsonObject> createResource(IRequestData requestData) {
    final HttpRequest<Buffer> request =
        client.postAbs(okapiUrl + requestData.getPath());

    setHeaders(requestData.getHeaders(), request);

    final Future<JsonObject> future = Future.future();
    request
        .expect(ResponsePredicate.SC_CREATED)
        .expect(ResponsePredicate.JSON)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(requestData.getBody(),
            ar -> handleResponse(future, ar));

    return future;
  }

  @Override
  public Future<JsonObject> editResource(IRequestData fromData) {
    return null;
  }

  @Override
  public Future<JsonObject> deleteResource(IRequestData resource) {
    return null;
  }

  private void setHeaders(
      Map<String, String> headers,
      HttpRequest<Buffer> request) {
    for (Map.Entry<String,String> entry : Optional.ofNullable(headers)
        .orElse(Collections.emptyMap()).entrySet()) {
      request.putHeader(entry.getKey(), entry.getValue());
    }

    if (okapiToken != null) {
      request.putHeader(HEADER_X_OKAPI_TOKEN, okapiToken);
    }

    request.putHeader("x-okapi-tenant", tenant);
  }

  private void handleResponse(
      Future<JsonObject> future,
      AsyncResult<HttpResponse<JsonObject>> ar) {
    if (ar.succeeded()) {
      // Save the okapi token. There is probably a better way to do this.
      final String serverToken = ar.result()
          .getHeader(HEADER_X_OKAPI_TOKEN);
      if (!serverToken.equals(okapiToken)) {
        okapiToken = serverToken;
      }
      future.complete(ar.result().body());
    } else {
      log.error("Creation failed", ar.cause());
      future.fail(ar.cause());
    }
  }
}
