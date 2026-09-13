package app.keeply.services

import app.keeply.ai.OllamaAssistant
import app.keeply.backup.BackupReader
import app.keeply.backup.BackupWriter
import app.keeply.data.KeeplyDatabaseFactory
import app.keeply.data.KeeplyStore
import app.keeply.data.MetaRepository
import app.keeply.documents.DataDirectory
import app.keeply.documents.DocumentImporter
import app.keeply.documents.ImportLimits
import app.keeply.ocr.OcrService
import app.keeply.ocr.TessdataInstaller
import app.keeply.reminders.DesktopNotifier
import app.keeply.reminders.Notifier
import app.keeply.reminders.ReminderService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

/**
 * Everything Keeply is, assembled.
 *
 * One place that knows how the pieces fit together, so nothing else has to.
 * Opening this is the whole of Keeply's startup: a folder, a database, and a
 * handful of services that only touch the machine they are on.
 */
public class Keeply private constructor(
    public val directory: DataDirectory,
    public val store: KeeplyStore,
    public val imports: ImportService,
    public val purchases: PurchaseService,
    public val library: LibraryService,
    public val storage: StorageService,
    public val insights: InsightsService,
    public val demo: DemoData,
    public val reminders: ReminderService,
    public val backups: BackupService,
    public val assistant: OllamaAssistant,
    private val ocr: OcrService,
) : AutoCloseable {

    override fun close() {
        runCatching { ocr.close() }
        runCatching { assistant.close() }
        runCatching { store.close() }
    }

    public companion object {
        private val log = LoggerFactory.getLogger(Keeply::class.java)

        public const val VERSION: String = "1.0.0"

        /**
         * Opens, or creates, a Keeply library.
         *
         * The language model is unpacked next to the data on first run, so OCR works
         * offline from then on with nothing for the person to install.
         */
        public fun open(
            directory: DataDirectory = DataDirectory.default(),
            notifier: Notifier = DesktopNotifier(),
            clock: Clock = Clock.systemDefaultZone(),
            importLimits: ImportLimits = ImportLimits.DEFAULT,
        ): Keeply {
            directory.create()
            val store = KeeplyDatabaseFactory.open(
                directory.database.resolve(KeeplyDatabaseFactory.DATABASE_FILE_NAME),
            )
            store.categories.installDefaultsIfEmpty()
            if (store.meta.get(MetaRepository.CREATED_AT) == null) {
                store.meta.put(MetaRepository.CREATED_AT, Instant.now(clock).toString())
            }
            store.meta.put(MetaRepository.APP_VERSION, VERSION)

            val tessdata = TessdataInstaller.install(directory.root.resolve("ocr"))
            val ocr = OcrService(tessdata)
            val importer = DocumentImporter(directory, importLimits, clock)

            log.info("Keeply {} opened at {}", VERSION, directory.root)
            return Keeply(
                directory = directory,
                store = store,
                imports = ImportService(store, directory, importer, ocr, clock = clock),
                purchases = PurchaseService(store, clock),
                library = LibraryService(store, clock),
                storage = StorageService(store, directory),
                insights = InsightsService(store, clock),
                demo = DemoData(store, directory, clock),
                reminders = ReminderService(notifier, clock = clock),
                backups = BackupService(
                    store = store,
                    directory = directory,
                    writer = BackupWriter(directory, VERSION),
                    reader = BackupReader(),
                    appVersion = VERSION,
                    clock = clock,
                ),
                assistant = OllamaAssistant.disabled(),
                ocr = ocr,
            )
        }
    }
}
