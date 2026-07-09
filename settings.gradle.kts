import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

// Repos vía proxy JFrog (gluonlatam) porque CrowdStrike bloquea el acceso directo
// a los repos públicos. El token se lee del ~/.npmrc (misma config que mini-timer).
// El bloque pluginManagement se evalúa antes que el resto, por eso el token y los
// repos van inline en cada bloque.

pluginManagement {
    val token = java.io.File(System.getProperty("user.home"), ".npmrc")
        .takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.contains("_authToken=") }
        ?.substringAfter("_authToken=")?.trim() ?: ""
    repositories {
        listOf("scp-gradle-public").forEach { repo ->
            maven {
                url = uri("https://gluonlatam.jfrog.io/artifactory/$repo")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Bearer $token"
                }
                authentication { create<HttpHeaderAuthentication>("header") }
            }
        }
    }
}
dependencyResolutionManagement {
    val token = java.io.File(System.getProperty("user.home"), ".npmrc")
        .takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.contains("_authToken=") }
        ?.substringAfter("_authToken=")?.trim() ?: ""
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        listOf("scp-gradle-public").forEach { repo ->
            maven {
                url = uri("https://gluonlatam.jfrog.io/artifactory/$repo")
                credentials(HttpHeaderCredentials::class) {
                    name = "Authorization"
                    value = "Bearer $token"
                }
                authentication { create<HttpHeaderAuthentication>("header") }
            }
        }
    }
}

rootProject.name = "Athletic"
include(":app")
