(ns reagent-serverside.core
  (:require [clojure.string :as str]
            [hiccup.core :refer [html]]
            [clojure.zip :as zip]))

(def ^:private base 65521)
(defn- adler32 [data]
  (let [a (atom 1)
        b (atom 0)
        i (atom 0)
        l (count data)
        m (bit-and l -4)]
    (while (< @i m)
      (let [n (min (+ @i 4096) m)]
        (while (< @i n)
          (swap! b + (+
                      (swap! a + (int (.charAt data @i)))
                      (swap! a + (int (.charAt data (+ @i 1))))
                      (swap! a + (int (.charAt data (+ @i 2))))
                      (swap! a + (int (.charAt data (+ @i 3))))))
          (swap! a mod base)
          (swap! b mod base)
          (swap! i + 4))))
    (while (< @i l)
      (swap! b + (swap! a + (int (.charAt data @i))))
      (swap! i inc))
    (swap! a mod base)
    (swap! b mod base)
    (bit-or @a (unchecked-int (bit-shift-left @b 16)))))


(defn- react-id-str [react-id]
  (assert (vector? react-id))
  (str "." (clojure.string/join "." react-id)))

(defn- set-react-id [react-id element]
  (let [element (-> element
                    (update 1 merge {:data-reactid (react-id-str react-id)}))]
    (assoc element 1
           (->> (second element)
                (filter #(nil? (re-matches #"^on.*" (name (% 0)))))
                (into {})))))

(defn normalize [component]
  (if (map? (second component))
    component
    (into [(first component) {}] (rest component))))

(defn hiccup-zip
  "Returns a zipper for hiccup vectors"
  [root]
  (clojure.zip/zipper #(or (vector? %) (seq? %))
          seq
          (fn [node children]
            (if (keyword? (first children))
              (with-meta (vec children) (meta node))
              (with-meta children (meta node))))
          root))

(defn render
  ([component] (render [0] component))
  ([id component]
   (cond
     (fn? component)
     (render (component))

     (not (coll? component))
     component

     (coll? (first component))
     (map-indexed #(render (conj (into [] (butlast id)) (str "$" %1)) %2) component)

     (keyword? (first component))
     (let [[tag opts & body] (normalize component)]
       ;; (println id tag)
       (->> body
            (map-indexed #(render (conj id %1) %2))
            (into [tag opts])
            (set-react-id id)))

     (fn? (first component))
     (render id (apply (first component) (rest component))))))

(defn render-new [component]
  (let [root (hiccup-zip (render component))]
    (loop [node root
           react-id 1]
      (if (clojure.zip/end? node)
        (clojure.zip/root node)
        (if (map? (first node))
          (recur (clojure.zip/next (clojure.zip/replace node (into {} (assoc (first node) :data-reactid react-id)))) (inc react-id))
          (recur (clojure.zip/next node) react-id))))))

(defn render-page [page]
  (let [rendered (render-new page)
        fully-rendered (reorder-keys (update rendered 1 merge {:data-reactroot "" :data-react-checksum (adler32 (html rendered))}))]
    (println fully-rendered)
    fully-rendered))
