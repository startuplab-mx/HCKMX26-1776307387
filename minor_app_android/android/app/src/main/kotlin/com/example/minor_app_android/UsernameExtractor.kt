package com.example.minor_app_android

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extrae el username o nombre de contacto de la persona con quien
 * interactúa el menor, usando el árbol de accesibilidad.
 *
 * Cada app expone el nombre en nodos diferentes:
 * - WhatsApp: título de la conversación en la toolbar
 * - Instagram/TikTok: @username en contentDescription
 * - Telegram: título del chat
 * - etc.
 */
object UsernameExtractor {

    private const val TAG = "MinorUsername"

    // Regex para @handles (Instagram, TikTok, Twitter)
    private val handleRegex = Regex("@[\\w._]{1,30}")

    // Regex para números de teléfono (WhatsApp)
    private val phoneRegex = Regex("\\+?\\d[\\d\\s\\-()]{7,}")

    /**
     * Intenta extraer el username/contacto del rootNode según el packageName.
     * Devuelve null si no se puede determinar.
     */
    fun extract(root: AccessibilityNodeInfo, packageName: String): String? {
        return try {
            val username = when (packageName) {
                "com.whatsapp",
                "com.whatsapp.w4b"          -> extractWhatsApp(root)

                "com.instagram.android"     -> extractInstagram(root)

                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill"  -> extractTikTok(root)

                "org.telegram.messenger",
                "org.telegram.messenger.web" -> extractTelegram(root)

                "com.facebook.katana"       -> extractFacebook(root)
                "com.facebook.orca"         -> extractMessenger(root)

                "com.snapchat.android"      -> extractSnapchat(root)
                "com.twitter.android"       -> extractTwitter(root)
                "com.discord"               -> extractDiscord(root)

                else -> extractGeneric(root)
            }

            if (username != null) {
                Log.d(TAG, "Extracted username from $packageName: $username")
            }

            username
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting username from $packageName: ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════
    // WHATSAPP
    // El nombre del contacto aparece en:
    // - viewIdResourceName que contiene "conversation_contact_name"
    // - O el primer texto significativo en la toolbar
    // - O un número de teléfono en el header
    // ══════════════════════════════════════════════════
    private fun extractWhatsApp(root: AccessibilityNodeInfo): String? {
        // Estrategia 1: Buscar por resource ID conocido
        val byId = findNodeByIdContaining(root, "conversation_contact_name")
        if (byId != null) {
            val name = byId.text?.toString()?.trim()
            byId.recycle()
            if (!name.isNullOrBlank()) return name
        }

        // Estrategia 2: Buscar en la toolbar / action_bar
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByClassName(root, "androidx.appcompat.widget.Toolbar")
            ?: findNodeByIdContaining(root, "action_bar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "whatsapp", "chats", "llamadas", "estados", "comunidades",
                "nueva", "grupo", "configuración", "buscar", "ajustes"
            ))
            toolbar.recycle()
            if (name != null) return name
        }

        // Estrategia 3: Buscar número de teléfono en los primeros nodos
        return extractFirstPhone(root)
    }

