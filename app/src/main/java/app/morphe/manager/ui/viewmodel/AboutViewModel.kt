package app.morphe.manager.ui.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.lifecycle.ViewModel
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Github

data class SocialLink(
    val name: String,
    val url: String,
    val preferred: Boolean = false,
)

class AboutViewModel : ViewModel() {
    companion object {
        val socials: List<SocialLink> = listOf(
            SocialLink(
                name = "Manager source",
                url = "https://github.com/SysAdminDoc/patchdock-manager",
                preferred = true
            ),
            SocialLink(
                name = "Patch source",
                url = "https://github.com/icysymmetra/tiktok-patches-for-morphe"
            ),
            SocialLink(
                name = "Upstream",
                url = "https://github.com/MorpheApp/morphe-manager"
            )
        )

        private val socialIcons = mapOf(
            "Manager source" to FontAwesomeIcons.Brands.Github,
            "Patch source" to Icons.AutoMirrored.Outlined.Article,
            "Upstream" to Icons.Outlined.Public,
        )

        fun getSocialIcon(name: String) = socialIcons[name] ?: Icons.Outlined.Language
    }
}
