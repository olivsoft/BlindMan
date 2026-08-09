package ch.olivsoft.android.blindman

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BlindManViewModel : ViewModel() {

    companion object {
        // Defined value lists
        val ALLOWED_LIVES: List<Int> = listOf(1, 2, 3, 4, 6, 9, 12, 0)
        val BACKGROUND_ALPHA = intArrayOf(0, 0x40, 0x80)
        val OBSTACLE_ROWS = intArrayOf(8, 11, 15)

        // Game state
        enum class GameState {
            IDLE, SHOW, PLAY, HINT
        }
    }

    // Live Data
    val sizeData = MutableLiveData(1)
    var size: Int = sizeData.value!!
        set(value) {
            field = value
            sizeData.value = value
        }
        get() {
            return sizeData.value!!
        }

    val levelData = MutableLiveData(1)
    var level: Int = levelData.value!!
        set(value) {
            field = value
            levelData.value = value
        }
        get() {
            return levelData.value!!
        }

    val backgroundData = MutableLiveData(1)
    var background: Int = backgroundData.value!!
        set(value) {
            field = value
            backgroundData.value = value
        }
        get() {
            return backgroundData.value!!
        }

    val livesData = MutableLiveData(3)
    var lives: Int = livesData.value!!
        set(value) {
            // Reset in case something went completely wrong
            field = if (ALLOWED_LIVES.contains(value)) value
            else ALLOWED_LIVES[2]
            livesData.value = field
        }
        get() {
            return livesData.value!!
        }

    val invalidateCounterData = MutableLiveData(0)
    var invalidateCounter: Int = invalidateCounterData.value!!
        set(value) {
            field = value
            invalidateCounterData.value = value
        }

    val isHapticFeedbackEnabledData = MutableLiveData(true)
    var isHapticFeedbackEnabled: Boolean = isHapticFeedbackEnabledData.value!!
        set(value) {
            field = value
            isHapticFeedbackEnabledData.value = value
        }
        get() {
            return isHapticFeedbackEnabledData.value!!
        }

    val isSoundEffectsEnabledData = MutableLiveData(true)
    var isSoundEffectsEnabled: Boolean = isSoundEffectsEnabledData.value!!
        set(value) {
            field = value
            isSoundEffectsEnabledData.value = value
        }
        get() {
            return isSoundEffectsEnabledData.value!!
        }

    val isMusicEnabledData = MutableLiveData(false)
    var isMusicEnabled: Boolean = isMusicEnabledData.value!!
        set(value) {
            field = value
            isMusicEnabledData.value = value
        }
        get() {
            return isMusicEnabledData.value!!
        }

    val messageTextData = MutableLiveData("")
    var messageText: String = messageTextData.value!!
        set(value) {
            field = value
            messageTextData.value = value
        }
        get() {
            return messageTextData.value!!
        }
}
