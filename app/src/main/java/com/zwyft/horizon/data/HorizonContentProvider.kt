package com.zwyft.horizon.data

import androidx.core.content.FileProvider

/**
 * FileProvider for sharing attachment files (MMS images, PDFs, etc.)
 * via system share intents. Paths configured in res/xml/file_paths.xml.
 */
class HorizonContentProvider : FileProvider()
