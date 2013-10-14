# Vert.x Twitter Client

This verticle is a simple twitter client

Please clone this and add the unit tests & doc for me ;)

For a start, edit the conf.json, add your twitter app consumer key & secret, then try a 

./gradlew copyMod

export VERTX_MODS=./build/mods

vertx runmod mobocomu~twitter-verticle~0.1-SNAPSHOT -conf ./conf.json -cluster

oh yeah, i guess you already have a running vertx install, or else you wouldn't be here, would you ? 


somewhere else in another shell : 

edit a vertx groovy script "twitter-http.groovy" :
    import org.vertx.groovy.core.http.RouteMatcher

    def server = vertx.createHttpServer()

    def routeMatcher = new RouteMatcher()

    server.requestHandler(routeMatcher.asClosure()).listen(8080, "localhost")

    routeMatcher.get("/twitter/signin") { req ->

        vertx.eventBus.send("twitter-signin", [callbackURL:"http://localhost:8080/twitter/callback"]) { reply ->
          def authURLString = reply.body().authenticationURL
          req.response.putHeader("Set-Cookie", "twitterid=${reply.body().id}")//; Domain=localhost:8080; Path=/twitter")
          req.response.putHeader("Location", authURLString )
          req.response.setStatusCode(302)
          req.response.end()
        }


    }

    routeMatcher.get("/twitter/callback") { req ->
        def twitterId = req.headers["Cookie"].split(';').find{ it.startsWith("twitterid=") }.substring("twitterid=".size())

        try{ 
            java.util.UUID.fromString(twitterId)
        } catch ( Exception e ){ 
            req.response.setStatusCode(403)
            req.response.end "you snuffer boy"
        }

        vertx.eventBus.send("twitter-callback", [ id: twitterId, oauthVerifier:req.params["oauth_verifier"]]) { reply ->
            req.response.end "You requested cats"
        }
    }


and run this verticle : vertx run twitter-http.groovy -cluster

and try this url in your browser : "http://localhost:8080/twitter/signin"

you should be redirected to twitter to authorize your twitter app ( according to your consumer key, right? ), and then redirected again to a beautiful html page saying : "You requested cats"