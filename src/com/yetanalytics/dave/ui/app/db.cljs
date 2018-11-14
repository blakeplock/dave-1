(ns com.yetanalytics.dave.ui.app.db
  "Handle top-level app state & persistence"
  (:require [re-frame.core :as re-frame]
            [clojure.spec.alpha :as s]
            [com.yetanalytics.dave.ui.app.nav :as nav]
            [cognitect.transit :as t]
            [com.yetanalytics.dave.workbook :as workbook]
            [com.yetanalytics.dave.ui.interceptor :as i]
            [com.cognitect.transit.types :as ty]
            [com.yetanalytics.dave.util.spec :as su])
  (:import [goog.storage Storage]
           [goog.storage.mechanism HTML5LocalStorage]))

;; Make transit UUIDs work with the uuid? spec pred
(extend-type ty/UUID
  cljs.core/IUUID)

;; Persistence
(defonce w (t/writer :json))
(defonce r (t/reader :json))

(defonce storage
  (Storage. (HTML5LocalStorage.)))

(defn storage-get
  "Get a value from storage, if it exists deserialize it from transit."
  [k]
  (when-let [data-str (.get storage k)]
    (t/read r data-str)))

(defn storage-set
  "Set a value in storage, serializing it to transit."
  [k v]
  (.set storage k (t/write w
                           v)))

(defn storage-remove
  "Remove a value from storage"
  [k]
  (.remove storage k))

;; compose state specs
(s/def ::id uuid?)
(s/def ::nav nav/nav-spec)

;; Install an index on workbook
;; These are not present in the base spec so workbooks can stand on their own.
(s/def ::workbook/index
  su/index-spec)

(s/def ::workbooks
  (s/and (s/map-of ::workbook/id
                   (s/merge workbook/workbook-spec
                            (s/keys :req-un [::workbook/index])))
         (comp su/sequential-indices? vals)))

(def db-state-spec
  (s/keys :opt-un [::id
                   ::nav
                   ::workbooks
                   ]))

;; This will include the default workbooks for DAVE
(def db-default
  {:workbooks {#uuid "f1d0bd64-0868-43ec-96c6-a51c387f5fc8"
               {:id #uuid "f1d0bd64-0868-43ec-96c6-a51c387f5fc8"
                :title "Test Workbook 1"
                :description "A dummy workbook for dev/testing"
                :index 0
                :questions {#uuid "344d1296-bb19-43f5-92e5-ceaeb7089bb1"
                            {:id #uuid "344d1296-bb19-43f5-92e5-ceaeb7089bb1"
                             :text "What content is completed the most?"
                             :index 0
                             :visualizations
                             {#uuid "c9d0e0c2-3d40-4c5d-90ab-5a482588459f"
                              {:id #uuid "c9d0e0c2-3d40-4c5d-90ab-5a482588459f"
                               :index 0}}}}}
               #uuid "958d2e94-ffdf-441f-a42c-3754cac04c71"
               {:id #uuid "958d2e94-ffdf-441f-a42c-3754cac04c71"
                :title "Test Workbook 2"
                :description "Another dummy workbook for dev/testing"
                :index 1
                :questions {#uuid "4e285a1c-ff7f-4de9-87bc-8ab346ffedea"
                            {:id #uuid "4e285a1c-ff7f-4de9-87bc-8ab346ffedea"
                             :text "Who are my best performers?"
                             :index 0
                             :visualizations
                             {#uuid "8cd6ea72-08d0-4d8e-8547-032d6a340a0b"
                              {:id #uuid "8cd6ea72-08d0-4d8e-8547-032d6a340a0b"
                               :index 0}}}}}}})

(s/def ::saved
  (s/keys :req-un [::workbooks]))

(s/fdef load-cofx
  :args (s/cat :cofx
               map?)
  :ret (s/keys ::opt-un [::saved]))

(defn- load-cofx [cofx]
  (let [saved (storage-get "dave.ui.db")]
    (cond
      (and (not-empty saved)
           (s/valid? db-state-spec saved))
      (assoc cofx :saved saved)

      (some? saved) ;; it must not be valid. Let's delete it
      (do
        (.warn js/console "DB invalid! %o" saved)
        (s/explain db-state-spec saved)
        (storage-remove "dave.ui.db")
        cofx)
      ;; otherwise, doesn't add any cofx
      :else cofx)))

(re-frame/reg-cofx
 ::load
 load-cofx)

(s/fdef save!-fx
  :args (s/cat :db-state db-state-spec))

(defn- save!-fx
  [db-state]
  (storage-set "dave.ui.db"
               (dissoc db-state
                       ;; Dissoc ID
                       :id
                       ;; Dissoc nav, as this would force navigation in
                       ;; multi-tab situations
                       :nav
                       ;; Don't save dave.debug state, as it might be huge
                       :debug)))
(re-frame/reg-fx
 :db/save!
 save!-fx)

(re-frame/reg-fx
 :db/destroy!
 (fn [_]
   (storage-remove "dave.ui.db")))

(re-frame/reg-event-fx
 :db/init
 [(re-frame/inject-cofx ::load)
  i/persist-interceptor]
 (fn [{:keys [saved
              db] :as ctx} [_ instance-id]]
   {:db (merge
         db ;; merge DB so it works with reset!
         saved
         (when-not saved
           (.log js/console "Creating new DAVE ui DB...")
           db-default)
         {:id instance-id})}))

(re-frame/reg-event-fx
 :db/reset!
 (fn [{{:keys [id]} :db
       :as ctx} _]
   {:db/destroy! true
    :dispatch [:db/init id]}))

;; Top-level sub for form-2 subs
(re-frame/reg-sub
 :dave/db
 (fn [db _]
   db))

(re-frame/reg-sub
 :db/debug
 (fn [db _]
   db))

(re-frame/reg-sub
 :db/transit-str
 (fn [db _]
   (t/write w db)))

(re-frame/reg-sub
 :db/edn-str
 (fn [db _]
   (pr-str db)))