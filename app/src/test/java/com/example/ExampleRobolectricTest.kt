package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.drive.DriveBackupError
import com.example.drive.GoogleDriveBackupRepository
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
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
}
