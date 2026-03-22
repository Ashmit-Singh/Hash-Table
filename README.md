# Hash Table Fundamentals & Implementation

> 10 real-world problems, each on its own branch, implemented in Java.

## Branch Map

| Branch | Problem | Key Concepts |
|--------|---------|--------------|
| `p1/username-checker` | Social Media Username Availability | HashMap basics, O(1) lookup, frequency counting |
| `p2/flash-sale-inventory` | E-commerce Flash Sale Inventory | ConcurrentHashMap, atomic ops, LinkedHashMap waitlist |
| `p3/dns-cache` | DNS Cache with TTL | Custom Entry class, TTL expiry, LRU eviction |
| `p4/plagiarism-detector` | Plagiarism Detection | N-grams, string hashing, Jaccard similarity |
| `p5/analytics-dashboard` | Real-Time Analytics Dashboard | Multiple HashMaps, PriorityQueue top-N |
| `p6/rate-limiter` | Distributed Rate Limiter | Token bucket, time-based ops, thread safety |
| `p7/autocomplete` | Autocomplete System | Trie + HashMap hybrid, min-heap top-K |
| `p8/parking-lot` | Parking Lot (Open Addressing) | Linear probing, tombstones, custom hash fn |
| `p9/transaction-analyzer` | Two-Sum Financial Variants | Complement lookup, K-Sum, duplicate detection |
| `p10/multi-level-cache` | Multi-Level Cache (L1/L2/L3) | LRU LinkedHashMap, promotion, invalidation |

## Quick Start

```bash
# Clone and run any branch
git clone https://github.com/Ashmit-Singh/Hash-Table.git
cd Hash-Table

# Switch to a problem branch
git checkout p1/username-checker

# Compile and run (Java 17+)
cd src
javac UsernameChecker.java && java UsernameChecker
```

## Concepts Covered

- **Hash table basics**: key-value mapping, O(1) average lookup/insert/delete
- **Collision resolution**: chaining (separate lists) vs open addressing (linear probing)
- **Load factor**: when to resize/rehash (default: 0.75 for Java's HashMap)
- **Frequency counting**: `merge()` pattern, top-K with min-heaps
- **Thread safety**: `ConcurrentHashMap`, `AtomicInteger`, `synchronized` blocks
- **Time-based operations**: TTL expiry, sliding windows, token buckets
- **Hybrid structures**: Trie + HashMap, LinkedHashMap for LRU

## Java Classes Used

| Class | Use Case |
|-------|----------|
| `HashMap<K,V>` | General-purpose O(1) key-value store |
| `LinkedHashMap<K,V>` | Insertion/access-order map (LRU caches) |
| `ConcurrentHashMap<K,V>` | Thread-safe concurrent access |
| `HashSet<E>` | Unique element membership testing |

## Running All Problems

```bash
# Use the setup script to create all branches automatically
chmod +x setup_branches.sh
./setup_branches.sh
```
