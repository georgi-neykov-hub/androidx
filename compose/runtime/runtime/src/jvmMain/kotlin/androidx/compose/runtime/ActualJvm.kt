/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.runtime

actual typealias AtomicReference<V> = java.util.concurrent.atomic.AtomicReference<V>

internal actual open class ThreadLocal<T> actual constructor(
    private val initialValue: () -> T
) : java.lang.ThreadLocal<T>() {
    @Suppress("UNCHECKED_CAST")
    actual override fun get(): T {
        return super.get() as T
    }

    actual override fun set(value: T) {
        super.set(value)
    }

    override fun initialValue(): T? {
        return initialValue.invoke()
    }
}

internal actual typealias WeakHashMap<K, V> = java.util.WeakHashMap<K, V>

internal actual fun identityHashCode(instance: Any?): Int = System.identityHashCode(instance)

internal actual inline fun <R> synchronized(lock: Any, block: () -> R): R {
    kotlin.synchronized(lock) {
        return block()
    }
}

internal actual typealias TestOnly = org.jetbrains.annotations.TestOnly
