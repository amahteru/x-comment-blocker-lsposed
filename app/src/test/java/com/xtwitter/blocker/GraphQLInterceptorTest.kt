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

    @Test
    fun testNoteTweetLongSpamFiltered() {
        val json = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    {
                      "entryId": "tweet-long-spam",
                      "content": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "legacy": {
                                "full_text": "这是一个长推文前缀 https://t.co/abc"
                              },
                              "note_tweet": {
                                "note_tweet_results": {
                                  "result": {
                                    "text": "万达广场附近寻找男大弟弟，提供各种无偿福利"
                                  }
                                }
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

        val filtered = GraphQLInterceptor.filterJsonResponse(json, engine)
        val root = JSONObject(filtered)
        val entries = root.getJSONObject("data")
            .getJSONObject("threaded_conversation_with_injections_v2")
            .getJSONArray("instructions")
            .getJSONObject(0)
            .getJSONArray("entries")

        assertEquals(0, entries.length())
    }

    @Test
    fun testTimelineAddToModuleRepliesFiltered() {
        val json = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddToModule",
                  "moduleItems": [
                    {
                      "entryId": "module-reply-1",
                      "item": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "legacy": {
                                "full_text": "正常的回复内容"
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "entryId": "module-reply-2-spam",
                      "item": {
                        "itemContent": {
                          "itemType": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "legacy": {
                                "full_text": "同城约，点击主页查看"
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

        val filtered = GraphQLInterceptor.filterJsonResponse(json, engine)
        val root = JSONObject(filtered)
        val moduleItems = root.getJSONObject("data")
            .getJSONObject("threaded_conversation_with_injections_v2")
            .getJSONArray("instructions")
            .getJSONObject(0)
            .getJSONArray("moduleItems")

        assertEquals(1, moduleItems.length())
        assertEquals("module-reply-1", moduleItems.getJSONObject(0).getString("entryId"))
    }

    @Test
    fun testCountValidCommentEntriesAndExtractBottomCursor() {
        val json = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    { "entryId": "conversationthread-1", "content": {} },
                    { "entryId": "conversationthread-2", "content": {} },
                    { "entryId": "promoted-3", "content": {} },
                    { "entryId": "cursor-bottom-12345", "content": { "value": "CURSOR_NEXT_PAGE_123", "cursorType": "Bottom" } }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val count = GraphQLInterceptor.countValidCommentEntries(json)
        val cursor = GraphQLInterceptor.extractBottomCursor(json)

        assertEquals(2, count)
        assertEquals("CURSOR_NEXT_PAGE_123", cursor)
    }

    @Test
    fun testMergeTimelineResponses() {
        val page1Json = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    { "entryId": "tweet-normal-1", "content": {} },
                    { "entryId": "cursor-bottom-page1", "content": { "value": "CURSOR_PAGE_2", "cursorType": "Bottom" } }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val page2Json = """
        {
          "data": {
            "threaded_conversation_with_injections_v2": {
              "instructions": [
                {
                  "type": "TimelineAddEntries",
                  "entries": [
                    { "entryId": "tweet-normal-2", "content": {} },
                    { "entryId": "tweet-normal-3", "content": {} },
                    { "entryId": "cursor-bottom-page2", "content": { "value": "CURSOR_PAGE_3", "cursorType": "Bottom" } }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val merged = GraphQLInterceptor.mergeTimelineResponses(page1Json, page2Json)
        val count = GraphQLInterceptor.countValidCommentEntries(merged)
        val cursor = GraphQLInterceptor.extractBottomCursor(merged)

        assertEquals(3, count)
        assertEquals("CURSOR_PAGE_3", cursor)

        val root = JSONObject(merged)
        val entries = root.getJSONObject("data")
            .getJSONObject("threaded_conversation_with_injections_v2")
            .getJSONArray("instructions")
            .getJSONObject(0)
            .getJSONArray("entries")

        assertEquals(4, entries.length()) // 3 tweets + 1 bottom cursor
        assertEquals("tweet-normal-1", entries.getJSONObject(0).getString("entryId"))
        assertEquals("tweet-normal-2", entries.getJSONObject(1).getString("entryId"))
        assertEquals("tweet-normal-3", entries.getJSONObject(2).getString("entryId"))
        assertEquals("cursor-bottom-page2", entries.getJSONObject(3).getString("entryId"))
    }
}

