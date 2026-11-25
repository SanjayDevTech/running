package com.example.running.core.util

/** A map that keeps at most [maxEntries], dropping the least recently read or written entry. Not thread-safe. */
internal class LruMap<K, V>(private val maxEntries: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
}
