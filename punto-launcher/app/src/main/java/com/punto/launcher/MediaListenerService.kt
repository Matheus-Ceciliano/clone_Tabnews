package com.punto.launcher

import android.service.notification.NotificationListenerService

/**
 * Serviço vazio: existe só para o Android liberar ao launcher a leitura
 * da música que está tocando (MediaSessionManager.getActiveSessions).
 */
class MediaListenerService : NotificationListenerService()
