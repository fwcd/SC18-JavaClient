package com.thedroide.sc18.utils;

import java.util.Map;

/**
 * A map with ordered keys providing easy operations
 * to fetchs keys at a specific index.
 * 
 * @param <K> - The key type
 * @param <V> - The value type
 */
public interface IndexedMap<K, V> extends Map<K, V> {
	/**
	 * Adds or replaces a mapping.
	 * 
	 * @param index - The key index
	 * @param key - The key
	 * @param value - The associated value
	 * @return The previous value associated with the key or null if there was none
	 */
	V put(int index, K key, V value);
	
	/**
	 * Removes a mapping.
	 * 
	 * @param index - The index of the key
	 * @return The value of the mapping
	 */
	V remove(int index);
	
	/**
	 * Fetches a key by it's index.
	 * 
	 * @param index - The index
	 * @return The key
	 */
	K getKey(int index);
	
	default V setValue(int index, V value) {
		return put(index, getKey(index), value);
	}
	
	default V getValue(int index) {
		return get(getKey(index));
	}
}