(ns wongchat.router)

(def routes ["/"
             [["" :index]
              ["chat" [["" :chat-index]
                       [["/channel/" :channel] :chat]]]
              ["subscribe" :subscribe]
              ["unsubscribe" :unsubscribe]
              ["logout" :logout]]])
