package app.keeply.reminders

import org.slf4j.LoggerFactory
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shows notifications using whatever the current desktop provides.
 *
 * macOS gets a real Notification Centre banner through the system's own scripting
 * bridge. Windows and Linux use the system tray, which is what a Java application
 * has there. Where neither works, Keeply says so rather than pretending: the home
 * screen still shows what needs attention, which is the part that matters.
 */
public class DesktopNotifier(private val applicationName: String = "Keeply", private val platform: Platform = Platform.current()) :
    Notifier {
    private val log = LoggerFactory.getLogger(DesktopNotifier::class.java)

    public enum class Platform {
        MAC_OS,
        WINDOWS,
        LINUX,
        UNKNOWN,
        ;

        public companion object {
            public fun current(): Platform {
                val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
                return when {
                    os.contains("mac") || os.contains("darwin") -> MAC_OS
                    os.contains("win") -> WINDOWS
                    os.contains("nux") || os.contains("nix") -> LINUX
                    else -> UNKNOWN
                }
            }
        }
    }

    private val trayIcon: TrayIcon? by lazy {
        if (platform == Platform.MAC_OS || !SystemTray.isSupported()) return@lazy null
        runCatching {
            val image = BufferedImage(TRAY_ICON_SIZE, TRAY_ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(image, applicationName).apply {
                isImageAutoSize = true
                SystemTray.getSystemTray().add(this)
            }
        }.onFailure { log.debug("No system tray available", it) }.getOrNull()
    }

    override val isAvailable: Boolean
        get() = when (platform) {
            Platform.MAC_OS -> true
            else -> trayIcon != null
        }

    override fun notify(title: String, body: String): Boolean = when (platform) {
        Platform.MAC_OS -> showOnMac(title, body)

        else -> trayIcon?.let {
            it.displayMessage(title, body, TrayIcon.MessageType.INFO)
            true
        } ?: false
    }

    /**
     * Asks macOS to show a notification.
     *
     * The text comes from a receipt, so it is untrusted. A product name containing
     * a quote or a backslash would otherwise close the script's string literal and
     * let the rest of the name run as code. Arguments go straight to the
     * interpreter rather than through a shell, and every string is escaped and
     * stripped of the characters that could terminate one.
     */
    private fun showOnMac(title: String, body: String): Boolean = try {
        val script = "display notification ${quote(body)} with title ${quote(title)}"
        val process = ProcessBuilder("/usr/bin/osascript", "-e", script)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            log.warn("Notification timed out")
            false
        } else {
            process.exitValue() == 0
        }
    } catch (e: Exception) {
        log.warn("Could not show a notification", e)
        false
    }

    internal companion object {
        const val TIMEOUT_SECONDS = 5L
        private const val MAX_LENGTH = 200
        private const val TRAY_ICON_SIZE = 16
        private const val MIN_PRINTABLE = 0x20
        private const val DELETE_CHARACTER = 0x7F

        /**
         * Renders text as a script string literal that cannot escape itself.
         *
         * Backslashes and quotes are escaped, and control characters are removed
         * rather than encoded, because a notification is one line and nothing
         * legitimate needs them.
         */
        fun quote(text: String): String {
            val cleaned = text.take(MAX_LENGTH)
                .filter { it.code >= MIN_PRINTABLE && it.code != DELETE_CHARACTER }
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return "\"" + cleaned + "\""
        }
    }
}
