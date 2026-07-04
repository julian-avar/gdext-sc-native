package example.feature_showcase.showcase.memory_mangement

import com.`julian-avar`.gdext.core.*
import com.`julian-avar`.gdext.generated.*
import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.{malloc, free}

/** Manual native memory — explicit allocate / deallocate
  *
  * Animated procedural texture generated from a manually-managed pixel buffer.
  *
  * This example demonstrates the full lifecycle of a native memory buffer:
  *   1. malloc — allocate a heap block that lasts beyond a single stack frame
  *   2. Write — fill it with raw pointer writes inside a while-loop
  *   3. Pass — hand it (eventually) to a Godot batch API such as Image.createFromData /
  *      Image.setData (once the low-level path lands; currently the example falls back to per-pixel
  *      setPixel to show the pixel data)
  *   4. free — release the block when the owning node exits the scene tree
  *
  * The low-level generated API (Phase 3) will let you pass a Ptr[Byte] directly to Image/Texture
  * constructors without creating a PackedByteArray wrapper, making batch upload a single ptrcall
  * and eliminating the Zone requirement. Until then, pixel-by-pixel setPixel calls are used for
  * illustration.
  */
@gdclass class ProceduralTextureExample extends Sprite2D:
    private val W = 128
    private val H = 128

    // FORMAT_RGBA8 (Godot Image format constant = 5) → 4 bytes per pixel.
    private val scratch = malloc(W * H * 4).asInstanceOf[Ptr[Byte]]

    override def ready(): Unit =
        // ── fill scratch with pseudo-random noise via pointer writes ──
        var i = 0
        while i < W * H * 4 do
            scratch(i) = ((i * 1103515245 + 12345) & 0xff).toByte
            i += 1
        end while

        // ── create a Godot Image and fill pixel by pixel ──
        val img = Image.create(W, H, false, 5) // FORMAT_RGBA8
        var y   = 0
        while y < H do
            var x = 0
            while x < W do
                val noise = (scratch(y * W * 4 + x * 4) & 0xff).toFloat / 255f
                val r     = (x.toFloat / W.toFloat + noise * 0.3f).min(1f)
                val g     = (y.toFloat / H.toFloat + noise * 0.3f).min(1f)
                val b     = noise
                img.setPixel(x, y, Color(r, g, b, 1f))
                x += 1
            end while
            y += 1
        end while

        val tex = ImageTexture.createFromImage(img)
        setTexture(tex)
    end ready

    override def exitTree(): Unit = free(scratch.asInstanceOf[Ptr[Byte]])
end ProceduralTextureExample
