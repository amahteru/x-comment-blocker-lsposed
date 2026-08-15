package com.xtwitter.blocker

import com.xtwitter.blocker.engine.SpamFilterEngine
import com.xtwitter.blocker.hook.GraphQLInterceptor
import org.junit.Assert.*
import org.junit.Test

class ModernXCommentFilterTest {

    @Test
    fun testModernXSpamCommentsFiltered() {
        val modernJson = """
        {
          "data": {
            "timelineResponse": {
              "instructions": [
                {
                  "__typename": "TimelineAddEntries",
                  "entries": [
                    {
                      "entry_id": "tweet-normal-1",
                      "content": {
                        "__typename": "TimelineTimelineItem",
                        "content": {
                          "__typename": "TimelineTweet",
                          "tweet_results": {
                            "result": {
                              "__typename": "Tweet",
                              "core": {
                                "user_results": {
                                  "result": {
                                    "__typename": "User",
                                    "core": {
                                      "name": "Normal User",
                                      "screen_name": "normal_user"
                                    }
                                  }
                                }
                              },
                              "details": {
                                "full_text": "This is a great normal tweet about tech."
                              }
                            }
                          }
                        }
                      }
                    },
                    {
                      "entry_id": "tweetdetailrelatedtweets-spam-1",
                      "content": {
                        "__typename": "TimelineTimelineModule",
                        "items": [
                          {
                            "entry_id": "item-spam-1",
                            "item": {
                              "content": {
                                "__typename": "TimelineTweet",
                                "tweet_results": {
                                  "result": {
                                    "__typename": "Tweet",
                                    "core": {
                                      "user_results": {
                                        "result": {
                                          "__typename": "User",
                                          "core": {
                                            "name": "罗琦🌸同城上门♥线下选妃",
                                            "screen_name": "luoqi123"
                                          }
                                        }
                                      }
                                    },
                                    "details": {
                                      "full_text": "应该没人比我玩的开了吧😏🚑我福不黑不信你看"
                                    }
                                  }
                                }
                              }
                            }
                          },
                          {
                            "entry_id": "item-spam-2",
                            "item": {
                              "content": {
                                "__typename": "TimelineTweet",
                                "tweet_results": {
                                  "result": {
                                    "__typename": "Tweet",
                                    "core": {
                                      "user_results": {
                                        "result": {
                                          "__typename": "User",
                                          "core": {
                                            "name": "凌双🌸",
                                            "screen_name": "jotan213922"
                                          }
                                        }
                                      }
                                    },
                                    "details": {
                                      "full_text": "应该没人比我玩的更开了吧🤓🚄我福不黑不信你看"
                                    }
                                  }
                                }
                              }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
              ]
            }
          }
        }
        """.trimIndent()

        val engine = SpamFilterEngine.instance
        engine.isEnabled = true
        engine.isCheckUsername = true
        engine.updateKeywords("选妃\n同城上门\n我福不黑不信你看\n应该没人比我玩的开了吧", "")

        val filtered = GraphQLInterceptor.filterJsonResponse(modernJson, engine)

        assertFalse("Spam comment 1 should be removed", filtered.contains("罗琦🌸同城上门♥线下选妃"))
        assertFalse("Spam comment 2 should be removed", filtered.contains("凌双🌸"))
        assertFalse("Spam comment text should be removed", filtered.contains("我福不黑不信你看"))
        assertTrue("Normal tweet should be kept", filtered.contains("Normal User"))
    }
}
