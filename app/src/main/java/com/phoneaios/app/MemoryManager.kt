package com.phoneaios.app

import android.content.Context
import kotlinx.coroutines.flow.flow

class MemoryManager(context: Context) {
    private val db = MemoryGraphDatabase.getDatabase(context)
    private val dao = db.tripleDao()

    suspend fun remember(subject: String, predicate: String, objectValue: String) {
        dao.insert(MemoryTriple(subject = subject.lowercase(), predicate = predicate.lowercase(), objectValue = objectValue.lowercase()))
    }

    suspend fun recall(subject: String, predicate: String): String? {
        return dao.findObject(subject.lowercase(), predicate.lowercase())?.objectValue
    }

    /**
     * Multi-hop traversal: Finds a deep relationship.
     * Example: User -> likes -> ? (e.g., Pop Music)
     *          Pop Music -> played_by -> ? (e.g., Spotify)
     */
    suspend fun traverse(startSubject: String, depth: Int = 2): Map<String, String> {
        val results = mutableMapOf<String, String>()
        var currentSubject = startSubject.lowercase()
        
        // Simple BFS-like traversal for the requested depth
        for (i in 0 until depth) {
            val triples = dao.getTriplesBySubject(currentSubject)
            if (triples.isEmpty()) break
            
            for (triple in triples) {
                results[triple.predicate] = triple.objectValue
                // For the next hop, we'll pick the first object we found
                // Note: Real graph traversal would follow multiple paths. This is a minimalist agentic implementation.
                if (i < depth - 1) {
                    currentSubject = triple.objectValue
                }
            }
        }
        return results
    }

    suspend fun getAllKnowledgeSummary(): String {
        val all = dao.getAllTriples()
        if (all.isEmpty()) return "No specific memories found."
        
        return all.joinToString("\n") { "{${it.subject}, ${it.predicate}, ${it.objectValue}}" }
    }
}
