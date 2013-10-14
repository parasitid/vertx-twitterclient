/*
 * Example Groovy integration test that deploys the module that this project builds.
 *
 * Quite often in integration tests you want to deploy the same module for all tests and you don't want tests
 * to start before the module has been deployed.
 *
 * This test demonstrates how to do that.
 */

import static org.vertx.testtools.VertxAssert.*

// And import static the VertxTests script
import org.vertx.groovy.testtools.VertxTests;

// The test methods must being with "test"

def testSignin() {
  container.logger.info("in testPing()")
  println "vertx is ${vertx.getClass().getName()}"
  vertx.eventBus.send("twitter-signin", [callbackURL:"http://www.google.fr"]) { reply ->
      def authURLString = reply.body().authenticationURL
      assertNotNull( authURLString )

      assertTrue( authURLString.startsWith( "http://api.twitter.com/oauth/authenticate?oauth_token=" ) )
      
      testComplete()
  }
}

// Make sure you initialize
VertxTests.initialize(this)

// The script is execute for each test, so this will deploy the module for each one
// Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
// don't have to hardecode it in your tests

config = [:]

config["oauth.consumerKey"] = "pp0vof1Tr2aT1G3gqw0tw"
config["oauth.consumerSecret"] = "VQMDG9qOT9WHqv9HPwVaX9KhLRiMSn1FFpTSGtFQ"

container.deployModule(System.getProperty("vertx.modulename"), config ){ asyncResult ->
  // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
  assertTrue(asyncResult.succeeded)
  assertNotNull("deploymentID should not be null", asyncResult.result())
  // If deployed correctly then start the tests!
  VertxTests.startTests(this)
}