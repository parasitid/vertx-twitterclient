# Vert.x Twitter Client

This verticle is a simple twitter client

By now, 10/14/13, oauth signin is implemented. post statuses and query will soon be added.

Please clone this and add the unit tests & doc for me ;)

For a start, edit the conf.json, add your twitter app consumer key & secret, then try a 

    ./gradlew copyMod
    export VERTX_MODS=./build/mods
    vertx runmod mobocomu~twitter-verticle~0.1-SNAPSHOT -conf ./conf.json -cluster

oh yeah, i guess you already have a running vertx install, or else you wouldn't be here, would you ? 

somewhere else in another shell, edit/modify/play with the script "src/test/resources/twitter-http.groovy", then run it : 

    vertx run src/test/resources/twitter-http.groovy -cluster

and try this url in your browser : "http://localhost:8080/twitter/signin"

you should be redirected to twitter to authorize your twitter app ( according to your consumer key, right? ), and then redirected again to a beautiful page rendering your home timeline in json.