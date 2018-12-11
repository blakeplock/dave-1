(ns com.yetanalytics.dave.ui.app.workbook.question.func
  (:require [re-frame.core :as re-frame]
            [com.yetanalytics.dave.func :as func]))


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