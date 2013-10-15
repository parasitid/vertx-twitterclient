/*
 *
 * this file is licensed to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 *
 */

package com.mobocomu.twitter

import org.vertx.groovy.platform.Verticle
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j.auth.RequestToken
import twitter4j.auth.AccessToken
import twitter4j.ResponseList
import twitter4j.User
import twitter4j.Status

/*
 * This is a simple verticle made to ease the twitter integration within a vertx app.
 * replies
 *
 * @author <a href="http://twitter.com/yanndegat">yanndegat</a>
 */
class TwitterVerticle extends BusModBase implements Handler<Message<JsonObject>> {

    TwitterFactory twitterFactory = null

    @Override void start() {
        container.logger().info("starting twitter mod")
        ConfigurationBuilder cb = new ConfigurationBuilder()
        Map<String,Object> config = container.config().toMap();

        cb.OAuthConsumerKey = config["oauth.consumerKey"]
        cb.OAuthConsumerSecret = config["oauth.consumerSecret"]
        cb.includeMyRetweetEnabled = false
        cb.includeRTsEnabled = false

        def twitterConfig = cb.build()
        twitterFactory = new TwitterFactory(twitterConfig)

        container.logger().info("registering twitter client to address: " + config[ "address"] );
        vertx.eventBus().registerHandler(config["address"], this);

    }


    @Override public void stop() {
        container.logger().info("twitter client verticle stopped.")
    }

    @Override public void handle(Message<JsonObject> message) {
        String action = getMandatoryString( "action", message );

        container.logger().debug("request:" + message.body().toString())

        if (action == null) {
            sendError(message, "action must be specified");
            return;
        }

        try {
            switch (action) {
            case "signin":
                signin(message);
                break;
            case "callback":
                signinCallback(message);
                break;
            case "homeTimeline":
                homeTimeline(message);
                break;
            case "updateStatus":
                throw new UnsupportedOperationException("query will soon be impl.");
            case "search":
                throw new UnsupportedOperationException("mapr will soon be impl.");
            default:
                sendError(message, "Invalid action: " + action);
            }
        } catch (Exception e) {
            container.logger().error(e, e);
            sendError(message, e.getMessage());
        }
    }

    private void signin( message ) { 
        Twitter twitter = twitterFactory.getInstance()

        StringBuffer callbackURL = new StringBuffer( message.body().getString("callbackURL") )
        int index = callbackURL.lastIndexOf('/')
        callbackURL.replace(index, callbackURL.length(), "").append("/callback")

        try{ 
            RequestToken requestToken = twitter.getOAuthRequestToken(callbackURL.toString())
            sendOK(message, requestTokenToJson(requestToken) )
            container.logger().info("request token acquired.")
        } catch (TwitterException e) {
            container.logger().error("failed to acquire request token: ${e.message}")
            container.logger().debug("failed to acquire request token", e)
            sendError( message, e.message)
        }
    }

    private void signinCallback( message ) { 
        Twitter twitter = twitterFactory.getInstance()

        RequestToken requestToken = requestTokenFromJson(message.body().getObject("requestToken"))
        def oauthVerifier = message.body().getString("oauthVerifier")

        try {
            def accessToken = twitter.getOAuthAccessToken(requestToken, oauthVerifier)
            
            sendOK(message, accessTokenToJson(accessToken) )
            container.logger().info "access granted for user ${accessToken.userId}"
        } catch (TwitterException e) {
            container.logger().error("failed to validate request token: ${e.message}")
            container.logger().debug("failed to validate request token", e)
            sendError( message, e.message)
        }
    }

    private void homeTimeline( message ) { 
        AccessToken accessToken = accessTokenFromJson(message.body().getObject("accessToken"))
        Twitter twitter = twitterFactory.getInstance(accessToken)

        try {
            
            JsonObject reply = new JsonObject()
            reply.putArray("statuses", statusesToJson(twitter.getHomeTimeline()))
            sendOK(message, reply )
            container.logger().info "homeTimeline retrieved for user ${accessToken.userId}"
        } catch (TwitterException e) {
            container.logger().error("failed to retrieve homeTimeline for user ${accessToken.userId}: ${e.message}")
            container.logger().debug("failed to retrieve homeTimeline for user ${accessToken.userId}", e)
            sendError( message, e.message)
        }
    }


    static JsonArray statusesToJson( ResponseList<Status> statuses ){ 
         statuses.inject(new JsonArray()){ arr, cur -> 
            arr.add( statusToJson(cur) )
        }
     }
    
    

    static JsonObject statusToJson( Status status ){ 
        JsonObject json = new JsonObject()
        
        json.putString("text", status.text )
        json.putBoolean("isRetweet", status.isRetweet() )
        json.putBoolean("isRetweetedByMe", status.isRetweetedByMe() )
        json.putNumber("id", status.id )
        json.putValue( "createdAt", status.createdAt )
        json.putObject("user", userToJson( status.user ))

        json
     }

    static JsonObject userToJson( User user ){ 
        JsonObject json = new JsonObject()
        json.putNumber("id", user.id)
        json.putString("name", user.name)
        json.putString("screenName", user.screenName)
        return json
    }


    static RequestToken requestTokenFromJson( JsonObject jsonToken ){ 
        new RequestToken( jsonToken.getString("token"), jsonToken.getString("tokenSecret") )
    }

    static JsonObject requestTokenToJson( RequestToken token ){ 
        JsonObject json = new JsonObject()
        json.putString("token", token.token)
        json.putString("tokenSecret", token.tokenSecret )
        json.putString("authenticationURL", token.authenticationURL )
        return json
    }

    static AccessToken accessTokenFromJson( JsonObject jsonToken ){ 
        new AccessToken( jsonToken.getString("token"), jsonToken.getString("tokenSecret"), jsonToken.getLong("userId") )
    }

    static JsonObject accessTokenToJson( AccessToken token ){ 
        JsonObject json = new JsonObject()
        json.putString("token", token.token)
        json.putString("tokenSecret", token.tokenSecret )
        json.putNumber("userId", token.userId )
        json.putString("screenName", token.screenName )
        json
    }


}
