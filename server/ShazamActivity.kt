import com.shazam.shazamkit.AudioSampleRateInHz
import com.shazam.shazamkit.MatchResult
import com.shazam.shazamkit.ShazamKit
import com.shazam.shazamkit.ShazamKitResult


class ShazamActivity {

    val catalog = (ShazamKit.createShazamCatalog(developerTokenProvider) as ShazamKitResult.Success).data

    val currentSession = (ShazamKit.createStreamingSession(
        catalog,
        AudioSampleRateInHz.SAMPLE_RATE_48000,
        2
    ) as ShazamKitResult.Success).data

    coroutineScope.launch {
        // record audio and flow it to the StreamingSession
        recordingFlow().collect { audioChunk ->
            currentSession?.matchStream(
                audioChunk.buffer,
                audioChunk.meaningfulLengthInBytes,
                audioChunk.timestamp
            )
        }
    }

    coroutineScope.launch {
        currentSession?.recognitionResults().collect { matchResult ->
            println("Received MatchResult: $matchResult")
        }
    }
}