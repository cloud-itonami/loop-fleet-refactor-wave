(ns loop-fleet-refactor-wave.mission-a
  "Candidate-discovery heuristics for Mission A: eliminate single-vendor,
   non-reconstructable primitives (Cloudflare D1, or any other single-vendor
   conditional-write / single-region SQL) used as a CAS/consensus/ref
   arbiter, from paths that self-identify as decentralized/blockchain.

   The acceptance criterion itself is NOT owned here -- it is
   superproject ADR-2608039000 (adr-2608039000-d1-is-not-a-premise-in-decentralized-paths).
   These functions only narrow a large repo set to a short candidate list;
   the operational test (\"if you delete this store right now, does data get
   lost or correctness break?\") is applied by a human/agent reading the
   real code, not automated here.

   Pure, portable across clj/cljs/nbb. No I/O -- callers read files and pass
   text in.")

(defn decentralization-claim?
  "True if `text` (e.g. a README) contains a self-claim of decentralization,
   using ADR-2608039000's own scoping words: 分散 / decentralized /
   blockchain. Per the ADR: 'D1 usage in a path that does not make this
   claim is out of scope, regardless of D1's role there.'"
  [text]
  (boolean (re-find #"(?i)(decentrali[sz]|blockchain|分散)" (or text ""))))

(defn d1-binding?
  "True if `wrangler-text` (the contents of a wrangler.jsonc/wrangler.toml)
   declares a D1 database binding."
  [wrangler-text]
  (boolean (re-find #"d1_databases" (or wrangler-text ""))))

(defn cas-arbiter-signal?
  "Heuristic: does `source-text` show signs of D1 being used as an
   ordering/consensus/head/ref arbiter, as opposed to ordinary application
   data storage? This is intentionally coarse -- it exists only to avoid
   flagging the common case (D1 as session/content data, ADR-2608039000's
   explicit out-of-scope majority) as a candidate. A positive match here is
   not itself proof of a premise violation; it is a reason to look closer."
  [source-text]
  (boolean
   (re-find #"(?i)(WHERE\s+sequence|onlyIf\.etagMatches|If-Match|head[_-]?db|conditional[_-]?write|cas[_-]?arbiter|ref[_-]?plane)"
            (or source-text ""))))

(defn mission-a-candidate?
  "A repo/path is a Mission A candidate only if it BOTH self-claims
   decentralization AND shows a D1-as-arbiter signal. Per ADR-2608039000
   Section 4 ('適用範囲 -- 全面禁止ではない'), D1 usage alone, without the
   decentralization self-claim, is out of scope. Most d1_databases bindings
   in this workspace are ordinary appview data and must not be flagged."
  [{:keys [readme-text wrangler-text source-text]}]
  (and (decentralization-claim? readme-text)
       (d1-binding? wrangler-text)
       (cas-arbiter-signal? source-text)))
