# SDA Commons Client Jersey

[![javadoc](https://javadoc.io/badge2/org.sdase.commons/sda-commons-client-jersey/javadoc.svg)](https://javadoc.io/doc/org.sdase.commons/sda-commons-client-jersey)

The module `sda-commons-client-jersey` provides support for using Jersey clients within the Dropwizard application.


## Usage

The [`JerseyClientBundle`](./src/main/java/org/sdase/commons/client/jersey/JerseyClientBundle.java) must be added to the 
application. It provides a [`ClientFactory`](./src/main/java/org/sdase/commons/client/jersey/ClientFactory.java) to 
create clients. The `ClientFactory` needs to be initialized and is available in the `run(...)` phase. Therefore the 
bundle should be declared as field and not in the `initialize` method.

```java
public class MyApplication extends Application<MyConfiguration> {
   private JerseyClientBundle jerseyClientBundle = JerseyClientBundle.builder().build();

   public static void main(final String[] args) {
      new MyApplication().run(args);
   }

   @Override
   public void initialize(Bootstrap<MyConfiguration> bootstrap) {
      // ...
      bootstrap.addBundle(jerseyClientBundle);
      // ...
   }

   @Override
   public void run(MyConfiguration configuration, Environment environment) {
      // ...
      ClientFactory clientFactory = jerseyClientBundle.getClientFactory();
   }
}
```

The `ClientFactory` is able to create Jersey clients from interfaces defining the API with JAX-RS annotations. It may 
also create generic Jersey clients that can build the request definition with a fluent API:

```java
Client googleClient = clientFactory.externalClient()
      .buildGenericClient("google")
      .target("https://maps.google.com");
Response response = googleClient.path("api")/* ... */.get();
```


## Configuration

All clients defined with the `ClientFactory` can be built either for the SDA Platform or for external services. 
Both SDA Platform and external clients are [OpenTracing](https://opentracing.io/) enabled.
Clients for the SDA Platform have some magic added that clients for an external service don't have:

- _Trace-Token_

  SDA Platform clients always add the Trace-Token of the current incoming request context to the headers of the outgoing
  request. If no incoming request context is found, a new unique Trace-Token is generated for each request. It will be
  discarded when the outgoing request completes.
  
- _Authorization_

  SDA Platform clients can be configured to pass through the Authorization header of an incoming request context:
  
  ```java
  clientFactory.platformClient()
        .enableAuthenticationPassThrough()
        .api(OtherSdaServiceClient.class)
        .atTarget("http://other-sda-service.sda.net/api");
  ```
- _Consumer-Token_
  
  SDA Platform clients are able to send a Consumer-Token header to identify the caller. The token that is used has to be
  configured and published to the bundle. Currently the token is only the name of the consumer.
  
  ```java
  private JerseyClientBundle jerseyClientBundle = JerseyClientBundle.builder()
        .withConsumerTokenProvider(MyConfiguration::getConsumerToken).build();
  ```
  
  Then the clients can be configured to add a Consumer-Token header:
  
  ```java
  clientFactory.platformClient()
        .enableConsumerToken()
        .api(OtherSdaServiceClient.class)
        .atTarget("http://other-sda-service.sda.net/api");
  ```


## Writing API Clients as interfaces

Client interfaces use the same annotations as the service definitions for REST endpoints. An example is the 
[`MockApiClient`](./src/test/java/org/sdase/commons/client/jersey/test/MockApiClient.java) in the integration tests
of this module.

Error handling is different based on the return type:

If a specific return type is defined (e.g. `List<MyResource> getMyResources();`), it is only returned for successful 
requests. In any error or redirect case, an exception is thrown. The thrown exception is a 
[`ClientRequestException`](./src/main/java/org/sdase/commons/client/jersey/error/ClientRequestException.java)
wrapping `javax.ws.rs.ProcessingException` or subclasses of `javax.ws.rs.WebApplicationException`: 
- `javax.ws.rs.RedirectionException` to indicate redirects 
- `javax.ws.rs.ClientErrorException` for client errors
- `javax.ws.rs.ServerErrorException` for server errors

If the `ClientRequestException` exception is handled in the application code **the application must `close()` the 
exception**.

If a `javax.ws.rs.core.Response` is defined as return type, HTTP errors and redirects can be read from the `Response`
object. **Remember to always close the `Response` object. It references open socket streams.**

In both variants a `java.net.ConnectException` may be thrown if the client can't connect to the server.

## Using Jersey `Client`

Jersey Clients can be built using the client factory for cases where the API variant with an interface is not suitable.

Jersey clients can not automatically convert `javax.ws.rs.WebApplicationException` into our 
`ClientRequestException`. To avoid passing through the error the application received to the caller of the application,
the exceptions must be handled for all usages that expect a specific type as return value.

The [`ClientErrorUtil`](./src/main/java/org/sdase/commons/client/jersey/error/ClientErrorUtil.java) can be used to 
convert the exceptions. In the following example, a 4xx or 5xx response will result in a `ClientRequestException` that
causes a 500 response for the incoming request:

```java
Client client = clientFactory.platformClient().buildGenericClient("test")
Invocation.Builder requestBuilder = client.target(BASE_URL).request(MediaType.APPLICATION_JSON); 
MyResource myResource = ClientErrorUtil.convertExceptions(() -> requestBuilder.get(MyResource.class));
```

If the error should be handled in the application, the exception may be caught and the error can be read from the 
response.
If the `ClientRequestException` is caught the implementation **must `close()` it**.
If it is not handled or rethrown, the `ClientRequestExceptionMapper` will take care about closing the underlying open 
socket.
Therefore the `ClientRequestException` **must not** be wrapped as `cause` in another exception!

```java
Client client = clientFactory.platformClient().buildGenericClient("test")
Invocation.Builder requestBuilder = client.target(BASE_URL).request(MediaType.APPLICATION_JSON); 
try {
   MyResource myResource = ClientErrorUtil.convertExceptions(() -> requestBuilder.get(MyResource.class));
}
catch (ClientRequestException e) {
   ApiError error = ClientErrorUtil.readErrorBody(e);
   e.close();
}
```

## Multipart Support

To support sending multipart requests like file uploads, `sda-commons-shared-forms` has to be added to the project. 

The client is the configured automatically to support multipart.


## Concurrency

If you plan to use the Jersey client from another thread, note that the authorization from the request context and the trace token are missing.
This can cause issues.
You can use `ContainerRequestContextHolder.transferRequestContext` to transfer the request context and `MDC` to another thread.

```java
executorService.submit(transferRequestContext(() -> {
  // MDC.get("Trace-Token") returns the same value as the parent thread
})).get();
```

## HTTP Client Configuration and Proxy Support

Each client can be configured with the standard
[Dropwizard configuration](https://www.dropwizard.io/en/latest/manual/configuration.html#man-configuration-clients-http).
Please note that this requires that there is a property in your Application's `Configuration` class. 

```java
import org.sdase.commons.client.jersey.HttpClientConfiguration;

public class MyConfiguration extends Configuration {
   private HttpClientConfiguration myClient = new HttpClientConfiguration();

   public HttpClientConfiguration getMyClient() {
     return myClient;
   }

   public void setMyClient(HttpClientConfiguration myClient) {
     this.myClient = myClient;
   }
}
```

```java
Client client = clientFactory.platformClient(configuration.getMyClient()).buildGenericClient("test");
```

```yaml
myClient:
  timeout: 500ms
  proxy:
    host: 192.168.52.11
    port: 8080
    scheme : http
```

> Tip: There is no need to make all configuration properties available as environment variables.
> Seldomly used properties can always be configured using [System Properties](https://www.dropwizard.io/en/latest/manual/core.html#man-core-configuration).

This configuration can be used to configure a proxy server if needed.
Use this if all clients should use individual proxy configurations.

In addition, each client can consume the standard [proxy system properties](https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html#Proxies).
Please note that a specific proxy configuration in the `HttpClientConfiguration` disables the proxy system properties for the client using that configuration.
This can be helpful when all clients in an Application should use the same proxy configuration (this includes the clients that are used by the [`sda-commons-server-auth` bundle](../sda-commons-server-auth).

## Tips and Tricks

### 3rd Party `javax.ws.rs-api` Client Implementations in Classpath

The clients used in sda-commons require the Jersey Client implementation. 
If you are facing problems with other `javax.ws.rs-api` implementations in the classpath (e.g. RestEasy which comes
with the Keycloak SDK) the Jersey Client Builder must be propagated in your project as service.
Therefore the service definition `src/main/resources/META-INF/services/javax.ws.rs.client.ClientBuilder` must be added
to your project containing:

```
org.glassfish.jersey.client.JerseyClientBuilder
```

This works if the library that requires the other implementation does not rely on the Java ServiceLoader.
