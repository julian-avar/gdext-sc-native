package gdext.core

/** Structured logging to Godot's Output panel.
  *
  * All messages route through Godot's `print` utility function so they appear in the editor output
  * and in the project's log file alongside GDScript output.
  *
  * Call-by-name args ensure the message string is not evaluated when logging is disabled.
  *
  * Usage:
  * {{{
  * Log.info("PlayerSc ready")
  * Log.warn(s"speed=$speed out of range")
  * Log.error(s"null pointer at $pos")
  * }}}
  */
object Log:
    private var traceEnabled = false

    /** Enable/disable TRACE-level logging (very verbose). Off by default. */
    def setTraceEnabled(enabled: Boolean): Unit = traceEnabled = enabled

    /** Extremely verbose — method entry/exit, per-frame events. Off by default. */
    def trace(msg: => String): Unit = if traceEnabled then GdxApi.printString(s"[TRACE] $msg")

    /** Verbose diagnostic info for development. */
    def debug(msg: => String): Unit = GdxApi.printString(s"[DEBUG] $msg")

    /** Normal operational messages. */
    def info(msg: => String): Unit = GdxApi.printString(msg)

    /** Something unexpected but recoverable. */
    def warn(msg: => String): Unit = GdxApi.printString(s"[WARN]  $msg")

    /** A serious error that will likely cause incorrect behavior. */
    def error(msg: => String): Unit = GdxApi.printString(s"[ERROR] $msg")
end Log
