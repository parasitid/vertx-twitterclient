import org.vertx.groovy.core.http.RouteMatcher

def server = vertx.createHttpServer()

def routeMatcher = new RouteMatcher()

server.requestHandler(routeMatcher.asClosure()).listen(8080, "localhost")

routeMatcher.get("/twitter/signin") { req ->
    def request = [action:"signin", callbackURL:"http://localhost:8080/twitter/callback"]

    vertx.eventBus.send("twitter-client", request ) { reply ->
      def authURLString = reply.body().authenticationURL

      def id = java.util.UUID.randomUUID().toString()
      saveRequestToken( id, reply.body() )
      
      req.response.putHeader("Location", authURLString )
      req.response.putHeader("Set-Cookie", "sessionid=${id}; Path=/;")
      req.response.setStatusCode(302)
      req.response.end()
    }
}

routeMatcher.get("/twitter/callback") { req ->
    def sessionId = req.headers["Cookie"]?.split(';').collect{ it.trim() }.find{ it.startsWith("sessionid=") }?.substring("sessionid=".size())
    
    try{ 
        java.util.UUID.fromString(sessionId)
    } catch ( Exception e ){ 
        req.response.setStatusCode(403)
        req.response.end "you snuffer boy"
    }

    def requestToken = loadRequestToken( sessionId )
    
    def request = [action:"callback", oauthVerifier:req.params["oauth_verifier"], requestToken: requestToken]

    vertx.eventBus.send("twitter-client", request) { reply ->
        deleteRequestToken(sessionId)
        saveAccessToken(sessionId, reply.body())

        req.response.putHeader("Location", "/twitter/timeline" )
        req.response.setStatusCode(302)
        req.response.end()
    }

}

routeMatcher.get("/twitter/timeline") { req ->
    def sessionId = req.headers["Cookie"]?.split(';').collect{ it.trim() }.find{ it.startsWith("sessionid=") }?.substring("sessionid=".size())
    
    try{ 
        java.util.UUID.fromString(sessionId)
    } catch ( Exception e ){ 
        req.response.setStatusCode(403)
        req.response.end "you snuffer boy"
    }

    def accessToken = loadAccessToken( sessionId )
    def requestTimeline = [ action:"homeTimeline", accessToken: loadAccessToken(sessionId) ]

    vertx.eventBus.send("twitter-client", requestTimeline){ reply -> 
        req.response.end String.valueOf( reply.body() )
    }

}

def loadRequestToken( id ){
    container.logger.info("load request token for id ${id}")
    requestTokenFromJsonString(vertx.sharedData.getMap("twitter.requestTokens").get(id))
}

def deleteRequestToken( id ){
    container.logger.info("delete request token for id ${id}")
    requestTokenFromJsonString(vertx.sharedData.getMap("twitter.requestTokens").remove(id))
}

def saveRequestToken(id, requestToken){ 
    container.logger.info("save request token ${requestToken} for id ${id}")
    vertx.sharedData.getMap("twitter.requestTokens").put(id, requestTokenToJsonString(requestToken) )
}

def loadAccessToken(id){ 
    container.logger.info("load access token for id ${id}")
    accessTokenFromJsonString(vertx.sharedData.getMap("twitter.accessTokens").get(id))
}

def deleteAccessToken(id){ 
    container.logger.info("delete access token for id ${id}")
    accessTokenFromJsonString(vertx.sharedData.getMap("twitter.accessTokens").remove(id))
}

def saveAccessToken(id, accessToken){ 
    container.logger.info("save access token ${accessToken} for id ${id}")
    vertx.sharedData.getMap("twitter.accessTokens").put(id, accessTokenToJsonString(accessToken) )
}

requestTokenFromJsonString = { String stringToken ->
    println "json:${stringToken}"
    def jsonToken = new groovy.json.JsonSlurper().parseText(stringToken)
    [token: jsonToken.token, tokenSecret: jsonToken.tokenSecret]
}

requestTokenToJsonString = { token ->
    def map = [token:token.token, tokenSecret:token.tokenSecret]
    String jsonString = new groovy.json.JsonBuilder(map).toString()
    return jsonString
}

accessTokenFromJsonString = { String stringToken ->
    def jsonToken = new groovy.json.JsonSlurper().parseText(stringToken)
    [token: jsonToken.token, tokenSecret: jsonToken.tokenSecret, userId: jsonToken.userId]
}

accessTokenToJsonString = { token ->
    def map = [token:token.token, tokenSecret:token.tokenSecret, userId:token.userId]
    return new groovy.json.JsonBuilder(map).toString()
}
