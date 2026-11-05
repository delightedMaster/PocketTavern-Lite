package me.rerere.rikkahub.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导入酒馆预设时，名称和描述应优先使用 JSON 内的 name/description，
 * 缺失时才回退到文件名和默认文案。
 */
class PresetImportNameTest {

    private fun presetJson(extraHeader: String): String = """
    {
      $extraHeader
      "prompts": [
        { "identifier": "main", "name": "主要提示", "content": "你是{{char}}。", "role": "system" }
      ],
      "prompt_order": [
        {
          "character_id": 100001,
          "order": [
            { "identifier": "main", "enabled": true }
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `json name and description take priority over file name`() {
        val book = LorebookSerializer.importFromJson(
            presetJson("\"name\": \"JSON预设名\", \"description\": \"JSON描述\","),
            "文件名",
        )
        assertEquals("JSON预设名", book.name)
        assertEquals("JSON描述", book.description)
    }

    @Test
    fun `falls back to file name and default description when json has none`() {
        val book = LorebookSerializer.importFromJson(
            presetJson("\"name\": \"   \","),
            "文件名",
        )
        assertEquals("文件名", book.name)
        assertEquals("SillyTavern 预设导入", book.description)
    }
}
