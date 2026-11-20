package com.punto.launcher.assistant

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import com.punto.launcher.data.AppEntry
import com.punto.launcher.data.Apps
import com.punto.launcher.data.CarState
import com.punto.launcher.data.MediaHub
import com.punto.launcher.data.Pkg
import com.punto.launcher.data.Prefs
import com.punto.launcher.data.WeatherApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Executa os comandos de voz. Hoje entende frases por regras (funciona
 * offline e sem custo). O próximo passo é trocar/combinar com um modelo de IA.
 */
class Assistant(
    private val ctx: Context,
    private val prefs: Prefs,
    private val media: MediaHub,
    private val car: CarState,
    private val voice: Voice,
    private val requestContactsPermission: () -> Unit,
    private val refreshWeather: () -> Unit,
) {
    private val ptBR = Locale.forLanguageTag("pt-BR")

    fun handle(text: String) {
        when (val cmd = CommandParser.parse(text)) {
            Command.Help -> say(
                "Eu posso tocar músicas, abrir o Waze com um endereço, falar o clima, a hora e a velocidade, " +
                    "abrir aplicativos, ligar e preparar mensagens no WhatsApp.",
            )
            Command.AirConditioning -> say(
                "Ainda não consigo controlar o ar-condicionado. Isso fica para quando a central estiver ligada ao carro.",
            )
            is Command.WhatsApp -> whatsapp(cmd.contact, cmd.message)
            is Command.Call -> call(cmd.contact)
            is Command.Navigate -> navigate(cmd.destination)
            Command.CarData -> say(
                "Ainda não estou conectado ao carro. Quando você instalar o leitor OBD2, " +
                    "vou te dizer a temperatura do motor, a rotação e a tensão da bateria.",
            )
            Command.Weather -> weather()
            Command.Time -> say("Agora são ${SimpleDateFormat("HH:mm", ptBR).format(Date())}.")
            Command.Speed -> speed()
            Command.Trip -> trip()
            Command.VolumeUp -> { media.volumeUp(); say("Aumentei o volume.") }
            Command.VolumeDown -> { media.volumeDown(); say("Abaixei o volume.") }
            Command.Pause -> { media.pause(); say("Música pausada.") }
            Command.Next -> { media.next(); say("Próxima música.") }
            Command.Previous -> { media.previous(); say("Música anterior.") }
            Command.Resume -> { media.play(); say("Tocando.") }
            is Command.PlayMusic -> playMusic(cmd.query, cmd.app)
            is Command.OpenApp -> openByName(cmd.name, notFoundMessage = true)
            is Command.Unknown -> {
                if (!openByName(cmd.text, notFoundMessage = false)) {
                    say(
                        "Não entendi \"${cmd.text}\". Tente: toca Legião Urbana, " +
                            "me leva para o shopping, ou como está o clima.",
                    )
                }
            }
        }
    }

    // ---------------- apps ----------------

    /** Abre um app pelo pacote, bloqueando vídeo com o carro andando. */
    fun openPackage(pkg: String, label: String? = null, announce: Boolean = false) {
        if (blockVideo(pkg)) return
        if (Apps.launch(ctx, pkg)) {
            if (announce) say("Abrindo ${label ?: "o app"}.")
        } else {
            say("${label ?: "Esse app"} não está instalado. Vou abrir a loja.")
            Apps.openStore(ctx, pkg)
        }
    }

    fun openEntry(entry: AppEntry) {
        if (blockVideo(entry.packageName)) return
        Apps.launch(ctx, entry)
    }

    private fun blockVideo(pkg: String): Boolean {
        if (pkg in Apps.VIDEO_APPS && car.speedKmh > 10f) {
            say("Por segurança, vídeo só com o carro parado.")
            return true
        }
        return false
    }

    private fun openByName(name: String, notFoundMessage: Boolean): Boolean {
        val target = norm(name).trim()
        if (target.isEmpty()) return false
        val apps = Apps.loadAll(ctx, withIcons = false)
        val best = apps.firstOrNull { norm(it.label) == target }
            ?: apps.firstOrNull { norm(it.label).startsWith(target) }
            ?: apps.firstOrNull { norm(it.label).contains(target) }
            ?: apps.firstOrNull { target.contains(norm(it.label)) && it.label.length >= 4 }
        if (best == null) {
            if (notFoundMessage) say("Não achei o aplicativo $name.")
            return false
        }
        if (blockVideo(best.packageName)) return true
        say("Abrindo ${best.label}.")
        Apps.launch(ctx, best)
        return true
    }

    // ---------------- música ----------------

    private fun playMusic(query: String, app: String?) {
        val preferred = when (app ?: prefs.musicApp) {
            Prefs.MUSIC_YTMUSIC -> Pkg.YTMUSIC
            else -> Pkg.SPOTIFY
        }
        val pkg = listOf(preferred, Pkg.SPOTIFY, Pkg.YTMUSIC).distinct()
            .firstOrNull { Apps.isInstalled(ctx, it) }
        if (pkg == null) {
            say("Instale o Spotify ou o YouTube Music para eu tocar músicas.")
            return
        }
        val appName = if (pkg == Pkg.SPOTIFY) "Spotify" else "YouTube Music"
        if (query.isBlank()) {
            Apps.launch(ctx, pkg)
            media.play()
            say("Abrindo o $appName.")
            return
        }
        say("Tocando $query no $appName.")
        // "Tocar a partir de busca": o mesmo intent que o Google Assistente usa.
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(pkg)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(intent) }.isFailure && pkg == Pkg.SPOTIFY) {
            val search = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(search) }
        }
    }

    // ---------------- navegação ----------------

    private val homeWords = setOf("casa", "minha casa", "a minha casa", "lar")
    private val workWords = setOf("trabalho", "meu trabalho", "o trabalho", "servico", "meu servico", "firma", "escritorio")

    private fun navigate(destination: String) {
        val d = norm(destination).trim()
        val target = when (d) {
            in homeWords -> prefs.homeAddress.ifBlank {
                say("Ainda não sei o endereço da sua casa. Cadastre na engrenagem, nas configurações.")
                return
            }
            in workWords -> prefs.workAddress.ifBlank {
                say("Ainda não sei o endereço do seu trabalho. Cadastre na engrenagem, nas configurações.")
                return
            }
            else -> destination
        }
        val useWaze = prefs.navApp != Prefs.NAV_MAPS && Apps.isInstalled(ctx, Pkg.WAZE)
        val intent = if (useWaze) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=${Uri.encode(target)}&navigate=yes"))
                .setPackage(Pkg.WAZE)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(target)}")).apply {
                if (Apps.isInstalled(ctx, Pkg.MAPS)) setPackage(Pkg.MAPS)
            }
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        say("Indo para $destination${if (useWaze) " pelo Waze" else ""}.")
        if (runCatching { ctx.startActivity(intent) }.isFailure) {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(geo) }.isFailure) {
                say("Não encontrei um app de mapas. Instale o Waze ou o Google Maps.")
            }
        }
    }

    // ---------------- informações ----------------

    private fun weather() {
        val w = car.weather
        if (w == null) {
            refreshWeather()
            say("Ainda estou buscando a previsão. Confira se a localização e a internet estão ligadas e pergunte de novo.")
            return
        }
        val place = car.place?.let { " em $it" } ?: ""
        val rain = if (w.rainChance >= 0) " Chance de chuva de ${w.rainChance} por cento." else ""
        say(
            "Agora$place faz ${w.temperature.roundToInt()} graus, ${WeatherApi.describe(w.code)}. " +
                "Hoje a máxima é ${w.max.roundToInt()} e a mínima ${w.min.roundToInt()}.$rain",
        )
        refreshWeather()
    }

    private fun speed() {
        if (!car.gpsFix) {
            say("Ainda estou sem sinal de GPS.")
        } else {
            say("Você está a ${car.speedKmh.roundToInt()} quilômetros por hora.")
        }
    }

    private fun trip() {
        val km = car.tripMeters / 1000.0
        say(
            String.format(ptBR, "Nesta viagem você andou %.1f quilômetros. ", km) +
                "A velocidade máxima foi ${car.maxSpeedKmh.roundToInt()} quilômetros por hora.",
        )
    }

    // ---------------- contatos: WhatsApp e ligação ----------------

    private data class Contact(val name: String, val number: String)

    private fun hasContactsPermission() =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun findContact(rawName: String): Contact? {
        val target = norm(rawName).replace(Regex("^(?:minha|meu|o|a)\\s+"), "").trim()
        if (target.isEmpty()) return null
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        ) ?: return null
        var best: Contact? = null
        var bestScore = 0
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val number = it.getString(1) ?: continue
                val n = norm(name)
                val score = when {
                    n == target -> 4
                    n.startsWith("$target ") -> 3
                    n.split(" ").contains(target) -> 2
                    n.contains(target) -> 1
                    else -> 0
                }
                if (score > bestScore) {
                    best = Contact(name, number)
                    bestScore = score
                }
            }
        }
        return best
    }

    /** Converte número da agenda para o formato do WhatsApp (55 + DDD + número). */
    private fun whatsappNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return digits
        val d = digits.trimStart('0')
        return when {
            d.startsWith("55") && d.length in 12..13 -> d
            d.length in 10..11 -> "55$d"
            d.length > 11 -> "55" + d.takeLast(11)
            else -> d
        }
    }

    private fun whatsapp(contact: String, message: String) {
        val pkg = listOf(Pkg.WHATSAPP, Pkg.WHATSAPP_BUSINESS).firstOrNull { Apps.isInstalled(ctx, it) }
        if (pkg == null) {
            say("O WhatsApp não está instalado.")
            return
        }
        if (!hasContactsPermission()) {
            say("Preciso de permissão para ver seus contatos. Depois é só repetir o pedido.")
            requestContactsPermission()
            return
        }
        val c = findContact(contact)
        if (c == null) {
            say("Não encontrei $contact nos contatos. Escolha a conversa no WhatsApp.")
            val share = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, message)
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(share) }
            return
        }
        var url = "https://api.whatsapp.com/send?phone=${whatsappNumber(c.number)}"
        if (message.isNotBlank()) url += "&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        say(
            if (message.isBlank()) "Abrindo a conversa com ${c.name}."
            else "Mensagem pronta para ${c.name}. É só tocar em enviar.",
        )
        runCatching { ctx.startActivity(intent) }
    }

    private fun call(contact: String) {
        val digits = contact.filter { it.isDigit() }
        val number = if (digits.length >= 8) {
            digits
        } else {
            if (!hasContactsPermission()) {
                say("Preciso de permissão para ver seus contatos. Depois é só repetir o pedido.")
                requestContactsPermission()
                return
            }
            val c = findContact(contact)
            if (c == null) {
                say("Não encontrei $contact nos contatos.")
                return
            }
            say("Ligando para ${c.name}. Toque no botão verde para confirmar.")
            c.number
        }
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(dial) }
    }

    private fun say(text: String) = voice.say(text)
}
