package org.sdase.commons.server.starter.builder;

import io.dropwizard.Configuration;
import org.sdase.commons.server.auth.config.AuthConfigProvider;
import org.sdase.commons.server.consumer.ConsumerTokenBundle.ConsumerTokenConfigProvider;
import org.sdase.commons.server.cors.CorsConfigProvider;
import org.sdase.commons.server.opa.config.OpaConfigProvider;

/**
 * Container for builder interfaces required for custom configuration classes. They are all in one
 * place in the order how they are called for easier extension with more supported bundles.
 */
public interface CustomConfigurationProviders {

  interface AuthConfigProviderBuilder<C extends Configuration> {

    /**
     * Disable authentication entirely.
     *
     * @return the builder instance
     */
    CorsConfigProviderBuilder<C> withoutAuthentication();

    /**
     * Enables authentication (i.e. token validation) for annotated endpoints and requires tokens
     *
     * @param authConfigProvider a provider, for the {@link
     *     org.sdase.commons.server.auth.config.AuthConfig}, e.g. {@code MyAppConfig::getAuth}
     * @return the builder instance
     */
    CorsConfigProviderBuilder<C> withAuthConfigProvider(AuthConfigProvider<C> authConfigProvider);

    /**
     * Enables authentication (i.e. token validation) for all endpoints and use the {@link
     * org.sdase.commons.server.opa.OpaBundle} to authorize the requests. Requests without
     * Authorization header will <i>not</i> be rejected but need to be handled in the Authorization
     * policy.
     *
     * @param authConfigProvider a provider, for the {@link
     *     org.sdase.commons.server.auth.config.AuthConfig}, e.g. {@code MyAppConfig::getAuth}
     * @param opaConfigProvider a provider, for the {@link
     *     org.sdase.commons.server.opa.config.OpaConfig}, e.g. {@code MyAppConfig::getOpa}
     * @return the builder instance
     */
    CorsConfigProviderBuilder<C> withOpaAuthorization(
        AuthConfigProvider<C> authConfigProvider, OpaConfigProvider<C> opaConfigProvider);
  }

  interface CorsConfigProviderBuilder<C extends Configuration> {

    /**
     * Disable CORS support. Browsers are not allowed to access the application from a foreign
     * domain.
     *
     * @return the builder instance
     */
    ConsumerTokenConfigBuilder<C> withoutCorsSupport();

    /**
     * @param corsConfigProvider a provider, for the {@link
     *     org.sdase.commons.server.cors.CorsConfiguration}, e.g. {@code MyAppConfig::getCors}
     * @return the builder instance
     */
    ConsumerTokenConfigBuilder<C> withCorsConfigProvider(CorsConfigProvider<C> corsConfigProvider);
  }

  interface ConsumerTokenConfigBuilder<C extends Configuration> {

    /**
     * Disable consumer token support. Consumer tokens are not required to access this service and
     * will be ignored if a client sends them.
     *
     * @return the builder instance
     */
    OpenApiInitialBuilder<C> withoutConsumerTokenSupport();

    /**
     * Disable consumer token support. Consumer tokens are not required to access this service but
     * will be tracked if a client sends them.
     *
     * @return the builder instance
     */
    OpenApiInitialBuilder<C> withOptionalConsumerToken();

    /**
     * Enable consumer token support. Consumer tokens are required to access this service. Further
     * configuration may exclude paths from this requirement.
     *
     * @return the builder instance
     */
    ConsumerTokenRequiredConfigInitialBuilder<C> withRequiredConsumerToken();

    /**
     * Configure consumer token support based on provided configuration per instance.
     *
     * @param consumerTokenConfigProvider the provider of the configuration, e.g. {@code
     *     MyConfig::getConsumerToken}
     * @return the builder instance
     */
    OpenApiInitialBuilder<C> withConsumerTokenConfigProvider(
        ConsumerTokenConfigProvider<C> consumerTokenConfigProvider);
  }

  interface ConsumerTokenRequiredConfigInitialBuilder<C extends Configuration>
      extends OpenApiInitialBuilder<C> {

    /**
     * Define paths by regex patterns that do not require a consumer token from the client.
     *
     * @param regex regex that matches paths that do not require a consumer token from the client
     * @return the builder instance
     */
    OpenApiInitialBuilder<C> withExcludePatternsForRequiredConsumerToken(String... regex);
  }

  interface OpenApiInitialBuilder<C extends Configuration> extends PlatformBundleBuilder<C> {

    /**
     * Adds a package to the packages Swagger should scan to pick up resources.
     *
     * @param resourcePackage the package to be scanned; not null
     * @return the builder instance
     * @throws NullPointerException if resourcePackage is null
     * @throws IllegalArgumentException if resourcePackage is empty
     * @see org.sdase.commons.server.openapi.OpenApiBundle.InitialBuilder#addResourcePackage(String)
     */
    OpenApiFinalBuilder<C> addOpenApiResourcePackage(String resourcePackage);

    /**
     * Adds the package of the given class to the packages Swagger should scan to pick up resources.
     *
     * @param resourcePackageClass the class whose package should be scanned; not null
     * @return the builder instance
     * @throws NullPointerException if resourcePackageClass is null
     * @see
     *     org.sdase.commons.server.openapi.OpenApiBundle.InitialBuilder#addResourcePackageClass(Class)
     */
    OpenApiFinalBuilder<C> addOpenApiResourcePackageClass(Class<?> resourcePackageClass);

    /**
     * Use an existing OpenAPI 3 specification as base for the generation.
     *
     * <p>Note that the OpenAPI annotations always override values from the files if classes are
     * registered with {@link #addOpenApiResourcePackage(String)} or {@link
     * #addOpenApiResourcePackageClass(Class)}.
     *
     * @param openApiJsonOrYaml the OpenAPI 3 specification as json or yaml
     * @return the builder instance
     * @see
     *     org.sdase.commons.server.openapi.OpenApiBundle.InitialBuilder#withExistingOpenAPI(String)
     */
    OpenApiFinalBuilder<C> withExistingOpenAPI(String openApiJsonOrYaml);

    /**
     * Reads an existing OpenAPI 3 specification from the given classpath resource and provide it to
     * {@link #withExistingOpenAPI(String)}
     *
     * @param path the path to an OpenAPI 3 YAML or JSON file in the classpath.
     * @return the builder instance
     * @see
     *     org.sdase.commons.server.openapi.OpenApiBundle.InitialBuilder#withExistingOpenAPIFromClasspathResource(String)
     */
    OpenApiFinalBuilder<C> withExistingOpenAPIFromClasspathResource(String path);
  }

  interface OpenApiFinalBuilder<C extends Configuration>
      extends PlatformBundleBuilder<C>, OpenApiInitialBuilder<C> {

    OpenApiFinalBuilder<C> disableSwaggerEmbedParameter();
  }
}
