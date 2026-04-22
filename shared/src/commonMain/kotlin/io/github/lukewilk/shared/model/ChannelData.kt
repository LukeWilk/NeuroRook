package io.github.lukewilk.shared.model

/**
 * Couples a processed backend payload with the source channel that produced it.
 *
 * Preferred flow APIs use this wrapper so downstream consumers can distinguish
 * interleaved channel emissions without guessing from ordering alone.
 *
 * @property channelId Zero-based internal channel index that produced [payload].
 * @property payload Processed value emitted for [channelId].
 */
data class ChannelData<T>(val channelId: Int, val payload: T)


