package com.janbean.sample.utils

class Storage(val name: String) {
    companion object {
        val credentialCache = Storage("credentialCache")
    }

    private val cache = HashMap<String, Any?>()

    fun<T> put(key: String, value: T?) {
        cache.put(key, value)
    }

    fun<T> get(key: String): T? {
        return cache[key] as? T
    }
}