package ru.darkkeks.trackyoursheet.prototype.sheet

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.SheetsScopes
import org.kodein.di.Kodein
import org.kodein.di.generic.instance
import java.io.File
import java.util.*

class CredentialsUtil(kodein: Kodein) {
    private val credentialsFile = "credentials.json"
    private val tokensDirectory = "tokens"

    private val jsonFactory: JsonFactory by kodein.instance()
    private val httpTransport: NetHttpTransport by kodein.instance()

    fun getCredential(): Credential {
        val clientSecrets = GoogleClientSecrets.load(jsonFactory, File(credentialsFile).reader())

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jsonFactory,
            clientSecrets,
            Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)
        )
            .setDataStoreFactory(FileDataStoreFactory(File(tokensDirectory)))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }
}