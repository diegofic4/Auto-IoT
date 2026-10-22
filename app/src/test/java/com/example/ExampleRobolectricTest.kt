package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("OT Shield Monitor", appName)
  }

  @Test
  fun `verify all visual palettes are defined and have valid tokens`() {
    val palettes = com.example.ui.theme.AppPalette.entries
    assertEquals(6, palettes.size)
    palettes.forEach { palette ->
      val colors = com.example.ui.theme.getPaletteColors(palette)
      org.junit.Assert.assertNotNull(colors)
      org.junit.Assert.assertEquals(palette, colors.palette)
    }
  }
}
