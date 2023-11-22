package org.openedx.core.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.openedx.core.R
import java.io.InputStreamReader

class Config(context: Context) {

    private var configProperties: JsonObject

    init {
        configProperties = try {
            val inputStream = context.resources.openRawResource(R.raw.config)
            val parser = JsonParser()
            val config = parser.parse(InputStreamReader(inputStream))
            config.asJsonObject
        } catch (e: Exception) {
            JsonObject()
        }
    }

    fun getFirebaseConfig(): FirebaseConfig {
        return getObjectOrNewInstance(FIREBASE, FirebaseConfig::class.java)
    }

    fun isWhatsNewEnabled(): Boolean {
        return getBoolean(WHATS_NEW_ENABLED, false)
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val element = getObject(key)
        return element?.asBoolean ?: defaultValue
    }

    private fun <T> getObjectOrNewInstance(key: String, cls: Class<T>): T {
        val element = getObject(key)
        return if (element != null) {
            val gson = Gson()
            gson.fromJson(element, cls)
        } else {
            try {
                cls.newInstance()
            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }
    }

    private fun getObject(key: String): JsonElement? {
        return configProperties.get(key)
    }

    companion object {
        private const val FIREBASE = "FIREBASE"
        private const val WHATS_NEW_ENABLED = "WHATS_NEW_ENABLED"
    }
}
