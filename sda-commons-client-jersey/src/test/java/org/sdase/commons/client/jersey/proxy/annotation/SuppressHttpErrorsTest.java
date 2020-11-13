package org.sdase.commons.client.jersey.proxy.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sdase.commons.client.jersey.proxy.ApiClientInvocationHandler.createProxy;

import java.util.Arrays;
import java.util.Collection;
import javax.ws.rs.WebApplicationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
import org.sdase.commons.client.jersey.error.ClientRequestException;

@RunWith(Parameterized.class)
public class SuppressHttpErrorsTest<T extends SuppressHttpErrorsTest.TestApi> {

  private final TestApi testApiImpl;
  private final boolean expectException;

  public SuppressHttpErrorsTest(Class<T> testApi, int givenHttpError, boolean expectException) {
    WebApplicationException error = new WebApplicationException(givenHttpError);
    this.testApiImpl = createProxy(testApi, createDelegate(testApi, error));
    this.expectException = expectException;
  }

  @Parameters(name = "{0}: {1}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[] {ClientErrorsSuppressed.class, 404, false},
        new Object[] {ClientErrorsSuppressed.class, 500, true},
        new Object[] {ServerErrorsSuppressed.class, 500, false},
        new Object[] {ServerErrorsSuppressed.class, 400, true},
        new Object[] {ServerErrorsAndNotFoundSuppressed.class, 500, false},
        new Object[] {ServerErrorsAndNotFoundSuppressed.class, 404, false},
        new Object[] {ServerErrorsAndNotFoundSuppressed.class, 400, true},
        new Object[] {NotFoundAndForbiddenSuppressed.class, 403, false},
        new Object[] {NotFoundAndForbiddenSuppressed.class, 404, false},
        new Object[] {NotFoundAndForbiddenSuppressed.class, 400, true},
        new Object[] {RedirectErrorsSuppressed.class, 303, false},
        new Object[] {RedirectErrorsSuppressed.class, 400, true});
  }

  @Test
  public void exceptionForObject() {
    if (expectException) {
      assertThatExceptionOfType(ClientRequestException.class).isThrownBy(testApiImpl::suppressed);
    } else {
      assertThat(testApiImpl.suppressed()).isNull();
    }
  }

  @Test
  public void exceptionForVoid() {
    if (expectException) {
      assertThatExceptionOfType(ClientRequestException.class)
          .isThrownBy(testApiImpl::suppressedForVoid);
    } else {
      assertThatCode(testApiImpl::suppressedForVoid).doesNotThrowAnyException();
    }
  }

  interface TestApi {
    Object suppressed();

    void suppressedForVoid();
  }

  public interface ClientErrorsSuppressed extends TestApi {
    @SuppressHttpErrors(allClientErrors = true)
    Object suppressed();

    @SuppressHttpErrors(allClientErrors = true)
    void suppressedForVoid();
  }

  public interface ServerErrorsSuppressed extends TestApi {
    @SuppressHttpErrors(allServerErrors = true)
    Object suppressed();

    @SuppressHttpErrors(allServerErrors = true)
    void suppressedForVoid();
  }

  public interface ServerErrorsAndNotFoundSuppressed extends TestApi {

    @SuppressHttpErrors(value = 404, allServerErrors = true)
    Object suppressed();

    @SuppressHttpErrors(value = 404, allServerErrors = true)
    void suppressedForVoid();
  }

  public interface NotFoundAndForbiddenSuppressed extends TestApi {

    @SuppressHttpErrors(value = {403, 404})
    Object suppressed();

    @SuppressHttpErrors(value = {403, 404})
    void suppressedForVoid();
  }

  public interface RedirectErrorsSuppressed extends TestApi {

    @SuppressHttpErrors(allRedirectErrors = true)
    Object suppressed();

    @SuppressHttpErrors(allRedirectErrors = true)
    void suppressedForVoid();
  }

  private T createDelegate(Class<T> testApi, WebApplicationException error) {
    T mock = mock(testApi);
    when(mock.suppressed()).thenThrow(error);
    Mockito.doThrow(error).when(mock).suppressedForVoid();
    return mock;
  }
}
