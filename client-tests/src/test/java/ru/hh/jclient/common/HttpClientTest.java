package ru.hh.jclient.common;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.MediaType.ANY_VIDEO_TYPE;
import static com.google.common.net.MediaType.JSON_UTF_8;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.net.MediaType.PROTOBUF;
import static com.google.common.net.MediaType.XML_UTF_8;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static ru.hh.jclient.common.HttpHeaders.X_HH_DEBUG;
import static ru.hh.jclient.common.HttpHeaders.X_REQUEST_ID;
import static ru.hh.jclient.common.HttpParams.DEBUG;
import static ru.hh.jclient.common.TestRequestDebug.Call.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;

import ru.hh.jclient.common.HttpClientImpl.CompletionHandler;
import ru.hh.jclient.common.exception.ClientResponseException;
import ru.hh.jclient.common.exception.NoContentTypeException;
import ru.hh.jclient.common.exception.ResponseConverterException;
import ru.hh.jclient.common.exception.UnexpectedContentTypeException;
import ru.hh.jclient.common.model.ProtobufTest;
import ru.hh.jclient.common.model.ProtobufTest.ProtobufTestMessage;
import ru.hh.jclient.common.util.storage.NonTransferableSupplier;
import ru.hh.jclient.common.model.XmlError;
import ru.hh.jclient.common.model.XmlTest;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.FluentCaseInsensitiveStringsMap;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;

public class HttpClientTest extends HttpClientTestBase {

  private ObjectMapper objectMapper = new ObjectMapper();
  private JAXBContext jaxbContext;

  public HttpClientTest() throws JAXBException {
    jaxbContext = JAXBContext.newInstance(XmlTest.class, XmlError.class);
  }

  @Before
  public void before() {
    debug.reset();
  }

