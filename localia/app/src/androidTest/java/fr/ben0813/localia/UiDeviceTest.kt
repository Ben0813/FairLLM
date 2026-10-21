package fr.ben0813.localia

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingRejectsCleartextAndScreensRemainReachable() {
        rule.waitUntil(10000) { rule.onAllNodesWithText("Connecter mon PC").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Connecter mon PC").assertIsDisplayed()
        // Test screenshots contain no real account data. Screen protection is explicitly disabled for QA.
        rule.onNodeWithText("Connexion").performClick()
        rule.onNodeWithTag("screenProtection").performScrollTo().performClick()
        rule.waitUntil(10000) { (rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) == 0 }
        rule.waitForIdle()
        rule.onNodeWithText("Chat", useUnmergedTree = false).performClick()
        rule.waitForIdle()
        capture("localia-chat.png")
        rule.onNodeWithText("Connexion").performClick()
        rule.onNodeWithText("Adresse HTTPS du PC").performScrollTo().performTextInput("http://pc.example:1234")
        rule.onNodeWithText("Jeton API LM Studio").performScrollTo().performTextInput("invalid-test-token")
        rule.onNodeWithText("Vérifier et enregistrer").performScrollTo().performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasText("Une adresse HTTPS est obligatoire", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Une adresse HTTPS est obligatoire", substring = true)).assertIsDisplayed()
        rule.onNodeWithText("Compris").performClick()
        rule.onNodeWithText("Adresse HTTPS du PC").performScrollTo().performTextClearance()
        rule.onNodeWithText("Jeton API LM Studio").performTextClearance()
        rule.runOnUiThread {
            val keyboard = rule.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
        }
        rule.waitUntil(10000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != true
        }
        rule.waitForIdle()
        capture("localia-connexion.png")
        rule.onNodeWithText("Discussions").performClick()
        rule.onNodeWithText("Tout commence par une question.").assertIsDisplayed()
        rule.onNodeWithText("Connexion").performClick()
        rule.onNodeWithTag("screenProtection").performScrollTo().performClick()
        rule.waitUntil(10000) { (rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0 }
    }
    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val file = File(rule.activity.getExternalFilesDir(null), name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
