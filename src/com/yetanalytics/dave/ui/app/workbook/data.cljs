(ns com.yetanalytics.dave.ui.app.workbook.data
  (:require [re-frame.core :as re-frame]
            [com.yetanalytics.dave.workbook.data :as data]
            [com.yetanalytics.dave.workbook.data.state :as state]
            [com.yetanalytics.dave.workbook.data.lrs.client
             :as lrs-client]
            [clojure.core.async :as a :include-macros true]
            [clojure.spec.alpha :as s]
            [goog.string :refer [format]]
            [goog.string.format]
            ))


(re-frame/reg-event-db
 ::set-state
 (fn [db
      [_
       workbook-id
       new-state
       force?]]
   (if force?
     (assoc-in db
               [:workbooks
                workbook-id
                :data
                :state]
               new-state)
     (update-in db
              [:workbooks
               workbook-id
               :data
               :state]
              (fnil
               (partial max-key :statement-idx)
               {:statement-idx -1})
              new-state))))

(s/fdef fetch-fx
  :args (s/cat :data data/data-spec)
  :ret (s/nilable map?))

(defmulti fetch-fx (fn [_ data _]
                     (:type data)))

(defmethod fetch-fx :default [_ _ _] {})

(defmethod fetch-fx ::data/file
  [workbook-id
   {:keys [statements
           uri] :as data}
   _]
  (when-not statements
    {:http/request {:request {:url uri
                              :method :get}
                    :handler [::load
                              workbook-id
                              nil]}}))

(defmethod fetch-fx ::data/lrs
  [workbook-id
   {{:keys [statement-idx
            stored-domain]
     :as lrs-state} :state
    :as lrs-spec
    :or {lrs-state {:statement-idx -1}}}
   db]
  (let [fresh-lrs? (= lrs-state
                      {:statement-idx -1})
        [init-state
         lrs-chan]
        (if fresh-lrs?
          [lrs-state
           (lrs-client/query lrs-spec)]
          (let [st (apply min-key
                          :statement-idx
                          (cons lrs-state
                                (for [[_ {{:keys [state]} :function}]
                                      (get-in db [:workbooks
                                                  workbook-id
                                                  :questions])]
                                  state)))
                ?since (get-in st [:stored-domain 1])]
            [st
             (lrs-client/query (cond-> lrs-spec
                                 ?since
                                 (assoc-in [:query :since] ?since))
                               :statement-idx
                               (if (< -1 (:statement-idx st))
                                 (:statement-idx st)
                                 0))]))]
    (a/go-loop [state init-state]
      (if-let [[tag body] (a/<! lrs-chan)]
        (case tag
          :result
          (when-let [statements (some-> body
                                        (get "statements")
                                        not-empty)]
            (re-frame/dispatch
             [::load
              workbook-id
              (::lrs-client/statement-idx-range
               (meta body))
              {:status 200 :body statements}])
            (recur (state/update-state
                     state statements)))
          :exception
          (println "LRS EX" body))
        ;; When we reach the end, we (maybe) update the LRS state
        (re-frame/dispatch [::set-state
                            workbook-id
                            state])
        )))
  {})

(re-frame/reg-event-fx
 ::ensure
 (fn [{:keys [db] :as ctx} [_ workbook-id]]
   (let [data (get-in db [:workbooks
                          workbook-id
                          :data])]
     (fetch-fx workbook-id data db))))

#_(s/fdef load
  :args (s/cat :data data/data-spec
               :response map?)
  :ret data/data-spec)

#_(defmulti load (fn [data _]
                   (:type data)))

#_(defmethod load :default [_ _]
  {})

#_(defmethod load ::data/file
  [{:as data} {:keys [body] :as response}]
  (let [])
  (assoc data :statements body))

(re-frame/reg-event-fx
 ::clear-errors
 (fn [{:keys [db] :as ctx} [_ workbook-id]]
   {:db (update-in db [:workbooks
                       workbook-id
                       :data]
                   dissoc
                   :errors)}))

(re-frame/reg-event-fx
 ::load
 (fn [{:keys [db] :as ctx}
      [_ workbook-id
       idx-range
       {:keys [status body] :as response}]]
   #_(println "response" response)
   (let [{data-type :type
          data-state :state
          :as data} (get-in db
                            [:workbooks
                             workbook-id
                             :data])]
     (if (= 200 status)
       {:dispatch-n
        (cond-> [[:workbook.question.function/step-all!
                  workbook-id
                  idx-range
                  body]
                 [::clear-errors workbook-id]]

          (= ::data/file
             data-type)
          (conj [::set-state
                 workbook-id
                 (state/update-state
                  {:statement-idx -1}
                  body)
                 true]))}
       #_{:db (update-in db
                       [:workbooks
                        workbook-id
                        :data]
                       load
                       response)
        :dispatch [::clear-errors workbook-id]}
       {:db (update-in db
                       [:workbooks
                        workbook-id
                        :data
                        :errors]
                       (fnil conj [])
                       {:type ::load-error
                        :message "Couldn't load data."
                        :workbook-id workbook-id
                        :response response})}))))
