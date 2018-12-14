(ns com.yetanalytics.dave.ui.app.workbook.question.func
  (:require [re-frame.core :as re-frame]
            [com.yetanalytics.dave.func :as func]))

(re-frame/reg-event-fx
 :workbook.question.function/set-func!
 (fn [{:keys [db] :as ctx} [_
                            workbook-id
                            question-id
                            func-id
                            ?args]]
   (let [new-db (assoc-in db
                          [:workbooks
                           workbook-id
                           :questions
                           question-id
                           :function]
                          {:id func-id
                           :args (or ?args
                                     {})})]
     {:db new-db
      :db/save! new-db})))

;; Offer function picker
(re-frame/reg-event-fx
 :workbook.question.function/offer-picker
 (fn [{:keys [db] :as ctx} [_
                            workbook-id
                            question-id]]
   {:dispatch [:picker/offer
               {:title "Choose a DAVE Function"
                :choices (into []
                               (for [[id {:keys [title
                                                 doc]}] func/registry]
                                 {:label title
                                  :img-src "img/lambda.svg"
                                  :dispatch [:workbook.question.function/set-func!
                                             workbook-id
                                             question-id
                                             id]}))}]}))

;; Set an arg
(re-frame/reg-event-fx
 :workbook.question.function/set-arg!
 (fn [{:keys [db] :as ctx} [_
                            workbook-id
                            question-id
                            arg-key
                            arg-val :as call]]
   (let [new-db (assoc-in db
                          [:workbooks
                           workbook-id
                           :questions
                           question-id
                           :function
                           :args
                           arg-key]
                          arg-val)]
     {:db new-db
      :db/save! new-db
      })))

;; Subs
(re-frame/reg-sub
 :workbook.question/function
 (fn [[_ & args] _]
   (re-frame/subscribe (into [:workbook/question] args)))
 (fn [question _]
   (:function question)))

(defn function-sub-base
  [[_ & args] _]
  (re-frame/subscribe (into [:workbook.question/function] args)))

(re-frame/reg-sub
 :workbook.question.function/id
 function-sub-base
 (fn [function _]
   (:id function)))

(re-frame/reg-sub
 :workbook.question.function/args
 function-sub-base
 (fn [function _]
   (:args function)))

(re-frame/reg-sub
 :workbook.question.function/arg
 (fn [[_ workbook-id question-id arg-k] _]
   [(re-frame/subscribe [:workbook.question.function/args workbook-id question-id])
    (re-frame/subscribe [:workbook.question.function.func/args-default workbook-id question-id])])
 (fn [[args
       args-default] [_ _ _ arg-k]]
   (get (merge args-default
               args) arg-k)))

(re-frame/reg-sub
 :workbook.question.function/func
 (fn [[_ & args] _]
   (re-frame/subscribe (into [:workbook.question.function/id] args)))
 (fn [func-id _]
   (func/get-func func-id)))

(defn func-sub-base
  [[_ & args] _]
  (re-frame/subscribe (into [:workbook.question.function/func] args)))

(re-frame/reg-sub
 :workbook.question.function.func/title
 func-sub-base
 (fn [func _]
   (:title func)))

(re-frame/reg-sub
 :workbook.question.function.func/doc
 func-sub-base
 (fn [func _]
   (:doc func)))

(re-frame/reg-sub
 :workbook.question.function.func/args-enum
 func-sub-base
 (fn [func _]
   (:args-enum func)))

(re-frame/reg-sub
 :workbook.question.function.func/args-default
 func-sub-base
 (fn [func _]
   (:args-default func)))

(re-frame/reg-sub
 :workbook.question.function/result
 (fn [[_ ?workbook-id ?question-id] _]
   [(re-frame/subscribe [:workbook.data/statements ?workbook-id])
    (re-frame/subscribe [:workbook.question/function ?workbook-id ?question-id])])
 ;; TODO: this gets disposed if you navigate away, and thus it might be better
 ;; to do this with events and then cache the last result for a given fn.
 (fn [[statements
       {:keys [id args] :as function}] _]
   (when (seq statements)
     (func/apply-func id args statements))))

(re-frame/reg-sub
 :workbook.question.function.result/count
 (fn [[_ ?workbook-id ?question-id] _]
   (re-frame/subscribe [:workbook.question.function/result ?workbook-id ?question-id]))
 (fn [{:keys [values]} _]
   (count values)))
