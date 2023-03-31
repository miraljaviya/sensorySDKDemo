package com.example.sensorysdkdemo

import ai.sensorycloud.Initializer
import ai.sensorycloud.api.common.ServerHealthResponse
import ai.sensorycloud.api.v1.audio.*
import ai.sensorycloud.api.v1.management.DeviceResponse
import ai.sensorycloud.interactors.AudioStreamInteractor
import ai.sensorycloud.interactors.TranscriptAggregator
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.protobuf.ByteString
import io.grpc.stub.StreamObserver
import java.io.InputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {
    private var txtHello: TextView? = null
    private lateinit var requestObserverTrans: StreamObserver<TranscribeRequest>
    private lateinit var interactor: AudioStreamInteractor
    private lateinit var audioService: AudioService

    var modelName = "wakeword-16kHz-open_sesame.ubm"
    var userID = "72f286b8-173f-436a-8869-6f7887789ee9"
    var enrollmentDescription = "My Enrollment"
    var isLivenessEnabled = false

    // boolean to control audio streaming
    var isRecording: AtomicBoolean = AtomicBoolean(false)

    var enrollmentID = "436ee716-346e-4066-8c28-7b5ef192831f"

    var aggregator = TranscriptAggregator()


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
        txtHello = findViewById(R.id.txt_hello)

        txtHello?.setOnClickListener {
           // onCompleteTransResolve()
        }
        val credentialStore = DefaultSecureCredentialStore(this, "default")
        val oAuthService = OAuthService(credentialStore)

        val fileStream: InputStream = this.javaClass.classLoader.getResourceAsStream("SensoryCloudConfig.ini")

        Log.e("xxx", "file stream ${fileStream.read()}")
        
        Initializer.initialize(
            oAuthService,
            null,  // JWT signer class, only used when enrollmentType is `jwt`
            fileStream,
            "21a060d711",  // Optional override for deviceID, useful when sharing config files across multiple devices
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

    private fun onCompleteTransResolve() {
        interactor.stopRecording()
        requestObserverTrans.onCompleted()
    }

    private fun createAudioService(oAuthService: OAuthService) {
        val tokenManager = TokenManager(oAuthService)
        val OAuthToken = tokenManager.accessToken
        Log.e("xxx", "auth token $OAuthToken")
        audioService = AudioService(tokenManager)

        if (ContextCompat.checkSelfPermission(
                this,
                RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO), 100)
            // request audio permissions
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        try {
            interactor = AudioStreamInteractor.newAudioStreamInteractor(this)
        } catch (e: Exception) {
            // Handle error (may be due to not having audio record permissions)
        }

        audioService.getModels(object : AudioService.GetModelsListener {
            override fun onSuccess(response: GetModelsResponse) {
                response.getModelsList()
                Log.e("xxx", "model list ${response.modelsList}")
                audioEnrollment()
            }
            override fun onFailure(t: Throwable) {
                Log.e("xxx", "on failure ${t.message}")
            }
        })
    }

    private fun audioEnrollment() {
       /* val requestObserver: StreamObserver<CreateEnrollmentRequest> =
            audioService.createEnrollment(
                modelName,
                userID,
                "",
                enrollmentDescription,
                isLivenessEnabled,
                0,
                0f,
                false,
                object : StreamObserver<CreateEnrollmentResponse> {
                    override fun onNext(value: CreateEnrollmentResponse) {
                        // The response contains information about the enrollment status.
                        // * audioEnergy
                        // * percentComplete
                        // For enrollments with liveness, there are two additional fields that are populated.
                        // * modelPrompt - indicates what the user should say in order to proceed with the enrollment.
                        // * sectionPercentComplete - indicates the percentage of the current ModelPrompt that has been spoken.
                        // EnrollmentId will be populated once the enrollment is complete
                        if (value.getEnrollmentId() !== "") {
                            // Enrollment is complete
                            isRecording.set(false)
                            Log.e("xxx", "enrolment ${value.enrollmentId}")
                        }
                    }

                    override fun onError(t: Throwable) {
                        Log.e("xxx", "error... ${t.message}")
                        // Handle Server error
                    }

                    override fun onCompleted() {
                        // Handle the grpc stream closing
                        isRecording.set(false)
                    }
                }
            )
*/
      /*  val mThread = Thread {
            interactor.startRecording()
            isRecording.set(true)
            while (isRecording.get()) {
                try {
                    val buffer: ByteArray = interactor.audioQueue.take()
                    val audio: ByteString = ByteString.copyFrom(buffer)
                    //Log.e("xxx", "audio $audio")
                    // (Make sure you use the proper type for the grpc stream you're using)
                    val request =
                        CreateEnrollmentRequest.newBuilder()
                            .setAudioContent(audio)
                            .build()
                    requestObserver.onNext(request)
                } catch (e: java.lang.Exception) {
                    Log.e("xxx", "exception ${e.message}")
                    // Handle errors (usually `InterruptedException` on the audioQueue.take call)
                }
            }
            interactor.stopRecording()
            // Close the grpc stream once you finish recording;
            requestObserver.onCompleted()
        }
        mThread.start()*/

        requestObserverTrans = audioService.transcribeAudio(
            "speech_recognition_en" ,
            userID,
            "",
            true,
            true,
            ThresholdSensitivity.HIGH,
            0f,
            object : StreamObserver<TranscribeResponse> {
                override fun onNext(value: TranscribeResponse) {
                    aggregator.processResponse(value.wordList)
                    val transcript = aggregator.transcript
                    Log.e("xxx", "value $transcript")
                    runOnUiThread {
                        txtHello?.text = transcript
                    }
                   // txtHello?.text = transcript
                }

                override fun onError(t: Throwable) {
                    Log.e("xxx", "error ${t.message}")
                    // Handle server error
                }

                override fun onCompleted() {
                    Log.e("xxx", "on complete")
                    // Handle grpc stream close
                }
            }
        )


        val mThread = Thread {
            interactor.startRecording()
            isRecording.set(true)
            while (isRecording.get()) {
                try {
                    val buffer: ByteArray = interactor.audioQueue.take()
                    val audio: ByteString = ByteString.copyFrom(buffer)
                    //Log.e("xxx", "audio $audio")
                    // (Make sure you use the proper type for the grpc stream you're using)
                    val request =
                        TranscribeRequest.newBuilder()
                            .setAudioContent(audio)
                            .build()
                    requestObserverTrans.onNext(request)
                } catch (e: java.lang.Exception) {
                    Log.e("xxx", "exception ${e.message}")
                    // Handle errors (usually `InterruptedException` on the audioQueue.take call)
                }
            }
            interactor.stopRecording()
            // Close the grpc stream once you finish recording;
            requestObserverTrans.onCompleted()
        }
        mThread.start()
        // requestObserverTrans.onCompleted()
    }

    private fun generateRandomToken(): String {
        return UUID.randomUUID().toString()
    }
}