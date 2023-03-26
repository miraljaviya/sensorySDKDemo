package com.example.sensorysdkdemo

import ai.sensorycloud.Initializer
import ai.sensorycloud.api.common.ServerHealthResponse
import ai.sensorycloud.api.v1.audio.GetModelsResponse
import ai.sensorycloud.api.v1.management.DeviceResponse
import ai.sensorycloud.interactors.AudioStreamInteractor
import ai.sensorycloud.service.AudioService
import ai.sensorycloud.service.HealthService
import ai.sensorycloud.service.HealthService.GetHealthListener
import ai.sensorycloud.service.OAuthService
import ai.sensorycloud.service.OAuthService.EnrollDeviceListener
import ai.sensorycloud.tokenManager.DefaultSecureCredentialStore
import ai.sensorycloud.tokenManager.TokenManager
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.util.*


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val clientID = "21a060d7-b134-4a5a-a1ff-e28b9b4ad755"
        val clientSecret: String = generateRandomToken()

// Use the credential ID to store multiple sets of client credentials using the same credential store.
// While the class itself is not static, all instances of DefaultCredentialStore will have access to your saved credentials.

// Use the credential ID to store multiple sets of client credentials using the same credential store.
// While the class itself is not static, all instances of DefaultCredentialStore will have access to your saved credentials.
       /* val credentialStore = DefaultSecureCredentialStore(this, "default")
        try {
            credentialStore.setCredentials(clientID, clientSecret)
        } catch (e: Exception) {
            Log.e("xxx", "exception ${e.message}")
            // Handle error with saving credentials (ensure your device has a secure enclave)
        }

        try {
            val retrievedClientID = credentialStore.clientId
            val retrievedClientSecret = credentialStore.clientSecret
        } catch (e: Exception) {
            Log.e("xxx", "exception ${e.message}")
            // Handle error with retrieving credentials
        }
*/
        val credentialStore = DefaultSecureCredentialStore(this, "default")
        val oAuthService = OAuthService(credentialStore)

        val fileStream: InputStream = this.javaClass.classLoader.getResourceAsStream("SensoryCloudConfig.ini")

        Log.e("xxx", "file stream ${fileStream.read()}")
        
        Initializer.initialize(
            oAuthService,
            null,  // JWT signer class, only used when enrollmentType is `jwt`
            fileStream,
            "21a060d72",  // Optional override for deviceID, useful when sharing config files across multiple devices
            "sandeep",  // Optional override for deviceName, useful when sharing config files across multiple devices
            object : EnrollDeviceListener {
                override fun onSuccess(response: DeviceResponse) {
                    Log.e("xxx", "on success $response")
                    // SDK has been successfully initialized and the device has been enrolled
                    // `response` may be null if the device has previously been enrolled

                    val healthService = HealthService()
                    healthService.getHealth(object : GetHealthListener {
                        override fun onSuccess(serverHealthResponse: ServerHealthResponse) {
                            Log.e("xxx", "health service $serverHealthResponse")
                            // Process health response

                            createAudioService(oAuthService)
                        }

                        override fun onFailure(t1: Throwable) {
                            Log.e("xxx", "health service error ${t1.message}")

                            // Server error occurred
                        }
                    })
                }

                override fun onFailure(t: Throwable) {
                    Log.e("xxx", "on failure1111 ${t.message}")
                    // Handle error during SDK initialization
                }
            }
        )



    }

    private fun createAudioService(oAuthService: OAuthService) {
        val tokenManager = TokenManager(oAuthService)
        val OAuthToken = tokenManager.accessToken
        Log.e("xxx", "auth token $OAuthToken")
        val audioService = AudioService(tokenManager)

        if (ContextCompat.checkSelfPermission(
                this,
                RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), 100)
            // request audio permissions
            return
        }

        try {
            val interactor = AudioStreamInteractor.newAudioStreamInteractor(this)
        } catch (e: Exception) {
            // Handle error (may be due to not having audio record permissions)
        }


        audioService.getModels(object : AudioService.GetModelsListener {
            override fun onSuccess(response: GetModelsResponse) {
                response.getModelsList()
                Log.e("xxx", "model list ${response.modelsList}")
            }

            override fun onFailure(t: Throwable) {
                Log.e("xxx", "on failure ${t.message}")
                // Handle server error
            }
        })
    }

    private fun generateRandomToken(): String {
        return UUID.randomUUID().toString()
    }
}