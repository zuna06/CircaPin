package com.circapin

import kotlinx.coroutines.sync.Mutex

/** Serializes background updates with a user-requested reset. */
object DataGuard { val mutex = Mutex() }
