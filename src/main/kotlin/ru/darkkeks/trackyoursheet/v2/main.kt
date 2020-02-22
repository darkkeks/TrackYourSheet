package ru.darkkeks.trackyoursheet.v2

import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import org.litote.kmongo.util.KMongoConfiguration
import org.slf4j.LoggerFactory
import ru.darkkeks.trackyoursheet.v2.sheet.*

val BOT_TOKEN: String = System.getenv("BOT_TOKEN") ?: ""

val kodein = Kodein {
    bind<NetHttpTransport>() with singleton { GoogleNetHttpTransport.newTrustedTransport() }
    bind<JsonFactory>() with singleton { JacksonFactory.getDefaultInstance() }
    bind<CredentialsUtil>() with singleton {
        CredentialsUtil(kodein)
    }

    bind<Sheets>() with singleton {
        val credentialsUtil: CredentialsUtil = instance()
        Sheets.Builder(instance(), instance(), credentialsUtil.getCredential())
            .setApplicationName("TrackYourSheet")
            .build()
    }

    bind<SheetApi>() with singleton {
        SheetApi(kodein)
    }

    bind<CoroutineDatabase>() with singleton {
        val credential = MongoCredential.createCredential("root", "admin", "root".toCharArray())
        val settings = MongoClientSettings.builder()
            .credential(credential).build()
        KMongo.createClient(settings).coroutine
            .getDatabase("track_your_sheet")
    }

    bind<SheetTrackRepository>() with singleton {
        SheetTrackRepository(kodein)
    }
}

suspend fun main() {
    val logger = LoggerFactory.getLogger("Main")
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("Unhandled exception on thread $t", e)
    }

    val module = SimpleModule()
        .addKeySerializer(Cell::class.java, CellKeySerializer())
        .addKeyDeserializer(Cell::class.java, CellKeyDeserializer())
    KMongoConfiguration.registerBsonModule(module)

    Controller(kodein).start()
}