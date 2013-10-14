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

import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j.auth.RequestToken
import twitter4j.auth.AccessToken

/*
 * This is a simple verticle made to ease the twitter integration within a vertx app.
 * replies
 *
 * @author <a href="http://twitter.com/yanndegat">yanndegat</a>
 */
class TwitterVerticle extends Verticle {

    TwitterFactory twitterFactory = null


    def start() {
        container.logger.info("starting twitter mod")
        ConfigurationBuilder cb = new ConfigurationBuilder()
        cb.OAuthConsumerKey = container.config["oauth.consumerKey"]
        cb.OAuthConsumerSecret = container.config["oauth.consumerSecret"]

        cb.includeMyRetweetEnabled = false
        cb.includeRTsEnabled = false

        def twitterConfig = cb.build()

        twitterFactory = new TwitterFactory(twitterConfig)

        container.logger.info("[twitter_mod] registering twitter-signin handler")
        vertx.eventBus.registerHandler("twitter-signin", twitterSigninHandler )

        container.logger.info("[twitter_mod] registering twitter-callback handler")
        vertx.eventBus.registerHandler("twitter-callback", twitterCallbackHandler )

        container.logger.info("[twitter_mod] registering twitter-logout handler")
        vertx.eventBus.registerHandler("twitter-logout") { message ->
            accessTokens.remove(message.body().id)
        }
    }

    def twitterSigninHandler = { message ->
        Twitter twitter = twitterFactory.getInstance()

        StringBuffer callbackURL = new StringBuffer( message.body().callbackURL )
        int index = callbackURL.lastIndexOf('/')
        callbackURL.replace(index, callbackURL.length(), "").append("/callback")

        try{ 
            RequestToken requestToken = twitter.getOAuthRequestToken(callbackURL.toString())
            def id = java.util.UUID.randomUUID().toString()

            saveRequestToken( id, requestToken )

            message.reply( [id:id, authenticationURL:requestToken.authenticationURL] )
            container.logger.info("request token acquired for id ${id}")
        } catch (TwitterException e) {
            container.logger.error("failed to acquire request token", e)
            message.reply([ERR:e.message])
        }
    }

    def twitterCallbackHandler = { message -> 
        Twitter twitter = twitterFactory.getInstance()

        def id = message.body().id
        def oauthVerifier = message.body().oauthVerifier
        container.logger.info "[twitter_mod] retrieving access token for id ${id}"

        RequestToken requestToken = loadRequestToken(id)

        try {
            def accessToken = twitter.getOAuthAccessToken(requestToken, oauthVerifier)
            saveAccessToken(id, accessToken)
            message.reply([screenName:accessToken.screenName, userId:accessToken.userId])
            container.logger.info "[twitter_mod] access granted for id ${id}"
        } catch (TwitterException e) {
            container.logger.error("[twitter_mod] failed to validate request token and acquire an access token for id ${id}", e)
            message.reply([ERR:e.message])
        } finally { 
            // revoke request token
            deleteRequestToken(message.body().id)
        }
    }


    def loadRequestToken( id ){
        container.logger.info("[twitter_mod] load request token for id ${id}")
        requestTokenFromJsonString(vertx.sharedData.getMap("twitter.requestTokens").get(id))
    }

    def deleteRequestToken( id ){
        container.logger.info("[twitter_mod] delete request token for id ${id}")
        requestTokenFromJsonString(vertx.sharedData.getMap("twitter.requestTokens").remove(id))
    }

    def saveRequestToken(id, requestToken){ 
        container.logger.info("[twitter_mod] save request token ${requestToken} for id ${id}")
        vertx.sharedData.getMap("twitter.requestTokens").put(id, requestTokenToJsonString(requestToken) )
    }

    def loadAccessToken(id){ 
        container.logger.info("[twitter_mod] load access token for id ${id}")
        accessTokenFromJsonString(vertx.sharedData.getMap("twitter.accessTokens").get(id))
    }

    def deleteAccessToken(id){ 
        container.logger.info("[twitter_mod] delete access token for id ${id}")
        accessTokenFromJsonString(vertx.sharedData.getMap("twitter.accessTokens").remove(id))
    }

    def saveAccessToken(id, accessToken){ 
        container.logger.info("[twitter_mod] save access token ${accessToken} for id ${id}")
        vertx.sharedData.getMap("twitter.accessTokens").put(id, accessTokenToJsonString(accessToken) )
    }

    static RequestToken requestTokenFromJsonString( String stringToken ){ 
        def jsonToken = new groovy.json.JsonSlurper().parseText(stringToken)
        new RequestToken( jsonToken.token, jsonToken.tokenSecret )
    }

    static String requestTokenToJsonString( RequestToken token ){ 
        def map = [token:token.token, tokenSecret:token.tokenSecret]
        String jsonString = new groovy.json.JsonBuilder(map).toString()
        return jsonString
    }

    static AccessToken accessTokenFromJsonString( String stringToken ){ 
        def jsonToken = new groovy.json.JsonSlurper().parseText(stringToken)
        new AccessToken( jsonToken.token, jsonToken.tokenSecret, jsonToken.userId )
    }

    static String accessTokenToJsonString( AccessToken token ){ 
        return new groovy.json.JsonBuilder([token:token.token, tokenSecret:token.tokenSecret, userId:token.userId]).toString()
    }


}
