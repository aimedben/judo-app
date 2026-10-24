package com.example.util.face

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde vie privee, verifiee a chaque build :
 *  - aucune empreinte faciale dans les DTO / les payloads de synchronisation ;
 *  - aucun log d'embedding, de vecteur ou de score ;
 *  - aucune ecriture d'image (frame ou visage) sur disque dans le module biometrie.
 */
class FacePrivacyGuardTest {

    private val mainSources: File = File(System.getProperty("user.dir"), "src/main/java")

    private fun sources(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun read(file: File): String = file.readText()

    @Test
    fun `les empreintes ne figurent jamais dans un DTO ou un payload de sync`() {
        val serializedKey = Regex("\"(embedding|face_template|faceTemplates|faceEmbedding)\"")
        val offenders = sources()
            .filter { file ->
                val path = file.path.replace('\\', '/')
                val syncOrDto = path.contains("/data/sync/") || path.contains("/data/remote/") ||
                    path.contains("Supabase") || path.contains("Sync") ||
                    file.name.contains("Dto") || file.name.contains("DTO")
                syncOrDto && (serializedKey.containsMatchIn(read(file)) || read(file).contains("FaceTemplateEntity"))
            }
            .map { it.relativeTo(mainSources).path }

        assertTrue(
            "Empreintes faciales presentes dans du code de synchronisation/DTO : $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `aucun log ne contient d embedding ni de score facial`() {
        val logPattern = Regex("""Log\.[divew]\s*\([^)]*(embedding|Embedding|vecteur|vector|score|FaceTemplate)""")
        val offenders = sources()
            .filter { logPattern.containsMatchIn(read(it)) }
            .map { it.relativeTo(mainSources).path }

        assertTrue("Logs biométriques interdits : $offenders", offenders.isEmpty())
    }

    @Test
    fun `le module biometrie n ecrit aucune image sur disque`() {
        val forbidden = Regex("""compress\s*\(|FileOutputStream|openFileOutput|MediaStore|getExternalStorage""")
        val facePackages = sources().filter {
            val p = it.path.replace('\\', '/')
            (p.contains("/util/face/") || p.contains("/data/local/face/")) &&
                it.name != "FaceDatabase.kt"
        }
        val offenders = facePackages
            .filter { forbidden.containsMatchIn(read(it)) }
            .map { it.relativeTo(mainSources).path }

        assertTrue("Écriture d'image détectée dans le module biométrie : $offenders", offenders.isEmpty())
    }
}
