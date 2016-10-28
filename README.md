# sente-websockets-rabbitmq

Yet another chat app.

A chat app example combining clojure, clojurescript, rabbitmq, postgresql, reagent, and oauth.

HTTP Sessions stored in redis.

This has now been refactored to work using components + system.

[Check it out!](https://sente-websockets-rabbitmq.herokuapp.com)

## Env vars

* oauth-callback: The oauth callback ($server/login)
* oauth-api-key: Google oauth api key
* oauth-api-secret: Google oauth api secret

### Heroku rabbit AMQP config

* rabbitmq-bigwig-rx-url: If deploying to heroku, the rabbitmq url path

### Alternate rabbit AMQP config

* amqp-host
* amqp-port
* amqp-user
* amqp-pass

### Postgres config

* db-host in the form `//server/schema`
* db-user
* db-pass

### Redis config

* redis-url in the form `redis://user:password@host:port`

### Webserver config

* url FQDN of the server root `http://site.com/`

## Development

Open a terminal and type `lein repl` to start a Clojure REPL
(interactive prompt).

In the REPL, type

```clojure
(run)
(browser-repl)
```

The call to `(run)` starts the Figwheel server at port 3449, which takes care of
live reloading ClojureScript code and CSS. Figwheel's server will also act as
your app server, so requests are correctly forwarded to the http-handler you
define.

Running `(browser-repl)` starts the Weasel REPL server, and drops you into a
ClojureScript REPL. Evaluating expressions here will only work once you've
loaded the page, so the browser can connect to Weasel.

When you see the line `Successfully compiled "resources/public/app.js" in 21.36
seconds.`, you're ready to go. Browse to `http://localhost:3449` and enjoy.

**Attention: It is not needed to run `lein figwheel` separately. Instead we
launch Figwheel directly from the REPL**

## Chestnut

Created with [Chestnut](http://plexus.github.io/chestnut/) 0.11.0 (3b671cf8).

## TODO

update local messages before sending

refactor/code cleanup (components?)

add reload, or startup/shutdown functions

core.async for message passing

connection pooling on postgres

cljc for common properties

login with github, twitter, other oauths?
