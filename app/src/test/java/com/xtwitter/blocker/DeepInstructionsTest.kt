package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeepInstructionsTest {

    private fun findAndFilterInstructions(obj: JSONObject, engine: SpamFilterEngine): Boolean {
        var modified = false
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "instructions") {
                val instructions = obj.optJSONArray(key)
                if (instructions != null) {
                    if (filterInstructions(instructions, engine)) {
                        modified = true
                    }
                }
            } else {
                val childObj = obj.optJSONObject(key)
                if (childObj != null) {
                    if (findAndFilterInstructions(childObj, engine)) {
                        modified = true
                    }
                }
            }
        }
        return modified
    }

    private fun filterInstructions(instructions: JSONArray, engine: SpamFilterEngine): Boolean {
        // filter logic
        return instructions.length() > 0
    }

    @Test
    fun testDeepHomeTimelineUrtInstructionsFound() {
        val homeJson = JSONObject("""
        {
          "data": {
            "home": {
              "home_timeline_urt": {
                "instructions": [
                  {
                    "type": "TimelineAddEntries",
                    "entries": []
                  }
                ]
              }
            }
          }
        }
        """)

        val engine = SpamFilterEngine.instance
        val modified = findAndFilterInstructions(homeJson.getJSONObject("data"), engine)
        assertTrue("Should find instructions inside home.home_timeline_urt", modified)
    }
}
