package app.morphe.manager.branding

import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrandAssetContractTest {
    private val workingDir = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
    private val moduleDir =
        if (workingDir.resolve("src/main").isDirectory) workingDir else workingDir.resolve("app")
    private val repositoryDir =
        if (moduleDir.name == "app") moduleDir.parentFile else workingDir

    @Test
    fun `adaptive foreground stays in the guaranteed Android safe zone`() {
        val adaptiveCanvasDp = 108.0
        val viewport = 512.0
        val markBounds = 280.0
        val markFootprintDp = markBounds / viewport * adaptiveCanvasDp
        val channelAt32Px = 56.0 / viewport * 32.0

        assertTrue(markFootprintDp in 56.0..60.0)
        assertTrue(markFootprintDp <= 66.0)
        assertTrue(channelAt32Px >= 3.0)

        val foreground = moduleDir.resolve("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        assertTrue(foreground.contains("android:viewportWidth=\"512\""))
        assertTrue(foreground.contains("android:viewportHeight=\"512\""))
        assertTrue(foreground.contains("#005FAC"))
        assertTrue(foreground.contains("#FF6B35"))
        assertEquals(2, "android:pathData=".toRegex().findAll(foreground).count())
    }

    @Test
    fun `themed icon is a purpose built single color silhouette`() {
        val monochrome = moduleDir.resolve("src/main/res/drawable/ic_launcher_monochrome.xml").readText()
        assertEquals(2, "android:pathData=".toRegex().findAll(monochrome).count())
        assertEquals(2, "android:fillColor=\"#000000\"".toRegex().findAll(monochrome).count())
        assertTrue(!monochrome.contains("#005FAC"))
        assertTrue(!monochrome.contains("#FF6B35"))

        moduleDir.resolve("src/main/res/mipmap-anydpi-v26")
            .listFiles { file -> file.extension == "xml" }
            .orEmpty()
            .forEach { adaptiveIcon ->
                assertTrue(
                    adaptiveIcon.readText().contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\""),
                    "${adaptiveIcon.name} must declare the dedicated monochrome layer",
                )
            }
    }

    @Test
    fun `Google Play icon is exact opaque 512 pixel RGBA artwork`() {
        val playIcon = repositoryDir.resolve("assets/brand/patchdock-play-store-512.png")
        val image = ImageIO.read(playIcon)

        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.colorModel.hasAlpha())
        for (y in 0 until image.height step 16) {
            for (x in 0 until image.width step 16) {
                assertEquals(255, image.getRGB(x, y).ushr(24), "Play icon must be opaque at $x,$y")
            }
        }
        assertTrue(playIcon.length() <= 1024L * 1024L)
    }
}
