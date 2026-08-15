package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GraphQLInterceptorTest {

    private lateinit var engine: SpamFilterEngine

    @Before
    fun setup() {
        engine = SpamFilterEngine()
        engine.isEnabled = true
        engine.isBlockPromoted = true
        engine.isCheckUsername = true
        engine.updateKeywords(
            cloudKeywords = "万达广场\n同城约\n男大弟弟\n福利群",
            userKeywords = "微信号\nvx:"
        )
    }

    @Test
    fun testGraphQLFilterRemovesSpamRepliesAndPromoted() {
        val sampleGraphQLResponse = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "entryId": "tweet-1001",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "core": {
                                "user_results": {
                                  "result": {
                                    "legacy": {
                                      "screen_name": "normal_dev",
                                      "name": "Normal Developer"
                                    }
                                  }
                                }
                              },
                              "legacy": {
                                "full_text": "这是一个非常有价值的技术讨论！"
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "entryId": "tweet-1002-spam",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "core": {
                                "user_results": {
                                  "result": {
                                    "legacy": {
                                      "screen_name": "bot_999",
                                      "name": "同城约 - 认证客服"
                                    }
                                  }
                                }
                              },
                              "legacy": {
                                "full_text": "万达广场附近的男大弟弟看过来，加微信号联系我"
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "entryId": "promoted-tweet-2001",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "promotedMetadata": {
                            "advertiserId": "ad_123"
                          },
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "legacy": {
                                "full_text": "Promoted Ad Tweet"
                              }
                            }
                          }
                        }
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val filteredJson = GraphQLInterceptor.filterJsonResponse(sampleGraphQLResponse, engine)

        // Parse resulting JSON
        val root = JSONObject(filteredJson)
        val entries = root.getJSONObject("data")
            .getJSONObject("threaded_conversation_with_injections_v2")
            .getJSONArray("instructions")
            .getJSONObject(0)
            .getJSONArray("entries")

        // Should only have 1 entry remaining: tweet-1001
        assertEquals(1, entries.length())

        val remainingEntry = entries.getJSONObject(0)
        assertEquals("tweet-1001", remainingEntry.getString("entryId"))
        val remainingText = remainingEntry.getJSONObject("content")
            .getJSONObject("itemContent")
            .getJSONObject("tweet_results")
            .getJSONObject("result")
            .getJSONObject("legacy")
            .getString("full_text")

        assertEquals("这是一个非常有价值的技术讨论！", remainingText)
        assertFalse(filteredJson.contains("tweet-1002-spam"))
        assertFalse(filteredJson.contains("promoted-tweet-2001"))
    }
}
