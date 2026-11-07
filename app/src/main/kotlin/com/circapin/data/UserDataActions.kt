package com.circapin.data

import java.io.File

interface UserDataActions {
    suspend fun export(csv: Boolean): File
    suspend fun deleteAll()
}
