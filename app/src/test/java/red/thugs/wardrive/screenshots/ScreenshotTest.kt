package red.thugs.wardrive.screenshots

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import red.thugs.wardrive.net.LiveState
import red.thugs.wardrive.net.LiveStatus
import red.thugs.wardrive.ui.AboutScreen
import red.thugs.wardrive.ui.OnboardingScreen
import red.thugs.wardrive.ui.Screen
import red.thugs.wardrive.ui.WardriveActions
import red.thugs.wardrive.ui.WardriveScaffold
import red.thugs.wardrive.ui.WardriveUiState
import red.thugs.wardrive.ui.theme.WardriveTheme

/**
 * Renders the real Compose UI to PNGs on the JVM via Robolectric — no emulator.
 * Fed with [Fakes] so the list, map and footer look like a live drive.
 *
 *   ./gradlew :app:recordRoborazziDebug     # (re)generate  ->  docs/screenshots/
 *   ./gradlew :app:verifyRoborazziDebug     # fail if the UI changed
 *
 * A plain Application is used so WardriveApp.onCreate() (EncryptedSharedPreferences,
 * services) is not run — the screens under test are stateless.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class ScreenshotTest {

    private val obs = Fakes.observations()

    @Test
    fun list() {
        captureRoboImage("../docs/screenshots/list.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(
                        screen = Screen.LIST,
                        observations = obs,
                        counts = Fakes.counts(obs),
                        scanning = true,
                        live = LiveStatus(LiveState.LIVE, sentOk = 214, pending = 6, detail = "Accepted 40"),
                    ),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    fun listEmpty() {
        captureRoboImage("../docs/screenshots/list_empty.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(screen = Screen.LIST, scanning = true),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    fun map() {
        captureRoboImage("../docs/screenshots/map.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(
                        screen = Screen.MAP,
                        observations = obs,
                        counts = Fakes.counts(obs),
                        scanning = true,
                        powerSaving = true,
                        track = Fakes.track(),
                        current = 55.680 to 12.560,
                    ),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-420dpi")
    fun stats() {
        captureRoboImage("../docs/screenshots/stats.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(
                        screen = Screen.STATS,
                        observations = obs,
                        counts = Fakes.counts(obs),
                        scanning = true,
                        growth = Fakes.growth(),
                    ),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1200dp-420dpi")
    fun onboarding() {
        captureRoboImage("../docs/screenshots/onboarding.png") {
            WardriveTheme(darkTheme = true) { OnboardingScreen(onDone = {}) }
        }
    }

    @Test
    fun scope() {
        captureRoboImage("../docs/screenshots/scope.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(
                        screen = Screen.SCOPE,
                        observations = obs,
                        counts = Fakes.counts(obs),
                        scanning = true,
                        congestion = Fakes.congestion(),
                    ),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h1400dp-420dpi")
    fun spy() {
        captureRoboImage("../docs/screenshots/spy.png") {
            WardriveTheme(darkTheme = true) {
                WardriveScaffold(
                    state = WardriveUiState(
                        screen = Screen.SPY,
                        observations = obs,
                        counts = Fakes.counts(obs),
                        scanning = true,
                        spyMode = true,
                        followers = Fakes.followers(),
                        trackedDevices = 128,
                    ),
                    actions = WardriveActions(),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h2600dp-420dpi")
    fun about() {
        captureRoboImage("../docs/screenshots/about.png") {
            WardriveTheme(darkTheme = true) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AboutScreen()
                }
            }
        }
    }
}
