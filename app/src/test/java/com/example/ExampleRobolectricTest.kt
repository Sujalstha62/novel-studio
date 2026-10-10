package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.GeminiGrammarChecker
import com.example.api.GrammarSuggestion
import com.example.drive.DriveBackupError
import com.example.drive.GoogleDriveBackupRepository
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Novel Writer", appName)
  }

  @Test
  fun `maps INTERNAL_ERROR with UNREGISTERED_ON_API_CONSOLE to MissingConfiguration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = GoogleDriveBackupRepository(context)
    val apiException = ApiException(
      Status(
        CommonStatusCodes.INTERNAL_ERROR,
        "[Unknown error [status=UNREGISTERED_ON_API_CONSOLE]]"
      )
    )

    val mapped = repo.mapToDriveBackupError(apiException, resultCode = 0, hasResultIntent = true)
    assertTrue("Expected MissingConfiguration but was ${mapped::class.java}", mapped is DriveBackupError.MissingConfiguration)
    assertTrue(mapped.message.contains("UNREGISTERED_ON_API_CONSOLE"))
    assertTrue(mapped.message.contains(context.packageName))
    assertTrue(mapped.message.contains("SHA-1"))
    assertTrue(mapped.message.contains("Google Drive API"))
  }

  @Test
  fun `maps nested cause containing UNREGISTERED_ON_API_CONSOLE to MissingConfiguration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = GoogleDriveBackupRepository(context)
    val nestedCause = IllegalStateException("INTERNAL_ERROR: 8: [Unknown error [status=UNREGISTERED_ON_API_CONSOLE]]")
    val wrapper = RuntimeException("Authorization failed", nestedCause)

    val mapped = repo.mapToDriveBackupError(wrapper)
    assertTrue("Expected MissingConfiguration but was ${mapped::class.java}", mapped is DriveBackupError.MissingConfiguration)
    assertTrue(mapped.message.contains("UNREGISTERED_ON_API_CONSOLE"))
    assertTrue(mapped.message.contains(context.packageName))
  }

  @Test
  fun `grammar checker blocks AI placeholders and marks them review-only`() {
    val checker = GeminiGrammarChecker()
    val manuscript = "The ancient gate was opened by the guard, and he recieve a letter."

    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", "[Active verb]"))
    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", "[Passive voice]"))
    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", "[Filter word: omit or replace]"))
    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", "[Correction]"))
    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", null))
    assertFalse(GeminiGrammarChecker.isUsableReplacementText("was opened", "   "))
    assertTrue(GeminiGrammarChecker.isUsableReplacementText("recieve", "received"))

    val rawJson = """
      [
        {
          "originalText": "was opened",
          "suggestedText": "[Active verb]",
          "explanation": "Passive voice phrasing.",
          "severity": "Style",
          "startOffset": 17,
          "endOffset": 27
        },
        {
          "originalText": "recieve",
          "suggestedText": "received",
          "explanation": "Spelling error.",
          "severity": "Typos",
          "startOffset": 49,
          "endOffset": 56
        }
      ]
    """.trimIndent()

    val validated = checker.parseAndValidateRawJson(manuscript, rawJson)
    assertEquals(2, validated.size)

    val passiveItem = validated[0]
    assertEquals("was opened", passiveItem.originalText)
    assertNull(passiveItem.suggestedText)
    assertFalse(passiveItem.hasUsableReplacement)
    assertEquals(17, passiveItem.startOffset)
    assertEquals(27, passiveItem.endOffset)

    val typoItem = validated[1]
    assertEquals("recieve", typoItem.originalText)
    assertEquals("received", typoItem.suggestedText)
    assertTrue(typoItem.hasUsableReplacement)
    assertEquals(49, typoItem.startOffset)
    assertEquals(56, typoItem.endOffset)
  }

  @Test
  fun `novelViewModel can be created via ViewModelProvider`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm1 = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
      .create(com.example.ui.NovelViewModel::class.java)
    val vm2 = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
      .create(com.example.ui.NovelViewModel::class.java)
    org.junit.Assert.assertNotNull(vm1)
    org.junit.Assert.assertNotNull(vm2)
  }
}
