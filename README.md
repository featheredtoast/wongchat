# sente-websockets-rabbitmq

Yet another chat app.

A chat app example combining clojure, clojurescript, rabbitmq, reagent, and oauth.

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

## Development

Open a terminal and type `lein repl` to start a Clojure REPL
(interactive prompt).

In the REPL, type

```clojure
(run)
(browser-repl)
(sente-websockets-rabbitmq.server/start-workers!)
```

`(start-workers!)` boots up the connection to rabbitmq and starts sente for websocket connections

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