  @Test
  public void testPlain() throws InterruptedException, ExecutionException, IOException {
    Supplier<Request> actualRequest = withEmptyContext().okRequest("test тест", PLAIN_TEXT_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/plain").build();
    String text = http.with(request).expectPlainText().result().get();
    assertEquals("test тест", text);
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testPlainCp1251() throws InterruptedException, ExecutionException, IOException {
    Charset charset = Charset.forName("Cp1251");
    Supplier<Request> actualRequest = withEmptyContext().okRequest("test тест".getBytes(charset), PLAIN_TEXT_UTF_8.withCharset(charset));

    Request request = new RequestBuilder("GET").setUrl("http://localhost/plain").build();
    String text = http.with(request).expectPlainText(charset).result().get();
    assertEquals("test тест", text);
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testResponseWrapper() throws InterruptedException, ExecutionException, IOException, JAXBException {
    XmlTest test = new XmlTest("test тест");
    Supplier<Request> actualRequest = withEmptyContext().okRequest(jsonBytes(test), JSON_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/json").build();
    ResultWithResponse<XmlTest> testOutputWrapper = http.with(request).expectJson(objectMapper, XmlTest.class).resultWithResponse().get();
    Optional<XmlTest> testOutput = testOutputWrapper.get();
    assertEquals(test.name, testOutput.get().name);
    assertNotNull(testOutputWrapper.getResponse());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test(expected = NoContentTypeException.class)
  public void testNoContentType() throws Throwable {
    withEmptyContext().okRequest("test тест".getBytes(), null);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    try {
      http.with(request).expectXml(jaxbContext, XmlTest.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, RESPONSE, FINISHED);
      throw e.getCause();
    }
  }

  @Test(expected = UnexpectedContentTypeException.class)
  public void testIncorrectContentType() throws Throwable {
    withEmptyContext().okRequest("test тест".getBytes(), JSON_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    try {
      http.with(request).expectXml(jaxbContext, XmlTest.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, RESPONSE, FINISHED);
      throw e.getCause();
    }
  }

  @Test
  public void testXml() throws InterruptedException, ExecutionException, IOException, JAXBException {
    XmlTest test = new XmlTest("test тест");
    Supplier<Request> actualRequest = withEmptyContext().okRequest(xmlBytes(test), XML_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    XmlTest testOutput = http.with(request).expectXml(jaxbContext, XmlTest.class).result().get();
    assertEquals(test.name, testOutput.name);
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test(expected = ResponseConverterException.class)
  public void testIncorrectXml() throws Throwable {
    withEmptyContext().okRequest("test тест".getBytes(), XML_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    try {
      http.with(request).expectXml(jaxbContext, XmlTest.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, RESPONSE, CONVERTER_PROBLEM, FINISHED);
      throw e.getCause();
    }
  }

  @Test
  public void testJson() throws IOException, InterruptedException, ExecutionException {
    String responseBody = "{\"name\":\"test тест\"}";
    Supplier<Request> actualRequest = withEmptyContext().okRequest(responseBody, JSON_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/json").build();
    XmlTest testOutput = http.with(request).<XmlTest> expectJson(objectMapper, XmlTest.class).result().get();
    assertEquals("test тест", testOutput.name);
    assertEqualRequests(request, actualRequest.get());
  }

  @Test
  public void testJsonCollection() throws IOException, InterruptedException, ExecutionException {
    XmlTest test1 = new XmlTest("test тест1");
    XmlTest test2 = new XmlTest("test тест2");
    List<XmlTest> tests = Arrays.asList(test1, test2);
    Supplier<Request> actualRequest = withEmptyContext().okRequest(jsonBytes(tests), JSON_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/json").build();
    Collection<XmlTest> testOutput = http.with(request).<XmlTest> expectJsonCollection(objectMapper, XmlTest.class).result().get();
    assertEquals(tests.size(), testOutput.size());
    assertEquals(test1.name, Iterables.get(testOutput, 0).name);
    assertEquals(test2.name, Iterables.get(testOutput, 1).name);
    assertEqualRequests(request, actualRequest.get());
  }

  @Test(expected = ResponseConverterException.class)
  public void testIncorrectJson() throws Throwable {
    withEmptyContext().okRequest("test тест".getBytes(), JSON_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/json").build();
    try {
      http.with(request).<XmlTest> expectJson(objectMapper, XmlTest.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, RESPONSE, CONVERTER_PROBLEM, FINISHED);
      throw e.getCause();
    }
  }

  @Test
  public void testProtobuf() throws IOException, InterruptedException, ExecutionException {
    ProtobufTest.ProtobufTestMessage test = ProtobufTest.ProtobufTestMessage.newBuilder().addIds(1).build();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    test.writeTo(out);

    Supplier<Request> actualRequest = withEmptyContext().okRequest(out.toByteArray(), PROTOBUF);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/protobuf").build();
    ProtobufTestMessage testOutput = http.with(request).<ProtobufTestMessage> expectProtobuf(ProtobufTestMessage.class).result().get();
    assertEquals(test.getIdsList(), testOutput.getIdsList());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test(expected = ResponseConverterException.class)
  public void testIncorrectProtobuf() throws Throwable {
    withEmptyContext().okRequest("test тест".getBytes(), PROTOBUF);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/protobuf").build();
    try {
      http.with(request).<ProtobufTestMessage> expectProtobuf(ProtobufTestMessage.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, RESPONSE, CONVERTER_PROBLEM, FINISHED);
      throw e.getCause();
    }
  }

  @Test
  public void testEmpty() throws IOException, InterruptedException, ExecutionException {
    Supplier<Request> actualRequest = withEmptyContext().okRequest(new byte[0], ANY_VIDEO_TYPE);
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();
    Object testOutput = http.with(request).expectEmpty().result().get();
    assertNull(testOutput);
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testReadOnly() throws IOException, InterruptedException, ExecutionException {
    Supplier<Request> actualRequest = withEmptyContext().okRequest(new byte[0], ANY_VIDEO_TYPE);
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();
    Object testOutput = http.with(request).readOnly().expectEmpty().result().get();
    assertNull(testOutput);
    assertTrue(actualRequest.get().getUrl().indexOf(HttpParams.READ_ONLY_REPLICA) > -1);
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, LABEL, FINISHED);
  }

  @Test
  public void testHeaders() throws IOException, InterruptedException, ExecutionException {
    FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
    headers.add("myheader1", "myvalue1");
    headers.add("myheader1", "myvalue2");
    headers.add("myheader2", "myvalue1");
    headers.add(X_REQUEST_ID, "111");

    Supplier<Request> actualRequest = withContext(headers).okRequest(new byte[0], ANY_VIDEO_TYPE);
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").addHeader("someheader", "somevalue").build();
    http.with(request).expectEmpty().result().get();
    // all those headers won't be accepted, as they come from global request and are not in allowed list
    assertFalse(actualRequest.get().getHeaders().containsKey("myheader1"));
    assertFalse(actualRequest.get().getHeaders().containsKey("myheader2"));
    // this header is accepted because it consists in allowed list
    assertEquals("111", actualRequest.get().getHeaders().getFirstValue(X_REQUEST_ID));
    // this header is accepted since it comes from local request
    assertEquals("somevalue", actualRequest.get().getHeaders().getFirstValue("someheader"));
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test(expected = IllegalStateException.class)
  public void testDebugManualHeaderWithNoDebug() throws IOException, InterruptedException, ExecutionException {
    // situation when manually building request with debug header, it should be removed
    Request request = new RequestBuilder("GET")
        .setUrl("http://localhost/empty")
        .addHeader(X_HH_DEBUG, "true")
        .addHeader(AUTHORIZATION, "someauth")
        .build();

    withEmptyContext().okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertFalse(httpClientContext.isDebugMode());
    http.with(request).expectEmpty().result().get();
  }

  @Test(expected = IllegalStateException.class)
  public void testDebugManualParamWithNoDebug() throws IOException, InterruptedException, ExecutionException {
    // situation when manually building request with debug param
    Request request = new RequestBuilder("GET")
        .setUrl("http://localhost/empty")
        .addHeader(AUTHORIZATION, "someauth")
        .addQueryParam(HttpParams.DEBUG, "123")
        .build();

    withEmptyContext().okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertFalse(httpClientContext.isDebugMode());
    http.with(request).expectEmpty().result().get();
  }

  @Test
  public void testDebug() throws IOException, InterruptedException, ExecutionException {
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();
    Supplier<Request> actualRequest;

    // debug is on via header, headers are passed
    FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap().add(X_HH_DEBUG, "true").add(AUTHORIZATION, "someauth");
    FluentCaseInsensitiveStringsMap queryParams = new FluentCaseInsensitiveStringsMap();

    actualRequest = withContext(headers, queryParams).okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertTrue(httpClientContext.isDebugMode());

    http.with(request).expectEmpty().result().get();

    assertEquals("true", actualRequest.get().getHeaders().getFirstValue(X_HH_DEBUG));
    assertEquals("someauth", actualRequest.get().getHeaders().getFirstValue(AUTHORIZATION));
    assertEquals(DEBUG, actualRequest.get().getQueryParams().get(0).getName());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);

    // debug is on via query param, headers are passed
    headers = new FluentCaseInsensitiveStringsMap().add(AUTHORIZATION, "someauth");
    queryParams = new FluentCaseInsensitiveStringsMap().add(DEBUG, "123");

    actualRequest = withContext(headers, queryParams).okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertTrue(httpClientContext.isDebugMode());

    http.with(request).expectEmpty().result().get();

    assertEquals("true", actualRequest.get().getHeaders().getFirstValue(X_HH_DEBUG));
    assertEquals("someauth", actualRequest.get().getHeaders().getFirstValue(AUTHORIZATION));
    assertEquals(DEBUG, actualRequest.get().getQueryParams().get(0).getName());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testExternalRequestWithDebugOn() throws IOException, InterruptedException, ExecutionException {
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();

    // debug is on but for 'external' but header / param should not be passed
    FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap().add(X_HH_DEBUG, "true").add(AUTHORIZATION, "someauth");
    FluentCaseInsensitiveStringsMap queryParams = new FluentCaseInsensitiveStringsMap().add(DEBUG, "123");

    Supplier<Request> actualRequest = withContext(headers, queryParams).okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertTrue(httpClientContext.isDebugMode());

    http.with(request).external().expectEmpty().result().get();

    assertFalse(actualRequest.get().getHeaders().containsKey(X_HH_DEBUG));
    assertFalse(actualRequest.get().getHeaders().containsKey(AUTHORIZATION)); // not passed through but can be added manually to request if needed
    assertTrue(actualRequest.get().getQueryParams().isEmpty());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, LABEL, FINISHED);
  }

  @Test
  public void testNoDebugRequestWithDebugOn() throws IOException, InterruptedException, ExecutionException {
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();

    // debug is on but for 'external' but header / param should not be passed
    FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap().add(X_HH_DEBUG, "true").add(AUTHORIZATION, "someauth");
    FluentCaseInsensitiveStringsMap queryParams = new FluentCaseInsensitiveStringsMap().add(DEBUG, "123");

    Supplier<Request> actualRequest = withContext(headers, queryParams).okRequest(new byte[0], ANY_VIDEO_TYPE);
    assertTrue(httpClientContext.isDebugMode());

    http.with(request).noDebug().expectEmpty().result().get();

    assertFalse(actualRequest.get().getHeaders().containsKey(X_HH_DEBUG));
    assertTrue(actualRequest.get().getHeaders().containsKey(AUTHORIZATION)); // passed through because it might be auth not related to debug
    assertTrue(actualRequest.get().getQueryParams().isEmpty());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, LABEL, FINISHED);
  }

  @Test
  public void testHostsWithSession() throws IOException, InterruptedException, ExecutionException {
    FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
    headers.add(HttpHeaders.HH_PROTO_SESSION, "somesession");

    Supplier<Request> actualRequest = withContext(headers).okRequest(new byte[0], ANY_VIDEO_TYPE);
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();
    http.with(request).expectEmpty().result().get();
    assertEquals("somesession", actualRequest.get().getHeaders().getFirstValue(HttpHeaders.HH_PROTO_SESSION));
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);

    request = new RequestBuilder("GET").setUrl("http://localhost2/empty").build();
    http.with(request).expectEmpty().result().get();
    assertFalse(actualRequest.get().getHeaders().containsKey(HttpHeaders.HH_PROTO_SESSION));
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test(expected = ClientResponseException.class)
  public void testResponseError() throws Throwable {
    withEmptyContext().request(ANY_VIDEO_TYPE, 403);
    Request request = new RequestBuilder("GET").setUrl("http://localhost/empty").build();
    try {
      http.with(request).expectEmpty().result().get();
    }
    catch (ExecutionException e) {
      // exception about bad response status, not reported to debug, so no CLIENT_PROBLEM here
      debug.assertCalled(REQUEST, RESPONSE, FINISHED);
      throw e.getCause();
    }
  }

  @Test(expected = TestException.class)
  public void testHttpClientError() throws Throwable {
    AsyncHttpClient httpClient = mock(AsyncHttpClient.class);
    when(httpClient.getConfig()).thenReturn(httpClientConfig);
    when(httpClient.executeRequest(isA(Request.class), isA(CompletionHandler.class))).then(iom -> {
      CompletionHandler handler = iom.getArgumentAt(1, CompletionHandler.class);
      handler.onThrowable(new TestException());
      return null;
    });
    http = new HttpClientBuilder(httpClient, ImmutableSet.of("http://localhost"), new NonTransferableSupplier<>(() -> httpClientContext));

    withEmptyContext();

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    try {
      http.with(request).<ProtobufTestMessage> expectProtobuf(ProtobufTestMessage.class).result().get();
    }
    catch (ExecutionException e) {
      debug.assertCalled(REQUEST, CLIENT_PROBLEM, FINISHED);
      throw e.getCause();
    }
  }

  @Test
  public void testErrorXml() throws InterruptedException, ExecutionException, IOException, JAXBException {
    XmlError error = new XmlError("errror message тест");
    byte[] bytes = xmlBytes(error);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/json").build();

    // with default range (>399)
    Supplier<Request> actualRequest = withEmptyContext().request(bytes, XML_UTF_8, 400);
    ResultOrErrorWithResponse<XmlTest, XmlError> response = http
        .with(request)
        .expectJson(objectMapper, XmlTest.class)
        .orXmlError(jaxbContext, XmlError.class)
        .resultWithResponse()
        .get();
    assertFalse(response.get().isPresent());
    assertTrue(response.getError().isPresent());
    assertEquals(error.message, response.getError().get().message);
    assertNotNull(response.getResponse());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);

    // and specific range
    actualRequest = withEmptyContext().request(bytes, XML_UTF_8, 800);

    response = http
        .with(request)
        .expectJson(objectMapper, XmlTest.class)
        .orXmlError(jaxbContext, XmlError.class)
        .forStatus(800)
        .resultWithResponse()
        .get();
    assertFalse(response.get().isPresent());
    assertTrue(response.getError().isPresent());
    assertEquals(error.message, response.getError().get().message);
    assertNotNull(response.getResponse());
    assertEquals(800, response.getResponse().getStatusCode());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);

    // and when range is missed
    response = http
        .with(request)
        .expectJson(objectMapper, XmlTest.class)
        .orXmlError(jaxbContext, XmlError.class)
        .forStatus(500)
        .resultWithResponse()
        .get();
    assertFalse(response.get().isPresent());
    assertFalse(response.getError().isPresent());
    assertNotNull(response.getResponse());
    assertEquals(800, response.getResponse().getStatusCode());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testXmlWithNoError() throws InterruptedException, ExecutionException, IOException, JAXBException {
    XmlTest test = new XmlTest("test тест");
    Supplier<Request> actualRequest = withEmptyContext().okRequest(xmlBytes(test), XML_UTF_8);

    Request request = new RequestBuilder("GET").setUrl("http://localhost/xml").build();
    ResultOrErrorWithResponse<XmlTest, String> response = http
        .with(request)
        .expectXml(jaxbContext, XmlTest.class)
        .orPlainTextError()
        .resultWithResponse()
        .get();
    assertTrue(response.get().isPresent());
    assertFalse(response.getError().isPresent());
    assertEquals(test.name, response.get().get().name);
    assertNotNull(response.getResponse());
    assertEqualRequests(request, actualRequest.get());
    debug.assertCalled(REQUEST, RESPONSE, RESPONSE_CONVERTED, FINISHED);
  }

  @Test
  public void testAcceptHeaderBeingSet() throws InterruptedException, ExecutionException, IOException, JAXBException {
    Supplier<Request> actualRequest;
    Request request = new RequestBuilder("GET").setUrl("http://localhost/content").build();

    ResultProcessor<?> resultProcessor;

    actualRequest = withEmptyContext().okRequest("test тест", PLAIN_TEXT_UTF_8);
    resultProcessor = http.with(request).expectPlainText();
    resultProcessor.result().get();
    assertProperAcceptHeader(resultProcessor, actualRequest.get());

    actualRequest = withEmptyContext().okRequest(jsonBytes(new XmlTest("zxc")), JSON_UTF_8);
    resultProcessor = http.with(request).expectJson(objectMapper, XmlTest.class);
    resultProcessor.result().get();
    assertProperAcceptHeader(resultProcessor, actualRequest.get());

    actualRequest = withEmptyContext().okRequest(xmlBytes(new XmlTest("zxc")), XML_UTF_8);
    resultProcessor = http.with(request).expectXml(jaxbContext, XmlTest.class);
    resultProcessor.result().get();
    assertProperAcceptHeader(resultProcessor, actualRequest.get());
  }

  private static class TestException extends Exception {
  }

  private byte[] xmlBytes(Object object) throws JAXBException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    jaxbContext.createMarshaller().marshal(object, out);
    return out.toByteArray();
  }

  private byte[] jsonBytes(Object object) throws JsonGenerationException, JsonMappingException, IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    objectMapper.writeValue(out, object);
    return out.toByteArray();
  }

}
