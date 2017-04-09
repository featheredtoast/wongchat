(ns wongchat.router)

(def routes ["/"
             [["" :index]
              ["chat" :chat]
              ["subscribe" :subscribe]
              ["unsubscribe" :unsubscribe]
              ["logout" :logout]]])