(re-frame/reg-event-fx
 ::change
 (fn [{:keys [db] :as ctx}
      [_
       workbook-id
       data-spec]]
   {:db (assoc-in db
                  [:workbooks
                   workbook-id
                   :data]
                  data-spec)
    :dispatch-n [[:workbook.question.function/reset-all!
                  workbook-id
                  [::ensure workbook-id]]
                 ]}))

(re-frame/reg-event-fx
 ::check-lrs
 (fn [{:keys [db] :as ctx}
      [_
       lrs-spec
       dispatch-ok
       dispatch-error]]
   ;; Do an authenticated HEAD request
   {:http/request
    {:request {:url (str (:endpoint lrs-spec)
                         "/xapi/statements?limit=1")
               :headers {"X-Experience-Api-Version" "1.0.3"}
               :basic-auth (select-keys
                            (:auth lrs-spec)
                            [:username :password])
               :with-credentials? false
               :method :head}
     :handler dispatch-ok
     :error-handler dispatch-error}}))

(re-frame/reg-event-fx
 ::create-lrs
 (fn [{:keys [db] :as ctx}
      [_
       workbook-id
       lrs-data-spec
       ?check-resp]]
   (if-let [{:keys [success status] :as resp} ?check-resp]
     (if success
       ;; TODO: proceed with creation
       {:notify/snackbar
        {:message "Connecting LRS..."}
        :dispatch-n [[:dialog/dismiss]
                     [::change
                      workbook-id
                      lrs-data-spec]]}
       {:notify/snackbar
        {:message
         (format "LRS Error: %d"
                 status)
         #_(condp contains? status
           #{401 403} "Invalid Credentials"
           #{404} "Invalid Endpoint"
           (str ))}}
       )
     (if (s/valid? data/data-spec lrs-data-spec)
       {:dispatch [::check-lrs
                   lrs-data-spec
                   [::create-lrs
                    workbook-id
                    lrs-data-spec]]}
       {:notify/snackbar
        {:message "Invalid LRS Info!"}}))))

;; Picker/selection
(re-frame/reg-event-fx
 :workbook.data/offer-picker
 (fn [{:keys [db] :as ctx} [_
                            workbook-id]]
   {:dispatch [:picker/offer
               {:title "Choose a Data Source"
                :choices
                [{:label "DAVE Test Dataset"
                  :img-src ""
                  :dispatch [::change workbook-id
                             {:title "test dataset"
                              :type :com.yetanalytics.dave.workbook.data/file
                              :uri "data/dave/ds.json"
                              :state {:statement-idx -1}
                              :built-in? true}]}
                 {:label "LRS Data"
                  :img-src ""
                  :dispatch
                  [:dialog.form/offer
                   {:title "LRS Data Info"
                    :mode :com.yetanalytics.dave.ui.app.dialog/form
                    :fields [{:key :title
                              :label "LRS Name"}
                             {:key :endpoint
                              :label "LRS Endpoint"}
                             {:key [:auth :username]
                              :label "API Key"}
                             {:key [:auth :password]
                              :label "API Key Secret"}]
                    :form {:type :com.yetanalytics.dave.workbook.data/lrs
                           :built-in? false
                           ;; remove dummy vals
                           :title "My LRS"
                           :endpoint "http://localhost:9001"
                           :auth {:username "123456789"
                                  :password "123456789"
                                  :type :com.yetanalytics.dave.workbook.data.lrs.auth/http-basic}}
                    :dispatch-save [::create-lrs workbook-id]}]}]}]}))

(re-frame/reg-sub
 :workbook/data
 (fn [[_ ?workbook-id] _]
   (if (some? ?workbook-id)
     (re-frame/subscribe [:workbook/lookup ?workbook-id])
     (re-frame/subscribe [:workbook/current])))
 (fn [workbook _]
   (:data workbook)))

(re-frame/reg-sub
 :workbook.data/title
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook/data ?workbook-id]))
 (fn [data _]
   (:title data)))

(re-frame/reg-sub
 :workbook.data/type
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook/data ?workbook-id]))
 (fn [data _]
   (:type data)))

(re-frame/reg-sub
 :workbook.data/state
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook/data ?workbook-id]))
 (fn [data _]
   (:state data)))

(re-frame/reg-sub
 :workbook.data/errors
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook/data ?workbook-id]))
 (fn [{:keys [errors] :as data} _]
   errors))

(re-frame/reg-sub
 :workbook.data.state/statement-idx
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook.data/state ?workbook-id]))
 (fn [state _]
   (:statement-idx state -1)))

(re-frame/reg-sub
 :workbook.data.state/timestamp-domain
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook.data/state ?workbook-id]))
 (fn [state _]
   (:timestamp-domain state)))

(re-frame/reg-sub
 :workbook.data/statement-count
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook.data.state/statement-idx ?workbook-id]))
 (fn [statement-idx _]
   (inc statement-idx)))

(re-frame/reg-sub
 :workbook.data/timestamp-range
 (fn [[_ ?workbook-id] _]
   (re-frame/subscribe [:workbook.data.state/timestamp-domain ?workbook-id]))
 (fn [[mn mx :as timestamp-domain] _]
   {:min mn
    :max mx}))
