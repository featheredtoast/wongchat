(ns wongchat.router)

(def routes ["/"
             [["" :index]
              ["chat" [["" :chat]
                       [["/channel/" :channel] :chat]]]
              ["subscribe" :subscribe]
              ["unsubscribe" :unsubscribe]
              ["logout" :logout]]])
