package com.sadikbr.videowallpaper.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.MediaInfo
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.ui.home.HomeActions
import com.sadikbr.videowallpaper.ui.home.HomeScreen
import com.sadikbr.videowallpaper.ui.home.HomeState
import com.sadikbr.videowallpaper.ui.home.LockClockGuideContent
import com.sadikbr.videowallpaper.ui.studio.WallpaperStudio
import com.sadikbr.videowallpaper.ui.theme.WallpaperSlideshowTheme
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots of the main screens, rendered on the JVM with Paparazzi, to check layouts
 * without a phone. Record with `./gradlew recordPaparazziDebug`; images land in
 * `app/src/test/snapshots/images/`.
 */
class UiScreenshotTest {

    private object NoResults : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private val photoUri = "file:///sample/photo.png"

    private val secondUri = "file:///sample/second.png"

    private fun loaderWithSamplePhoto(): MediaInfoLoader {
        val bitmap = javaClass.classLoader!!.getResourceAsStream("sample_photo.png").use { BitmapFactory.decodeStream(it) }
        return MediaInfoLoader(paparazzi.context).apply {
            prime(photoUri, MediaInfo(MediaKind.IMAGE, "Violet Peaks", null, bitmap, available = true))
            prime(secondUri, MediaInfo(MediaKind.IMAGE, "Second photo", null, bitmap, available = true))
            prime("file:///sample/third.png", MediaInfo(MediaKind.IMAGE, "Third photo", null, bitmap, available = true))
        }
    }

    private val settings = WallpaperSettings(
        media = listOf(photoUri),
        clock = ClockSettings(enabled = true),
        widgets = WidgetSettings(weather = true, battery = true, calendar = true, signature = "Wallpaper Slideshow"),
    )

    @Test
    fun editor() = editorWithTool(0)

    @Test
    fun editorColor() = editorWithTool(1)

    @Test
    fun editorGlass() = editorWithTool(2)

    @Test
    fun editorWidgets() = editorWithTool(3)

    /** Two wallpapers: the item strip, and "Apply to all" in Options. */
    @Test
    fun editorTwoWallpapers() = editorWithTool(4, settings.copy(media = listOf(photoUri, secondUri)))

    @Test
    fun editorOptions() = editorWithTool(4)

    private fun editorWithTool(tool: Int, settings: WallpaperSettings = this.settings) {
        val loader = loaderWithSamplePhoto()
        paparazzi.snapshot {
            // No Activity here: a registry that ignores permission requests.
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoResults) {
                WallpaperSlideshowTheme {
                WallpaperStudio(
                    settings = settings,
                    startUri = photoUri,
                    loader = loader,
                    depthStates = emptyMap(),
                    systemUses24Hour = true,
                    onDone = {},
                    onDismiss = {},
                    initialTool = tool,
                )
                }
            }
        }
    }

    private val noActions = HomeActions(
        onAdd = {}, onEdit = {}, onRemove = {}, onMove = { _, _ -> }, onApply = {},
        onClockEverywhere = {}, onOpenSlideshowSettings = {}, builtInUri = { "" }, onToggleBuiltIn = {},
    )

    private fun home(settings: WallpaperSettings, loader: MediaInfoLoader) {
        paparazzi.snapshot {
            WallpaperSlideshowTheme {
                HomeScreen(
                    state = HomeState(
                        settings = settings,
                        wallpaperActive = true,
                        depthStates = emptyMap(),
                        depthProgress = emptyMap(),
                        widgetSnapshot = WidgetSnapshot(batteryPercent = 76),
                        systemUses24Hour = true,
                    ),
                    actions = noActions,
                    loader = loader,
                )
            }
        }
    }

    @Test
    fun homeWithWallpapers() = home(settings.copy(media = listOf(photoUri, secondUri, "file:///sample/third.png")), loaderWithSamplePhoto())

    /** The whole scrolling page, on a very tall screen. */
    @Test
    fun homeFullPage() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5200))
        home(settings.copy(media = listOf(photoUri, secondUri)), loaderWithSamplePhoto())
    }

    @Test
    fun lockClockGuide() {
        paparazzi.snapshot {
            WallpaperSlideshowTheme {
                androidx.compose.foundation.layout.Box(Modifier.background(androidx.compose.ui.graphics.Color(0xFF15122C))) {
                    LockClockGuideContent(onOpenSettings = {}, onDone = {}, onLater = {})
                }
            }
        }
    }

    @Test
    fun homeWithLockClockBanner() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 3000))
        paparazzi.snapshot {
            WallpaperSlideshowTheme {
                HomeScreen(
                    state = HomeState(
                        settings = settings,
                        wallpaperActive = true,
                        depthStates = emptyMap(),
                        depthProgress = emptyMap(),
                        widgetSnapshot = WidgetSnapshot(batteryPercent = 76),
                        systemUses24Hour = true,
                        showLockClockBanner = true,
                    ),
                    actions = noActions,
                    loader = loaderWithSamplePhoto(),
                )
            }
        }
    }

    @Test
    fun homeEmpty() = home(WallpaperSettings(), MediaInfoLoader(paparazzi.context))
}
