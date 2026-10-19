package me.rerere.rikkahub.data.ai

import org.junit.Assert.*
import org.junit.Test

class VertexServiceAccountCredentialsTest {
    private val credentials = """{"type":"service_account","project_id":"test-project","client_email":"test@test-project.iam.gserviceaccount.com","private_key":"-----BEGIN PRIVATE KEY-----\ntest\n-----END PRIVATE KEY-----\n"}"""

    @Test
    fun `imports complete service account JSON and preserves PEM line breaks`() {
        val result = VertexServiceAccountCredentials.fromJson(credentials)
        assertEquals("test-project", result.projectId)
        assertEquals("test@test-project.iam.gserviceaccount.com", result.email)
        assertTrue(result.privateKey.contains("\ntest\n"))
    }

    @Test
    fun `rejects OAuth client JSON rather than mixing it with previous credentials`() {
        assertThrows(IllegalArgumentException::class.java) {
            VertexServiceAccountCredentials.fromJson(credentials.replace("service_account", "authorized_user"))
        }
        assertThrows(IllegalStateException::class.java) {
            VertexServiceAccountCredentials.fromJson("""{"installed":{"client_id":"test-client"}}""")
        }
    }

    @Test
    fun `rejects incomplete service account and invalid private key`() {
        assertThrows(IllegalStateException::class.java) {
            VertexServiceAccountCredentials.fromJson(credentials.replace("\"project_id\":\"test-project\",", ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VertexServiceAccountCredentials.fromJson(credentials.replace("BEGIN PRIVATE KEY", "BEGIN PUBLIC KEY"))
        }
    }
}