    // ══════════════════════════════════════════════════
    // INSTAGRAM
    // En DMs: nombre en el header del chat
    // En Feed/Reels: @username en contentDescription de posts
    // ══════════════════════════════════════════════════
    private fun extractInstagram(root: AccessibilityNodeInfo): String? {
        // Buscar @handles en contentDescription (posts, reels)
        val handle = findFirstHandle(root)
        if (handle != null) return handle

        // Buscar en toolbar para DMs
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "action_bar")
            ?: findNodeByIdContaining(root, "toolbar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "instagram", "explorar", "inicio", "reels", "tienda", "buscar"
            ))
            toolbar.recycle()
            if (name != null) return name
        }

        return null
    }

    // ══════════════════════════════════════════════════
    // TIKTOK
    // @username en el overlay del video o contentDescription
    // ══════════════════════════════════════════════════
    private fun extractTikTok(root: AccessibilityNodeInfo): String? {
        return findFirstHandle(root)
    }

    // ══════════════════════════════════════════════════
    // TELEGRAM
    // Nombre del chat en el header
    // ══════════════════════════════════════════════════
    private fun extractTelegram(root: AccessibilityNodeInfo): String? {
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "action_bar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "telegram", "contactos", "ajustes", "buscar", "chats"
            ))
            toolbar.recycle()
            if (name != null) return name
        }

        return null
    }

    // ══════════════════════════════════════════════════
    // FACEBOOK
    // Nombre del autor del post o contacto en chat
    // ══════════════════════════════════════════════════
    private fun extractFacebook(root: AccessibilityNodeInfo): String? {
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "title_bar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "facebook", "inicio", "marketplace", "notificaciones", "menú", "watch"
            ))
            toolbar.recycle()
            if (name != null) return name
        }
        return null
    }

    // ══════════════════════════════════════════════════
    // MESSENGER
    // Nombre del contacto en header de conversación
    // ══════════════════════════════════════════════════
    private fun extractMessenger(root: AccessibilityNodeInfo): String? {
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "thread_title")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "messenger", "chats", "personas", "historias"
            ))
            toolbar.recycle()
            if (name != null) return name
        }
        return null
    }

    // ══════════════════════════════════════════════════
    // SNAPCHAT
    // ══════════════════════════════════════════════════
    private fun extractSnapchat(root: AccessibilityNodeInfo): String? {
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "action_bar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "snapchat", "spotlight", "discover", "chat", "map", "stories"
            ))
            toolbar.recycle()
            if (name != null) return name
        }
        return findFirstHandle(root)
    }

    // ══════════════════════════════════════════════════
    // TWITTER / X
    // @handle del tweet o usuario en DM
    // ══════════════════════════════════════════════════
    private fun extractTwitter(root: AccessibilityNodeInfo): String? {
        return findFirstHandle(root)
    }

    // ══════════════════════════════════════════════════
    // DISCORD
    // Nombre del canal o DM
    // ══════════════════════════════════════════════════
    private fun extractDiscord(root: AccessibilityNodeInfo): String? {
        val toolbar = findNodeByClassName(root, "android.widget.Toolbar")
            ?: findNodeByIdContaining(root, "toolbar")

        if (toolbar != null) {
            val name = extractFirstMeaningfulText(toolbar, excludeKeywords = listOf(
                "discord", "servidores", "amigos", "mensajes"
            ))
            toolbar.recycle()
            if (name != null) return name
        }
        return null
    }

    // ══════════════════════════════════════════════════
    // GENÉRICO — fallback para apps no mapeadas
    // ══════════════════════════════════════════════════
    private fun extractGeneric(root: AccessibilityNodeInfo): String? {
        return findFirstHandle(root)
    }

    // ══════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════

    /**
     * Busca un nodo cuyo viewIdResourceName contenga el string dado.
     */
    private fun findNodeByIdContaining(root: AccessibilityNodeInfo, idPart: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val resId = node.viewIdResourceName
            if (resId != null && resId.contains(idPart, ignoreCase = true)) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Busca un nodo por su className.
     */
    private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString() == className) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Busca el primer @handle en el árbol de nodos.
     */
    private fun findFirstHandle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            // Buscar en text y contentDescription
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).forEach { raw ->
                val match = handleRegex.find(raw)
                if (match != null) return match.value
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Busca el primer número de teléfono en el árbol.
     */
    private fun extractFirstPhone(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).forEach { raw ->
                val match = phoneRegex.find(raw)
                if (match != null) {
                    val phone = match.value.trim()
                    if (phone.length >= 8) return phone
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * Extrae el primer texto significativo de un nodo (típicamente toolbar).
     * Ignora textos que sean keywords de la app (ej: "WhatsApp", "Chats").
     */
    private fun extractFirstMeaningfulText(
        node: AccessibilityNodeInfo,
        excludeKeywords: List<String>,
        maxDepth: Int = 5,
        currentDepth: Int = 0
    ): String? {
        if (currentDepth > maxDepth) return null

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length >= 2) {
            val isKeyword = excludeKeywords.any { text.equals(it, ignoreCase = true) }
            if (!isKeyword) return text
        }

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc.length >= 2) {
            val isKeyword = excludeKeywords.any { desc.equals(it, ignoreCase = true) }
            if (!isKeyword) return desc
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractFirstMeaningfulText(child, excludeKeywords, maxDepth, currentDepth + 1)
            child.recycle()
            if (result != null) return result
        }

        return null
    }
}
