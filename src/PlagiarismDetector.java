import java.util.*;
import java.util.stream.*;

/**
 * Problem 4: Plagiarism Detection System
 * Demonstrates: String hashing, n-gram extraction, frequency counting, Jaccard similarity
 */
public class PlagiarismDetector {

    // ── N-gram Index ──────────────────────────────────────────────────────────

    // ngram (string) → set of documentIds that contain it
    private final HashMap<String, Set<String>> ngramIndex = new HashMap<>();

    // documentId → its n-grams (for similarity calc)
    private final HashMap<String, List<String>> docNgrams = new HashMap<>();

    private static final int N = 5;   // 5-gram (tune: 5-7 is typical)
    private static final double SUSPICIOUS_THRESHOLD  = 10.0;
    private static final double PLAGIARISM_THRESHOLD  = 50.0;

    // ── Indexing ──────────────────────────────────────────────────────────────

    /**
     * Add a document to the index.
     * Breaks it into N-grams and stores them in the hash map.
     * O(n) where n = number of words.
     */
    public void indexDocument(String docId, String content) {
        List<String> ngrams = extractNgrams(content, N);
        docNgrams.put(docId, ngrams);

        for (String ng : ngrams) {
            ngramIndex
                    .computeIfAbsent(ng, k -> new HashSet<>())
                    .add(docId);
        }

        System.out.printf("Indexed \"%s\" → %d n-grams extracted%n", docId, ngrams.size());
    }

    // ── Analysis ──────────────────────────────────────────────────────────────

    /**
     * Analyze a new document against the index.
     * Returns a sorted list of SimilarityResult (most similar first).
     * O(n) where n = n-grams in the document.
     */
    public List<SimilarityResult> analyzeDocument(String docId, String content) {
        List<String> ngrams = extractNgrams(content, N);
        int total = ngrams.size();
        System.out.printf("%nanalyzeDocument(\"%s\") → Extracted %d n-grams%n", docId, total);

        // Count matching n-grams per other document using a HashMap
        HashMap<String, Integer> matchCounts = new HashMap<>();

        for (String ng : ngrams) {
            Set<String> docs = ngramIndex.get(ng);
            if (docs == null) continue;
            for (String other : docs) {
                if (!other.equals(docId)) {
                    matchCounts.merge(other, 1, Integer::sum);
                }
            }
        }

        // Build results
        List<SimilarityResult> results = new ArrayList<>();
        for (Map.Entry<String, Integer> e : matchCounts.entrySet()) {
            double similarity = 100.0 * e.getValue() / total;
            String verdict = similarity >= PLAGIARISM_THRESHOLD ? "PLAGIARISM DETECTED"
                    : similarity >= SUSPICIOUS_THRESHOLD ? "suspicious"
                    : "ok";
            results.add(new SimilarityResult(e.getKey(), e.getValue(), similarity, verdict));
        }

        results.sort(Comparator.comparingDouble(SimilarityResult::similarity).reversed());
        results.forEach(r ->
                System.out.printf("  → Found %d matching n-grams with \"%s\" → Similarity: %.1f%% (%s)%n",
                        r.matchingNgrams(), r.otherDocId(), r.similarity(), r.verdict()));

        return results;
    }

    // ── N-gram Extraction ─────────────────────────────────────────────────────

    private List<String> extractNgrams(String content, int n) {
        String[] words = content.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .trim()
                .split("\\s+");
        List<String> ngrams = new ArrayList<>();
        for (int i = 0; i <= words.length - n; i++) {
            ngrams.add(String.join(" ", Arrays.copyOfRange(words, i, i + n)));
        }
        return ngrams;
    }

    // ── Result Record ─────────────────────────────────────────────────────────

    public record SimilarityResult(String otherDocId, int matchingNgrams, double similarity, String verdict) {}

    // ── Demo ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        PlagiarismDetector detector = new PlagiarismDetector();

        System.out.println("=== Plagiarism Detection System ===\n");

        // Seed the index with existing documents
        detector.indexDocument("essay_089.txt",
                "The impact of artificial intelligence on modern society has been profound. " +
                "Machine learning algorithms are transforming industries across the globe. " +
                "Many researchers believe that AI will continue to reshape human civilization " +
                "in ways we cannot yet fully predict or comprehend fully.");

        detector.indexDocument("essay_092.txt",
                "Artificial intelligence has had a profound impact on modern society and human life. " +
                "Machine learning algorithms are transforming every industry across the world. " +
                "Many researchers believe that AI will reshape human civilization in unpredictable ways. " +
                "The ethical implications of these changes must be carefully considered by all stakeholders.");

        detector.indexDocument("essay_001.txt",
                "The French Revolution began in 1789 and fundamentally changed the political landscape " +
                "of Europe. The causes included financial crisis, social inequality, and political tensions. " +
                "The revolution led to the rise of Napoleon Bonaparte and had lasting effects on history.");

        System.out.println();

        // Analyze a suspicious submission
        String submission = "Artificial intelligence has had a profound impact on modern society. " +
                "Machine learning algorithms are transforming industries across the world. " +
                "Many researchers believe that AI will reshape human civilization in ways " +
                "we cannot yet fully predict. The ethical implications must be carefully considered.";

        detector.analyzeDocument("essay_123.txt", submission);

        // Benchmark: hash lookup vs linear search
        System.out.println("\n--- Performance Note ---");
        System.out.println("Hash-based n-gram lookup: O(1) per n-gram → O(n) total");
        System.out.println("Linear search over 100K docs: O(n × d) → orders of magnitude slower");
    }
}
