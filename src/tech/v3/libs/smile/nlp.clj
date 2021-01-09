(ns tech.v3.libs.smile.nlp
  (:require [clojure.string :as str]
            [pppmap.core :as ppp]
           [tfidf.tfidf :as tfidf]
            [tech.v3.dataset :as ds])
  (:import smile.nlp.normalizer.SimpleNormalizer
           smile.nlp.stemmer.PorterStemmer
           [smile.nlp.tokenizer SimpleSentenceSplitter SimpleTokenizer]
           [smile.nlp.dictionary EnglishStopWords]
           ))


(defn resolve-stopwords [stopwords-option]
  (if (keyword? stopwords-option)
    (iterator-seq (.iterator (EnglishStopWords/valueOf (str/upper-case (name stopwords-option)))))
    stopwords-option))

(defn word-process [^PorterStemmer stemmer ^SimpleNormalizer normalizer ^String word]
  (let [

        ]
    (-> word
        (str/lower-case)
        (#(.normalize normalizer %))
        (#(.stem stemmer %)))))



(defn default-text->bow [text options]
  "Converts text to token counts (a map token -> count).
   Takes an option `stopwords` being either a keyword naming a
   default Smile dictionary (:default :google :comprehensive :mysql)
   or a seq of stop words."
  (let [normalizer (SimpleNormalizer/getInstance)
        stemmer (PorterStemmer.)
        stopwords-option (:stopwords options)
        stopwords  (resolve-stopwords stopwords-option)
        processed-stop-words (map #(word-process stemmer normalizer %)  stopwords)
        tokenizer (SimpleTokenizer. )
        sentence-splitter (SimpleSentenceSplitter/getInstance)
        freqs
        (->> text
             (.normalize normalizer)
             (.split sentence-splitter)
             (map #(.split tokenizer %))
             (map seq)
             flatten
             (remove nil?)
             (map #(word-process stemmer normalizer % ))
             frequencies
             )]
    (apply dissoc freqs processed-stop-words)))

(defn default-tokenize [text options]
  "Converts text to token counts (a map token -> count).
   Takes an option `stopwords` being either a keyword naming a
   default Smile dictionary (:default :google :comprehensive :mysql)
   or a seq of stop words."
  (let [normalizer (SimpleNormalizer/getInstance)
        stemmer (PorterStemmer.)
        tokenizer (SimpleTokenizer. )
        sentence-splitter (SimpleSentenceSplitter/getInstance)
        tokens
        (->> text
             (.normalize normalizer)
             (.split sentence-splitter)
             (map #(.split tokenizer %))
             (map seq)
             flatten
             (remove nil?)
             (map #(word-process stemmer normalizer % ))
             )]
    tokens
    ))


(defn ->vocabulary-top-n [bows n]
  "Takes top-n most frequent tokens"
  (let [vocabulary
        (->>
         (apply merge-with + bows)
         (sort-by second)
         reverse
         (take n)
         keys)
        vocab->index-map (zipmap vocabulary (range))]

    {:vocab vocabulary
     :vocab->index-map vocab->index-map
     :index->vocab-map (clojure.set/map-invert vocab->index-map)
     }))

(defn count-vectorize
  ([ds text-col bow-col text->bow-fn options]
   "Converts text column `text-col` to bag-of-words representation
   in the form of a frequency-count map"
   (ds/add-or-update-column
    ds
    (ds/new-column
     bow-col
     (ppp/ppmap-with-progress
      "text->bow"
      1000
      #(text->bow-fn % options)
      (get ds text-col)))))
  ([ds text-col bow-col text->bow-fn]
   (count-vectorize ds text-col bow-col text->bow-fn {})
   )
  )

(defn tfidf-vectorize
  ([ds text-col bow-col text->bow-fn options]
   "Converts text column `text-col` to bag-of-words representation
   in the form of a frequency-count map"
   (ds/add-or-update-column
    ds
    (ds/new-column
     bow-col
     (ppp/ppmap-with-progress
      "text->tfidf"
      1000
      #(default-tokenize % options)
      (get ds text-col)))))
  ([ds text-col bow-col text->bow-fn]
   (tfidf-vectorize ds text-col bow-col text->bow-fn {})
   )
  )


(defn bow->something-sparse [ds bow-col indices-col vocab-size bow->sparse-fn]
  "Converts a bag-of-word column `bow-col` to a sparse data column `indices-col`.
   The exact transformation to the sparse representtaion is given by `bow->sparse-fn`"
  (let [vocabulary (->vocabulary-top-n (get ds bow-col) vocab-size)
        vocab->index-map (:vocab->index-map vocabulary)
        ds
        (vary-meta ds assoc
                   :count-vectorize-vocabulary vocabulary)]
    (ds/add-or-update-column
     ds
     (ds/new-column
      indices-col
      (ppp/ppmap-with-progress
       "bow->sparse"
       1000
       #(bow->sparse-fn % vocab->index-map)
       (get ds bow-col))))))


(comment

  (defn get-dataset []
    (->
     (ds/->dataset "test/data/reviews.csv.gz" {:key-fn keyword })
     (ds/select-columns [:Text :Score])
     (ds/update-column :Score #(map dec %))))

  (def data
    (->
     (get-dataset)
     (tfidf-vectorize  :Text :tokens default-tokenize)))
  (tfidf/tfidf (:tokens data))
  )
(default-tokenize "this is clojure" {})
