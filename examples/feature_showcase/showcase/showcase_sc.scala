package example.feature_showcase

import com.`julian-avar`.gdext.core.*
import com.`julian-avar`.gdext.generated.*
import scala.scalanative.unsafe.*

// ── Scala enum → Godot ENUM dropdown ─────────────────────────────────────────
// The macro synthesises the hint string "Common,Uncommon,Rare,Epic,Legendary"
// and PropertyUsage.ClassIsEnum automatically — no boilerplate needed.
@gdenum
enum Rarity:
    case Common, Uncommon, Rare, Epic, Legendary

/** Feature showcase for gdext-sc-native.
  *
  * Every major feature implemented so far is exercised here:
  *   - Primary constructor @gdexport params with ExportHint
  *   - @export_category / @export_group for inspector sections
  *   - Enum → ENUM dropdown (no boilerplate)
  *   - ExportHint.colorNoAlpha, ExportHint.multiline, ExportHint.range
  *   - Tres[T] typed resource picker
  *   - GdArray[A] and GdDict[K,V] collections
  *   - @signal case class with typed params
  *   - @func methods returning math types and primitives
  *   - print(...) for output to the Godot Output panel
  *   - DefaultValue-based factory (primary ctor params need no default on Tres.empty)
  */
@gdclass class ShowcaseSc(
    @export_group("Base Stats") @gdexport(ExportHint.range(1, 200, 1)) var damage: Int = 10,
    @gdexport(ExportHint.range(0.0, 1.0, 0.05)) var critChance: Double = 0.15
) extends Node2D:

    @export_category("Appearance") @export_group("Color & Style") @gdexport(ExportHint.colorNoAlpha)
    var tintColor: Color         = Color(1f, 0.5f, 0.2f, 1f)
    @gdexport var rarity: Rarity = Rarity.Common
    @export_group("Description") @gdexport(ExportHint.multiline) var notes: String =
        "Edit me in the inspector."

    @export_category("References") @export_group("Resources") @gdexport
    var itemResource: Tres[Resource] = Tres.empty

    @export_category("Collections") @gdexport var tags: GdArray[String] = GdArray()

    // ── Signals with typed parameters ─────────────────────────────────────────
    // Godot shows the full signature in the Node → Signals dock.
    @signal case class StatsChanged(newDamage: Int, newCritChance: Float)
    @signal case class RarityUpgraded(from: Int, to: Int)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override def _ready(): Unit =
        print(s"ShowcaseSc._ready(): damage=$damage critChance=$critChance")
        print(s"  rarity ordinal: ${rarity.ordinal}")
        print(s"  tint: r=${tintColor.r} g=${tintColor.g} b=${tintColor.b}")

        // Demonstrate GdArray — populate if empty (editor may have cleared it)
        if tags.isEmpty then
            tags.append("melee")
            tags.append("magic")
            tags.append("ranged")
        end if
        print(s"  tags.size=${tags.size}  first='${tags(0)}'")

        // Demonstrate GdDict — local, not exported to inspector
        val scores = GdDict[String, Int]()
        scores("warrior") = damage
        scores("mage") = (damage * critChance * 3).toInt
        scores("rogue") = damage * 2
        print(s"  scores.size=${scores.size}")
        print(s"  warrior=${scores("warrior")}  mage=${scores("mage")}")

        // Emit the typed signal via the generated handle extension
        this.statsChanged.emitSignal(damage, critChance.toFloat)
        print("ShowcaseSc ready.")
    end _ready

    override def _process(delta: Double): Unit = ()

    // ── @func methods ─────────────────────────────────────────────────────────

    /** Returns current world position — @func returning Vector2. */
    @func def getOffset(): Vector2 = Zone { getPosition() }

    /** Damage scaled by a factor — @func with Float param and return. */
    @func def scaleDamage(factor: Float): Float = damage * factor

    /** Raw damage — simplest @func form. */
    @func def getDamage(): Int = damage

    /** Override tint from GDScript: `show_case.apply_tint(1.0, 0.2, 0.2)` */
    @func def applyTint(r: Float, g: Float, b: Float): Unit =
        tintColor = Color(r, g, b, 1f)
        print(s"tint set: r=$r g=$g b=$b")

    /** Advance rarity by one tier and fire rarityUpgraded. */
    @func def upgradeRarity(): Unit =
        val prev = rarity.ordinal
        if prev < Rarity.Legendary.ordinal then
            rarity = Rarity.fromOrdinal(prev + 1)
            this.rarityUpgraded.emitSignal(prev, rarity.ordinal)
            print(s"rarity upgraded: $prev → ${rarity.ordinal}")
        end if
    end upgradeRarity

end ShowcaseSc
