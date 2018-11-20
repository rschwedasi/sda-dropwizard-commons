package org.sdase.commons.server.weld;

import org.sdase.commons.server.weld.internal.WeldSupport;
import io.dropwizard.Application;
import org.jboss.weld.environment.se.WeldContainer;

/**
 * <p>
 * Start a Dropwizard application with Weld support.
 * </p>
 * <p>
 * This is used to create the Application class inside the Weld Context, so that
 * the Application, or instances produced by the Application, can be injected.
 * </p>
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 *     <code>
 *     public static void main(final String[]args) throws Exception {
 *         DropwizardWeldHelper.run(Application.class, args);
 *     }
 *     </code>
 * </pre>
 */
public class DropwizardWeldHelper {
   // We are rethrowing a generic exception that we have no control over, ignore the warning
   public static <C extends Application> void run(Class<C> applicationClass, String... arguments) throws Exception { // NOSONAR
      WeldSupport.initializeCDIProviderIfRequired();

      // We are not calling shutdown on the weld container, as it has an automatic shutdown hook on application exit,
      // so ignore the warning. Calling shutdown here is dangerous, as Application.run isn't blocking as one would
      // expect and that would cause the weld container to be unloaded directly after startup while the application is
      // still running.
      WeldContainer weldContainer = WeldSupport.createWeldContainer(); //NOSONAR
      weldContainer.select(applicationClass).get().run(arguments);
   }

   private DropwizardWeldHelper() {
      // No public constructor
   }
}